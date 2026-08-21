package org.example.gobang.net;

import org.example.gobang.logic.GameSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * spec3 §11 用例 12~18：本机回环集成（127.0.0.1 随机端口）。
 * 心跳参数注入缩短，用秒级窗口等价覆盖「静默无误判」逻辑。
 */
class LoopbackGameIT {

    private NetLink.HostTicket ticket;

    @AfterEach
    void tearDown() {
        if (ticket != null) {
            ticket.cancel();
            ticket = null;
        }
    }

    /** 一对已握手完成的连接 + 各自收包队列。 */
    private record Pair(NetLink a, NetLink b,
                        LinkedBlockingQueue<String> inA, LinkedBlockingQueue<String> inB,
                        AtomicReference<String> dropA, AtomicReference<String> dropB,
                        AtomicInteger dropCountA, AtomicInteger dropCountB) {
    }

    private Pair connect() throws Exception {
        AtomicReference<NetLink> hosted = new AtomicReference<>();
        var hostReady = new java.util.concurrent.CountDownLatch(1);
        ticket = NetLink.host(0, link -> {
            hosted.set(link);
            hostReady.countDown();
        }, err -> {
            throw new AssertionError("host 失败: " + err);
        });
        assertNotNull(ticket);

        AtomicReference<NetLink> joined = new AtomicReference<>();
        var joinReady = new java.util.concurrent.CountDownLatch(1);
        NetLink.join("127.0.0.1", ticket.port(), link -> {
            joined.set(link);
            joinReady.countDown();
        }, err -> {
            throw new AssertionError("join 失败: " + err);
        });
        // host 的 onReady 在首个连接被 accept 时触发，须先发起 join 再等待
        assertTrue(hostReady.await(5, TimeUnit.SECONDS));
        assertTrue(joinReady.await(5, TimeUnit.SECONDS));

        NetLink a = hosted.get();
        NetLink b = joined.get();
        assertNotNull(a);
        assertNotNull(b);

        LinkedBlockingQueue<String> inA = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<String> inB = new LinkedBlockingQueue<>();
        AtomicReference<String> dropA = new AtomicReference<>();
        AtomicReference<String> dropB = new AtomicReference<>();
        AtomicInteger dropCountA = new AtomicInteger();
        AtomicInteger dropCountB = new AtomicInteger();

        a.start("房主", new NetLink.Listener() {
            @Override
            public void onMessage(String line) {
                inA.add(line);
            }

            @Override
            public void onDisconnected(String reason) {
                dropA.set(reason);
                dropCountA.incrementAndGet();
            }
        });
        b.start("客人", new NetLink.Listener() {
            @Override
            public void onMessage(String line) {
                inB.add(line);
            }

            @Override
            public void onDisconnected(String reason) {
                dropB.set(reason);
                dropCountB.incrementAndGet();
            }
        });

        // 握手在读线程异步完成：轮询等待双方拿到 peerName
        long deadline = System.currentTimeMillis() + 5000;
        while ((a.peerName().isEmpty() || b.peerName().isEmpty())
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertEquals("客人", a.peerName());
        assertEquals("房主", b.peerName());
        return new Pair(a, b, inA, inB, dropA, dropB, dropCountA, dropCountB);
    }

    private static String poll(LinkedBlockingQueue<String> q) throws Exception {
        String line = q.poll(5, TimeUnit.SECONDS);
        assertNotNull(line, "等待消息超时");
        return line;
    }

    // 12. host+join 握手 HELLO 互通（connect() 内已断言 peerName）
    @Test
    void handshakeExchangesNames() throws Exception {
        Pair p = connect();
        assertTrue(p.a().isActive());
        assertTrue(p.b().isActive());
    }

    // 13. 完整猜先协议序列跑通，两侧 applyGuess 结果一致
    @Test
    void guessCeremonyConsistent() throws Exception {
        Pair p = connect();
        GameSession hostSession = new GameSession(GameSession.Mode.ONLINE, null);
        GameSession guestSession = new GameSession(GameSession.Mode.ONLINE, null);

        // 房主承诺 → 客人猜单 → 房主揭示 → 双方校验并应用
        GuessCrypto.Commit commit = GuessCrypto.createCommit();
        assertTrue(p.a().send(Protocol.guessCommit(commit.hashHex())));
        assertEquals(Protocol.Type.GUESS_COMMIT, Protocol.decode(poll(p.inB())).type());

        boolean guestGuessOdd = true;
        assertTrue(p.b().send(Protocol.guessChoice(guestGuessOdd)));
        Protocol.Message choice = Protocol.decode(poll(p.inA()));
        assertEquals(Protocol.Type.GUESS_CHOICE, choice.type());
        assertEquals(guestGuessOdd, "1".equals(choice.get("odd")));

        assertTrue(p.a().send(Protocol.guessReveal(commit.count(), commit.salt())));
        Protocol.Message reveal = Protocol.decode(poll(p.inB()));
        assertEquals(Protocol.Type.GUESS_REVEAL, reveal.type());
        int count = Integer.parseInt(reveal.get("count"));
        byte[] salt = unhex(reveal.get("salt"));
        assertTrue(GuessCrypto.verify(new GuessCrypto.Reveal(count, salt), commit.hashHex()));

        hostSession.applyGuess(false, false, count, guestGuessOdd);
        guestSession.applyGuess(false, false, count, guestGuessOdd);
        // 双侧对称：同参数 applyGuess 后必然同为 PLAYING、黑先、盘面一致
        // （guesserWins 判定函数已由 SessionTest 覆盖）
        assertEquals(org.example.gobang.model.GameState.PLAYING, hostSession.getState());
        assertEquals(org.example.gobang.model.GameState.PLAYING, guestSession.getState());
        assertEquals(1, hostSession.getCurrentColor());
        assertEquals(1, guestSession.getCurrentColor());
    }

    private static byte[] unhex(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    // 14. 交替 MOVE 20 手，两侧 history 完全一致
    @Test
    void twentyMovesStayInSync() throws Exception {
        Pair p = connect();
        GameSession sa = new GameSession(GameSession.Mode.ONLINE, null);
        GameSession sb = new GameSession(GameSession.Mode.ONLINE, null);
        sa.applyGuess(false, false, 1, true); // 双侧对称初始化
        sb.applyGuess(false, false, 1, true);

        for (int i = 0; i < 20; i++) {
            int r = i / 15;
            int c = i % 15;
            int color = sa.getCurrentColor();
            GameSession mover = i % 2 == 0 ? sa : sb;
            GameSession watcher = i % 2 == 0 ? sb : sa;
            LinkedBlockingQueue<String> inbox = i % 2 == 0 ? p.inB() : p.inA();

            assertNotNull(mover.place(r, c, color));
            NetLink moverLink = i % 2 == 0 ? p.a() : p.b();
            assertTrue(moverLink.send(Protocol.move(r, c, color)));
            Protocol.Message m = Protocol.decode(poll(inbox));
            assertEquals(color, Integer.parseInt(m.get("color")));
            assertNotNull(watcher.place(Integer.parseInt(m.get("r")),
                    Integer.parseInt(m.get("c")), Integer.parseInt(m.get("color"))));
        }
        assertEquals(sa.board.getHistory(), sb.board.getHistory());
        assertEquals(20, sa.board.getHistory().size());
    }

    // 15. UNDO_REQ→OK 双侧各撤 2 子一致
    @Test
    void undoNegotiationSymmetric() throws Exception {
        Pair p = connect();
        GameSession sa = new GameSession(GameSession.Mode.ONLINE, null);
        GameSession sb = new GameSession(GameSession.Mode.ONLINE, null);
        sa.applyGuess(false, false, 2, false);
        sb.applyGuess(false, false, 2, false);
        for (int i = 0; i < 4; i++) {
            sa.placeAny(i / 15, i % 15, sa.getCurrentColor());
            sb.placeAny(i / 15, i % 15, sb.getCurrentColor());
        }
        assertEquals(4, sa.board.getHistory().size());
        assertEquals(4, sb.board.getHistory().size());

        assertTrue(p.a().send(Protocol.undoReq()));
        assertEquals(Protocol.Type.UNDO_REQ, Protocol.decode(poll(p.inB())).type());
        assertTrue(p.b().send(Protocol.undoOk()));
        assertEquals(Protocol.Type.UNDO_OK, Protocol.decode(poll(p.inA())).type());

        List<?> ha = sa.undo();
        List<?> hb = sb.undo();
        assertEquals(2, ha.size());
        assertEquals(2, hb.size());
        assertEquals(sa.board.getHistory(), sb.board.getHistory());
    }

    // 16. REMATCH 双侧 nextRound 后 currentColor==BLACK 且盘面清空
    @Test
    void rematchResetsBothSides() throws Exception {
        Pair p = connect();
        GameSession sa = new GameSession(GameSession.Mode.ONLINE, null);
        GameSession sb = new GameSession(GameSession.Mode.ONLINE, null);
        sa.applyGuess(false, false, 1, true);
        sb.applyGuess(false, false, 1, true);
        sa.placeAny(7, 7, 1);
        sb.placeAny(7, 7, 1);

        assertTrue(p.a().send(Protocol.rematchReq()));
        assertEquals(Protocol.Type.REMATCH_REQ, Protocol.decode(poll(p.inB())).type());
        assertTrue(p.b().send(Protocol.rematchOk()));
        assertEquals(Protocol.Type.REMATCH_OK, Protocol.decode(poll(p.inA())).type());

        sa.nextRound();
        sb.nextRound();
        assertTrue(sa.board.getHistory().isEmpty());
        assertTrue(sb.board.getHistory().isEmpty());
        assertEquals(1, sa.getCurrentColor());
        assertEquals(1, sb.getCurrentColor());
    }

    // 17. 一端 close，另一端 onDisconnected 恰好触发一次
    @Test
    void closeFiresSingleDisconnect() throws Exception {
        Pair p = connect();
        p.a().close("返回菜单");
        long deadline = System.currentTimeMillis() + 5000;
        while (p.dropCountB().get() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(1, p.dropCountB().get());
        assertEquals("返回菜单", p.dropB().get());
        assertNull(p.dropA().get(), "本地主动 close 不应触发自身回调");
        Thread.sleep(200); // 观察窗口：确认不重复触发
        assertEquals(1, p.dropCountB().get());
    }

    // 18. 静默期心跳保活无误判（缩短参数等价覆盖生产 20s 场景）
    @Test
    void heartbeatKeepsIdleLinkAlive() throws Exception {
        NetLink.pingIntervalMs = 100;
        NetLink.deadAfterMs = 500;
        NetLink.checkIntervalMs = 100;
        try {
            Pair p = connect();
            Thread.sleep(1500); // 覆盖 ≥3 个超时判定窗、15 个心跳周期
            assertTrue(p.a().isActive());
            assertTrue(p.b().isActive());
            assertEquals(0, p.dropCountA().get());
            assertEquals(0, p.dropCountB().get());
        } finally {
            NetLink.pingIntervalMs = 5000;
            NetLink.deadAfterMs = 15000;
            NetLink.checkIntervalMs = 3000;
        }
    }
}
