package org.example.gobang.net.supa;

import org.example.gobang.net.NetLink;
import org.example.gobang.net.Protocol;
import org.junit.jupiter.api.Test;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * spec4 §4：真实连 Supabase 的双客户端互通（依赖外网与已初始化的 gobang_msg 表，
 * 默认跳过）。启用：mvn test "-Dsupa.it=true" -Dtest=SupaRestLinkIT
 */
class SupaRestLinkIT {

    @Test
    void twoClientsFullSequence() throws Exception {
        assumeTrue(Boolean.getBoolean("supa.it"), "需 -Dsupa.it=true 启用");
        SupaConfig cfg = SupaConfig.load();
        assumeTrue(cfg != null, "缺少 supabase.properties");

        String code = RoomCodes.generate();
        LinkedBlockingQueue<String> inHost = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<String> inGuest = new LinkedBlockingQueue<>();
        CountDownLatch hostReady = new CountDownLatch(1);
        CountDownLatch guestReady = new CountDownLatch(1);
        AtomicReference<String> hostDrop = new AtomicReference<>();
        AtomicInteger hostDropCount = new AtomicInteger();

        // 缩短轮询间隔，加快测试节奏
        SupaRestLink.pollIntervalMs = 200;

        SupaRestLink host = new SupaRestLink(cfg, code, true);
        SupaRestLink guest = new SupaRestLink(cfg, code, false);

        host.start("房主", new NetLink.Listener() {
            @Override
            public void onMessage(String line) {
                inHost.add(line);
            }

            @Override
            public void onDisconnected(String reason) {
                hostDrop.set(reason);
                hostDropCount.incrementAndGet();
            }

            @Override
            public void onPeerReady() {
                hostReady.countDown();
            }
        });
        guest.start("客人", new NetLink.Listener() {
            @Override
            public void onMessage(String line) {
                inGuest.add(line);
            }

            @Override
            public void onDisconnected(String reason) {
                // 记录不中断
            }

            @Override
            public void onPeerReady() {
                guestReady.countDown();
            }
        });

        assertTrue(hostReady.await(30, TimeUnit.SECONDS), "房主未就绪");
        assertTrue(guestReady.await(30, TimeUnit.SECONDS), "客人未就绪");
        assertEquals("客人", host.peerName());
        assertEquals("房主", guest.peerName());

        // 双向消息（含协议复用验证）
        assertTrue(host.send(Protocol.move(7, 8, 1)));
        assertEquals(Protocol.Type.MOVE, Protocol.decode(poll(inGuest)).type());
        assertTrue(guest.send(Protocol.undoReq()));
        assertEquals(Protocol.Type.UNDO_REQ, Protocol.decode(poll(inHost)).type());
        assertTrue(host.send(Protocol.guessCommit("a".repeat(64))));
        assertEquals(Protocol.Type.GUESS_COMMIT, Protocol.decode(poll(inGuest)).type());

        // 客人离开 → 房主收到断线回调恰一次
        guest.close("测试结束");
        long deadline = System.currentTimeMillis() + 30000;
        while (hostDropCount.get() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        assertEquals(1, hostDropCount.get());
        assertEquals("测试结束", hostDrop.get());
        host.close("清理");
    }

    private static String poll(LinkedBlockingQueue<String> q) throws Exception {
        String line = q.poll(30, TimeUnit.SECONDS);
        assertNotNull(line, "等待消息超时");
        return line;
    }
}
