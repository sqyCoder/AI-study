package org.example.gobang.net;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * P2P TCP 连接封装（spec3 §2.1）。
 *
 * <p>职责边界：HELLO 握手、PING/PONG 心跳、BYE 断开、超长/非法消息防护全部内部处理；
 * {@link Listener} 只收到游戏级消息（GUESS 系 / MOVE / UNDO 系 / REMATCH 系）。</p>
 *
 * <p>线程约定（保持本类零 JavaFX 依赖，可无头单测，spec3 §1 分层原则）：
 * 回调在 IO/调度线程触发，UI 层自行 Platform.runLater 切换。</p>
 *
 * <p>断开语义：
 * <ul>
 *   <li>{@link #close(String)}——本地主动离开：best-effort 发 BYE，<b>不</b>触发本地回调；</li>
 *   <li>内部 drop——对方断开/心跳超时/协议违规/BYE：清理后回调一次（CAS 幂等，与 close 互斥先到先得）。</li>
 * </ul></p>
 */
public final class NetLink implements Link {

    public interface Listener {
        /** 游戏级消息行（已拆行、已过滤 HELLO/PING/PONG/BYE）。IO 读线程回调。 */
        void onMessage(String line);

        /** 非本地主动关闭导致的断开。任意线程回调，幂等仅一次。 */
        void onDisconnected(String reason);

        /**
         * 对方 HELLO 就绪（spec4 §2.3）。仅 SupaLink 触发（P2P 握手在 start 内
         * 同步完成，进入对局前对方必然在线）。任意线程回调，仅一次。
         */
        default void onPeerReady() {
        }

        /**
         * 与服务器的链路已确认可用（spec4 v4.2：首个请求成功即触发）。
         * 用于大厅页给出「房间已创建」的明确反馈。任意线程回调，仅一次。
         */
        default void onLinkReady() {
        }
    }

    /** 房主监听凭据：cancel() 关闭端口并结束接受循环。 */
    public static final class HostTicket {
        private final ServerSocket serverSocket;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        HostTicket(ServerSocket ss) {
            this.serverSocket = ss;
        }

        public int port() {
            return serverSocket.getLocalPort();
        }

        public void cancel() {
            cancelled.set(true);
            closeQuietly(serverSocket);
        }
    }

    // 心跳参数集中定义可调（spec3 §14 弱网回退）；包内可写以便测试注入缩短
    static volatile long pingIntervalMs = 5000;
    static volatile long deadAfterMs = 15000;
    static volatile long checkIntervalMs = 3000;
    private static final int CONNECT_TIMEOUT_MS = 8000;

    private static final ScheduledExecutorService SCHED =
            Executors.newScheduledThreadPool(1, r -> {
                Thread t = new Thread(r, "net-heartbeat");
                t.setDaemon(true);
                return t;
            });

    private final Socket socket;
    private final BufferedWriter writer;
    private final AtomicBoolean finished = new AtomicBoolean(false);
    private final boolean hostSide;

    private volatile boolean active;
    private volatile long lastRecv;
    private volatile Listener listener;
    private volatile String peerName = "";
    private ScheduledFuture<?> pingTask;
    private ScheduledFuture<?> checkTask;

    private NetLink(Socket socket, boolean hostSide) throws IOException {
        this.socket = socket;
        this.hostSide = hostSide;
        socket.setTcpNoDelay(true);
        this.writer = new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    // ---------- 建连 ----------

    /**
     * 开房监听。立即返回凭据；accept 循环在守护线程运行。
     * 绑定失败返回 null 并回调 onError。首个接入者经 onReady 交付；
     * 之后直到 cancel() 前的后来者一律回「房间已满」并关闭。
     */
    public static HostTicket host(int port, Consumer<NetLink> onReady, Consumer<String> onError) {
        HostTicket ticket;
        try {
            ticket = new HostTicket(new ServerSocket(port));
        } catch (IOException e) {
            onError.accept("端口被占用或被防火墙拦截，请更换端口或放行 Java");
            return null;
        }
        Thread t = new Thread(() -> acceptLoop(ticket, onReady, onError), "net-host");
        t.setDaemon(true);
        t.start();
        return ticket;
    }

    private static void acceptLoop(HostTicket ticket, Consumer<NetLink> onReady, Consumer<String> onError) {
        boolean firstTaken = false;
        while (!ticket.cancelled.get()) {
            Socket s;
            try {
                s = ticket.serverSocket.accept();
            } catch (IOException e) {
                if (!ticket.cancelled.get()) {
                    onError.accept("监听中断：" + e.getMessage());
                }
                return;
            }
            if (firstTaken) {
                rejectFull(s);
                continue;
            }
            firstTaken = true;
            try {
                onReady.accept(new NetLink(s, true));
            } catch (IOException e) {
                onError.accept("连接建立失败：" + e.getMessage());
                return;
            }
        }
    }

    private static void rejectFull(Socket s) {
        try {
            BufferedWriter w = new BufferedWriter(
                    new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
            w.write(Protocol.bye("房间已满"));
            w.write('\n');
            w.flush();
        } catch (IOException ignored) {
            // 拒绝失败无需处理
        } finally {
            closeQuietly(s);
        }
    }

    /** 主动连接（8 秒超时）。结果经回调交付，回调在后台线程触发。 */
    public static void join(String ip, int port, Consumer<NetLink> onReady, Consumer<String> onError) {
        Thread t = new Thread(() -> {
            try {
                Socket s = new Socket();
                s.connect(new InetSocketAddress(ip, port), CONNECT_TIMEOUT_MS);
                onReady.accept(new NetLink(s, false));
            } catch (SocketTimeoutException e) {
                onError.accept("连接超时，请确认对方 IP 与端口");
            } catch (IOException e) {
                onError.accept("无法连接 " + ip + ":" + port);
            }
        }, "net-join");
        t.setDaemon(true);
        t.start();
    }

    // ---------- 会话 ----------

    /**
     * 启动会话：发送我方 HELLO → 启动读循环与心跳。
     * 读循环首条消息必须是合法 HELLO 且版本一致，否则按协议违规断开。
     */
    public void start(String myName, Listener l) {
        this.listener = Objects.requireNonNull(l);
        active = true;
        lastRecv = System.currentTimeMillis();
        sendRaw(Protocol.hello(myName));
        Thread reader = new Thread(this::readLoop, "net-reader");
        reader.setDaemon(true);
        reader.start();
        pingTask = SCHED.scheduleWithFixedDelay(this::pingTick,
                pingIntervalMs, pingIntervalMs, TimeUnit.MILLISECONDS);
        checkTask = SCHED.scheduleWithFixedDelay(this::checkTick,
                checkIntervalMs, checkIntervalMs, TimeUnit.MILLISECONDS);
    }

    private void readLoop() {
        boolean handshaken = false;
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                lastRecv = System.currentTimeMillis();
                if (line.length() > Protocol.MAX_LINE) {
                    violate("消息超长");
                    return;
                }
                if (!handshaken) {
                    if (!handshake(line)) {
                        return;
                    }
                    handshaken = true;
                    continue;
                }
                if (!dispatch(line)) {
                    return;
                }
            }
            drop("对方已断开连接");
        } catch (IOException e) {
            if (active) {
                drop("连接异常中断");
            }
        }
    }

    /** 握手期：首条必须是合法 HELLO 且版本一致。返回 false 表示已断开。 */
    private boolean handshake(String line) {
        Protocol.Message m;
        try {
            m = Protocol.decode(line);
        } catch (Protocol.ProtocolException e) {
            violate(e.getMessage());
            return false;
        }
        if (m.type() != Protocol.Type.HELLO) {
            violate("首条消息非 HELLO");
            return false;
        }
        peerName = m.get("name");
        return true;
    }

    /** 游戏期分发。返回 false 表示已断开。 */
    private boolean dispatch(String line) {
        Protocol.Message m;
        try {
            m = Protocol.decode(line);
        } catch (Protocol.ProtocolException e) {
            violate("非法消息");
            return false;
        }
        switch (m.type()) {
            case PING -> sendRaw(Protocol.pong());
            case PONG -> {
                // 仅刷新 lastRecv（已在循环头部完成）
            }
            case BYE -> {
                drop(m.get("reason"));
                return false;
            }
            default -> {
                Listener ls = listener;
                if (ls != null) {
                    ls.onMessage(line);
                }
            }
        }
        return true;
    }

    // ---------- 收发 ----------

    /** 发送一行（调用方传 Protocol 编码产物）。失败触发断开流程并返回 false。 */
    public boolean send(String line) {
        if (!active) {
            return false;
        }
        return sendRaw(line);
    }

    /**
     * 实际写盘。必须 synchronized：读线程（PONG 应答）、心跳线程（PING）、
     * 业务回调可能并发到达，BufferedWriter 非线程安全，交错写会产生损坏行。
     */
    private synchronized boolean sendRaw(String line) {
        try {
            writer.write(line);
            writer.write('\n');
            writer.flush();
            return true;
        } catch (IOException e) {
            if (active) {
                drop("发送失败");
            }
            return false;
        }
    }

    // ---------- 心跳 ----------

    private void pingTick() {
        if (active) {
            sendRaw(Protocol.ping());
        }
    }

    private void checkTick() {
        if (active && System.currentTimeMillis() - lastRecv > deadAfterMs) {
            drop("心跳超时");
        }
    }

    // ---------- 断开 ----------

    /** 本地主动离开：best-effort 发 BYE 后静默清理，不触发本地回调。幂等。 */
    public void close(String reason) {
        if (!finished.compareAndSet(false, true)) {
            return;
        }
        active = false;
        cancelHeartbeat();
        synchronized (this) {
            try {
                writer.write(Protocol.bye(reason));
                writer.write('\n');
                writer.flush();
            } catch (IOException ignored) {
                // 对端可能已不可达，尽力而为
            }
        }
        closeQuietly(socket);
    }

    /** 远端死亡/违规：清理并回调一次。 */
    private void drop(String reason) {
        if (!finished.compareAndSet(false, true)) {
            return;
        }
        active = false;
        cancelHeartbeat();
        closeQuietly(socket);
        Listener ls = listener;
        if (ls != null) {
            ls.onDisconnected(reason == null ? "未知原因" : reason);
        }
    }

    /** 协议违规：告知对方后断开。 */
    private void violate(String detail) {
        sendRaw(Protocol.bye("协议违规"));
        drop("协议违规：" + detail);
    }

    private void cancelHeartbeat() {
        if (pingTask != null) {
            pingTask.cancel(false);
        }
        if (checkTask != null) {
            checkTask.cancel(false);
        }
    }

    // ---------- 状态 ----------

    public boolean isActive() {
        return active;
    }

    /** 本端是否为房主（决定猜先角色：房主=持子方）。 */
    public boolean isHost() {
        return hostSide;
    }

    /** 握手完成后可得对方昵称。 */
    public String peerName() {
        return peerName;
    }

    // ---------- 工具 ----------

    private static void closeQuietly(Socket s) {
        try {
            s.close();
        } catch (IOException ignored) {
        }
    }

    private static void closeQuietly(ServerSocket ss) {
        try {
            ss.close();
        } catch (IOException ignored) {
        }
    }
}
