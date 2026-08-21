package org.example.gobang.net.supa;

import org.example.gobang.net.Link;
import org.example.gobang.net.NetLink;
import org.example.gobang.net.Protocol;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Supabase PostgREST 表轮询传输（spec4 v4.1 Plan B）。
 *
 * <p>背景：该项目 Realtime WS 后端对帧完全静默（实测 2026-08，Node/Java 双端验证），
 * 而 REST 通道稳定可用，故改用 gobang_msg 表 + 短轮询。语义与 NetLink 完全对齐：
 * HELLO 握手、BYE 断线、PING/PONG 保活；额外支持 start() 监听器移交（大厅→对局）。</p>
 *
 * <p>线程约定：回调在调度线程触发，UI 层自行 Platform.runLater 切换。</p>
 */
public final class SupaRestLink implements Link {

    static volatile long pollIntervalMs = 350;
    static volatile long pingMs = 5000;
    static volatile long deadAfterMs = 15000;
    static volatile long checkIntervalMs = 3000;
    static volatile long guestWaitMs = 15000;
    private static final int MAX_POLL_ERRORS = 10;

    private static final ScheduledExecutorService SCHED =
            Executors.newScheduledThreadPool(4, r -> {
                Thread t = new Thread(r, "supa-rest");
                t.setDaemon(true);
                return t;
            });

    private final SupaConfig cfg;
    private final String roomCode;
    private final boolean hostSide;
    private final String cid;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();
    private final AtomicBoolean finished = new AtomicBoolean(false);
    private final AtomicLong lastId = new AtomicLong(0);
    private final AtomicLong lastRecv = new AtomicLong(0);

    private volatile boolean started;
    private volatile boolean handshaken;
    private volatile NetLink.Listener listener;
    private volatile String myName = "";
    private volatile String peerName = "";
    private volatile int pollErrors;
    private final java.util.concurrent.atomic.AtomicInteger postErrors =
            new java.util.concurrent.atomic.AtomicInteger();
    private final AtomicBoolean linkReadyFired = new AtomicBoolean(false);
    private ScheduledFuture<?> pollTask;
    private ScheduledFuture<?> pingTask;
    private ScheduledFuture<?> checkTask;
    private ScheduledFuture<?> guestTimer;

    /** @param hostSide 房主=true（决定猜先角色与房间清理职责）。 */
    public SupaRestLink(SupaConfig cfg, String roomCode, boolean hostSide) {
        this.cfg = Objects.requireNonNull(cfg);
        this.roomCode = roomCode;
        this.hostSide = hostSide;
        byte[] b = new byte[8];
        new SecureRandom().nextBytes(b);
        StringBuilder sb = new StringBuilder();
        for (byte x : b) {
            sb.append(String.format("%02x", x));
        }
        this.cid = sb.toString();
    }

    // ---------- Link 接口 ----------

    @Override
    public void start(String myName, NetLink.Listener l) {
        this.myName = Objects.requireNonNull(myName);
        this.listener = Objects.requireNonNull(l);
        if (started) {
            return; // 监听器移交（大厅→对局），轮询保持
        }
        started = true;
        lastRecv.set(System.currentTimeMillis());
        log("START room=" + roomCode + " host=" + hostSide + " name=" + myName);
        if (hostSide) {
            // 先清残留再宣告房间（两请求必须串行，否则 DELETE 可能删掉刚插入的 HELLO）
            deleteRoomRows().whenComplete((r, ex) -> {
                if (!finished.get()) {
                    beginSession();
                }
            });
        } else {
            beginSession();
        }
    }

    /** 开启轮询与保活，并广播我方 HELLO。 */
    private void beginSession() {
        postLine(Protocol.hello(myName));
        pollTask = SCHED.scheduleWithFixedDelay(this::pollOnce,
                pollIntervalMs, pollIntervalMs, TimeUnit.MILLISECONDS);
        pingTask = SCHED.scheduleWithFixedDelay(() -> {
            if (handshaken && !finished.get()) {
                postLine(Protocol.ping());
            }
        }, pingMs, pingMs, TimeUnit.MILLISECONDS);
        checkTask = SCHED.scheduleWithFixedDelay(() -> {
            if (handshaken && !finished.get()
                    && System.currentTimeMillis() - lastRecv.get() > deadAfterMs) {
                drop("心跳超时");
            }
        }, checkIntervalMs, checkIntervalMs, TimeUnit.MILLISECONDS);
        if (!hostSide) {
            guestTimer = SCHED.schedule(() -> {
                if (!handshaken && !finished.get()) {
                    drop("房间不存在或房主未就绪");
                }
            }, guestWaitMs, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public boolean send(String line) {
        if (!started || finished.get()) {
            return false;
        }
        postLine(line);
        return true;
    }

    @Override
    public void close(String reason) {
        if (!finished.compareAndSet(false, true)) {
            return;
        }
        cancelTimers();
        if (started) {
            postLine(Protocol.bye(reason)); // best-effort 告别
        }
    }

    @Override
    public boolean isActive() {
        return started && !finished.get();
    }

    @Override
    public boolean isHost() {
        return hostSide;
    }

    @Override
    public String peerName() {
        return peerName;
    }

    // ---------- 轮询 ----------

    private void pollOnce() {
        if (finished.get() || !started) {
            return;
        }
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(cfg.restBase() + "/gobang_msg?room=eq." + roomCode
                            + "&cid=neq." + cid
                            + "&id=gt." + lastId.get()
                            + "&order=id.asc&select=id,body"))
                    .timeout(Duration.ofSeconds(8))
                    .header("apikey", cfg.anonKey())
                    .header("Authorization", "Bearer " + cfg.anonKey())
                    .GET()
                    .build();
            HttpResponse<String> resp =
                    http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                onPollError("HTTP " + resp.statusCode());
                return;
            }
            pollErrors = 0;
            Object root = JsonKit.parse(resp.body());
            if (!(root instanceof List<?> arr)) {
                return;
            }
            for (Object o : arr) {
                if (!(o instanceof Map<?, ?> m)) {
                    continue;
                }
                Object id = m.get("id");
                Object body = m.get("body");
                if (id instanceof Long n && body instanceof String s) {
                    if (n > lastId.get()) {
                        lastId.set(n);
                    }
                    dispatchGameLine(s);
                    if (finished.get()) {
                        return;
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            onPollError(e.getClass().getSimpleName());
        }
    }

    private void onPollError(String what) {
        pollErrors++;
        if (pollErrors >= MAX_POLL_ERRORS && !finished.get()) {
            drop("网络异常：" + what);
        }
    }

    // ---------- 消息处理 ----------

    /** 与 NetLink.dispatch 相同语义；HELLO 在已握手后到达视为重复并忽略。 */
    private void dispatchGameLine(String line) {
        lastRecv.set(System.currentTimeMillis());
        if (line.length() > Protocol.MAX_LINE) {
            violate("消息超长");
            return;
        }
        Protocol.Message m;
        try {
            m = Protocol.decode(line);
        } catch (Protocol.ProtocolException e) {
            violate(e.getMessage());
            return;
        }
        if (!handshaken) {
            if (m.type() != Protocol.Type.HELLO) {
                violate("首条消息非 HELLO");
                return;
            }
            peerName = m.get("name");
            handshaken = true;
            cancelGuestTimer();
            log("PEER READY room=" + roomCode + " peer=" + peerName);
            NetLink.Listener ls = listener;
            if (ls != null) {
                ls.onPeerReady();
            }
            return;
        }
        switch (m.type()) {
            case PING -> postLine(Protocol.pong());
            case PONG, HELLO -> {
                // 保活 / 双方并发 HELLO 的重复帧
            }
            case BYE -> drop(m.get("reason"));
            default -> {
                NetLink.Listener ls = listener;
                if (ls != null) {
                    ls.onMessage(line);
                }
            }
        }
    }

    // ---------- HTTP ----------

    private void postLine(String line) {
        String json = JsonKit.objRaw(
                "room", JsonKit.str(roomCode),
                "cid", JsonKit.str(cid),
                "body", JsonKit.str(line));
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(cfg.restBase() + "/gobang_msg"))
                .timeout(Duration.ofSeconds(8))
                .header("apikey", cfg.anonKey())
                .header("Authorization", "Bearer " + cfg.anonKey())
                .header("Content-Type", "application/json")
                .header("Prefer", "return=minimal")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        http.sendAsync(req, HttpResponse.BodyHandlers.discarding())
                .orTimeout(10, TimeUnit.SECONDS)
                .whenComplete((r, ex) -> {
                    if (ex != null) {
                        // 创建期(未握手)连续失败也要暴露，避免永远「等待中」
                        int n = postErrors.incrementAndGet();
                        if (!finished.get() && (handshaken || n >= 5)) {
                            drop("连接服务器失败");
                        }
                    } else {
                        postErrors.set(0);
                        if (!finished.get()
                                && linkReadyFired.compareAndSet(false, true)) {
                            log("LINK READY room=" + roomCode);
                            NetLink.Listener ls = listener;
                            if (ls != null) {
                                ls.onLinkReady();
                            }
                        }
                    }
                });
    }

    /** 删除本房号全部残留行（房主开局清理）。 */
    private java.util.concurrent.CompletableFuture<HttpResponse<Void>> deleteRoomRows() {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(cfg.restBase() + "/gobang_msg?room=eq." + roomCode))
                .timeout(Duration.ofSeconds(8))
                .header("apikey", cfg.anonKey())
                .header("Authorization", "Bearer " + cfg.anonKey())
                .DELETE()
                .build();
        return http.sendAsync(req, HttpResponse.BodyHandlers.discarding());
    }

    // ---------- 断开 ----------

    private void drop(String reason) {
        if (!finished.compareAndSet(false, true)) {
            return;
        }
        log("DROP room=" + roomCode + " host=" + hostSide + " reason=" + reason);
        cancelTimers();
        NetLink.Listener ls = listener;
        if (ls != null) {
            ls.onDisconnected(reason == null ? "未知原因" : reason);
        }
    }

    /** 轻量联机日志（~/.gobang/net.log），便于远程问题定位。 */
    private static void log(String msg) {
        try {
            java.io.File dir = new java.io.File(
                    System.getProperty("user.home"), ".gobang");
            if (!dir.exists() && !dir.mkdirs()) {
                return;
            }
            java.io.PrintWriter out = new java.io.PrintWriter(
                    new java.io.FileWriter(new java.io.File(dir, "net.log"), true));
            out.println(new java.text.SimpleDateFormat("MM-dd HH:mm:ss")
                    .format(new java.util.Date()) + "  " + msg);
            out.close();
        } catch (Exception ignored) {
        }
    }

    private void violate(String detail) {
        postLine(Protocol.bye("协议违规"));
        drop("协议违规：" + detail);
    }

    private void cancelTimers() {
        if (pollTask != null) {
            pollTask.cancel(false);
        }
        if (pingTask != null) {
            pingTask.cancel(false);
        }
        if (checkTask != null) {
            checkTask.cancel(false);
        }
        cancelGuestTimer();
    }

    private void cancelGuestTimer() {
        if (guestTimer != null) {
            guestTimer.cancel(false);
            guestTimer = null;
        }
    }
}
