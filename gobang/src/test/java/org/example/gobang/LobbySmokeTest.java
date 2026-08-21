package org.example.gobang;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.example.gobang.audio.SettingsStore;
import org.example.gobang.fx.ForestBackground;
import org.example.gobang.fx.NetLobbyView;
import org.example.gobang.net.Link;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * spec4 v4.2：大厅「创建房间」真实点击流冒烟（依赖外网，默认跳过）。
 * 启用：mvn test "-Dsupa.it=true" -Dtest=LobbySmokeTest
 */
public class LobbySmokeTest {

    private static final AtomicReference<Throwable> ERR = new AtomicReference<>();
    private static final AtomicReference<Object> RESULT = new AtomicReference<>();

    private static void fx(Runnable r) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                r.run();
            } catch (Throwable t) {
                ERR.set(t);
            } finally {
                latch.countDown();
            }
        });
        latch.await(20, TimeUnit.SECONDS);
        if (ERR.get() != null) throw new RuntimeException(ERR.get());
    }

    private static <T> T fxVal(java.util.function.Supplier<T> r) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                RESULT.set(r.get());
            } catch (Throwable t) {
                ERR.set(t);
            } finally {
                latch.countDown();
            }
        });
        latch.await(20, TimeUnit.SECONDS);
        if (ERR.get() != null) throw new RuntimeException(ERR.get());
        @SuppressWarnings("unchecked")
        T v = (T) RESULT.get();
        RESULT.set(null);
        return v;
    }

    private static Button findButton(Node n, String text) {
        if (n instanceof Button b && text.equals(b.getText())) return b;
        if (n instanceof javafx.scene.Parent p) {
            for (Node c : p.getChildrenUnmodifiable()) {
                Button found = findButton(c, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static TextField findCodeField(Node n) {
        if (n instanceof TextField t && t.getPromptText().startsWith("例如 K7XQ")) return t;
        if (n instanceof javafx.scene.Parent p) {
            for (Node c : p.getChildrenUnmodifiable()) {
                TextField found = findCodeField(c);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** 大字房号 Label（40px 金色，等待态唯一可见标志）。 */
    private static Label findBigCodeLabel(Node n) {
        if (n instanceof Label l && l.getStyle().contains("-fx-font-size: 40px")) return l;
        if (n instanceof javafx.scene.Parent p) {
            for (Node c : p.getChildrenUnmodifiable()) {
                Label found = findBigCodeLabel(c);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Test
    void createButtonProducesWaitingStateAndLink() throws Exception {
        assumeTrue(Boolean.getBoolean("supa.it"), "需 -Dsupa.it=true 启用");
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException ignored) {
        }

        AtomicReference<Link> connected = new AtomicReference<>();
        AtomicReference<NetLobbyView> lobbyRef = new AtomicReference<>();
        fx(() -> {
            ForestBackground bg = new ForestBackground();
            bg.start();
            NetLobbyView lobby = new NetLobbyView(bg, new SettingsStore(),
                    connected::set, () -> { });
            Stage st = new Stage();
            st.setScene(new javafx.scene.Scene(lobby.getRoot(), 800, 900));
            st.setX(-10000);
            st.show();
            lobbyRef.set(lobby);
        });
        NetLobbyView lobby = lobbyRef.get();
        assertNotNull(lobby);

        // 场景一：输入自定义房号 → 创建（覆盖用户报告的场景）
        fx(() -> {
            TextField code = findCodeField(lobby.getRoot());
            assertNotNull(code, "找不到房号输入框");
            code.setText("T8ST");
        });
        fx(() -> {
            Button create = findButton(lobby.getRoot(), "创建房间");
            assertNotNull(create, "找不到 创建房间 按钮");
            create.fire();
        });

        // 等待大字房号出现（onLinkReady 后进入等待态）
        long deadline = System.currentTimeMillis() + 30000;
        boolean waiting = false;
        while (System.currentTimeMillis() < deadline) {
            Boolean vis = fxVal(() -> {
                Label big = findBigCodeLabel(lobby.getRoot());
                return big != null && big.isVisible() && !big.getText().isEmpty();
            });
            if (Boolean.TRUE.equals(vis)) {
                waiting = true;
                break;
            }
            Thread.sleep(200);
        }
        assertTrue(waiting, "30s 内未进入等待接入状态");
        String shownCode = fxVal(() -> findBigCodeLabel(lobby.getRoot()).getText());
        assertEquals("T8ST", shownCode, "应使用用户输入的房号");

        // 链路在线（单人无对手，不触发导航；经反射读取大厅持有的链路）
        Boolean active = fxVal(() -> {
            try {
                java.lang.reflect.Field f = NetLobbyView.class.getDeclaredField("supaLink");
                f.setAccessible(true);
                Object l = f.get(lobby);
                return l instanceof Link link && link.isActive();
            } catch (Exception e) {
                return Boolean.FALSE;
            }
        });
        assertEquals(Boolean.TRUE, active, "创建后链路应在线");

        fx(() -> {
            try {
                java.lang.reflect.Field f = NetLobbyView.class.getDeclaredField("supaLink");
                f.setAccessible(true);
                Object l = f.get(lobby);
                if (l instanceof Link link) {
                    link.close("测试结束");
                }
            } catch (Exception ignored) {
            }
        });
    }
}
