package org.example.gobang;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import org.example.gobang.audio.SettingsStore;
import org.example.gobang.fx.ForestBackground;
import org.example.gobang.fx.GameView;
import org.example.gobang.fx.MenuView;
import org.example.gobang.logic.GameSession;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UI 冒烟测试：验证真实交互链路（菜单 → 猜先 → 棋盘落子）。
 * FX 线程负责 UI 操作，主线程负责轮询与等待（不能在 FX 线程 sleep）。
 */
public class UiSmokeTest {

    private static final AtomicReference<Throwable> ERR = new AtomicReference<>();
    private static final AtomicReference<Object> RESULT = new AtomicReference<>();

    /** 在 FX 线程执行 r，主线程等待其完成。 */
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

    /** 在 FX 线程执行 r，把返回值放到 RESULT。 */
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

    private static Button findButton(StackPane root, String text) {
        return (Button) findButtonNode(root, text);
    }

    private static Node findButtonNode(Node n, String text) {
        if (n instanceof Button b && text.equals(b.getText())) return b;
        if (n instanceof javafx.scene.Parent p) {
            for (Node c : p.getChildrenUnmodifiable()) {
                Node found = findButtonNode(c, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static boolean hasOverlay(StackPane root) {
        for (Node n : root.getChildren()) {
            if (n instanceof StackPane sp && sp.getChildren().stream()
                    .anyMatch(c -> c instanceof javafx.scene.shape.Rectangle)) {
                return true;
            }
        }
        return false;
    }

    private static Pane findBoardPane(Node n) {
        if (n instanceof Pane p && p.getPrefWidth() == 800 && p.getPrefHeight() == 700) {
            return p;
        }
        if (n instanceof javafx.scene.Parent parent) {
            for (Node c : parent.getChildrenUnmodifiable()) {
                Pane found = findBoardPane(c);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static int countStones(GameView gv) {
        try {
            java.lang.reflect.Field f = GameView.class.getDeclaredField("session");
            f.setAccessible(true);
            GameSession s = (GameSession) f.get(gv);
            return s.board.getHistory().size();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 轮询等待某个按钮出现（最多 timeoutMs）。 */
    private static Button awaitButton(GameView game, String text, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Button b = fxVal(() -> findButton(game.getRoot(), text));
            if (b != null) return b;
            Thread.sleep(100);
        }
        return null;
    }

    @Test
    void menuRendersButtons() throws Exception {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException ignored) {
        }
        ERR.set(null);
        fx(() -> {
            ForestBackground bg = new ForestBackground();
            bg.start();
            SettingsStore settings = new SettingsStore();
            MenuView menu = new MenuView(bg, settings, (m, d) -> { }, () -> { });
            javafx.stage.Stage st = new javafx.stage.Stage();
            javafx.scene.Scene sc = new javafx.scene.Scene(menu.getRoot(), 800, 900);
            st.setScene(sc);
            st.setX(-10000);
            st.show();
            javafx.scene.layout.StackPane mr = menu.getRoot();
            Button start = findButton(mr, "开始对局");
            Button pve = findButton(mr, "人机对战");
            Button pvp = findButton(mr, "双人对战");
            assertNotNull(start);
            assertNotNull(pve);
            assertNotNull(pvp);

            // 快照并检查按钮像素是否为奶油底色
            javafx.scene.image.WritableImage img = mr.snapshot(
                    new javafx.scene.SnapshotParameters(), null);
            checkButtonPixel(img, start, "开始对局");
            checkButtonPixel(img, pve, "人机对战");
            checkButtonPixel(img, pvp, "双人对战");
            // 保存快照供人工查看（纯 JDK，不依赖 javafx.swing）
            try {
                int w = (int) img.getWidth(), h = (int) img.getHeight();
                javafx.scene.image.PixelReader pr2 = img.getPixelReader();
                java.awt.image.BufferedImage bi = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < h; y++)
                    for (int x = 0; x < w; x++)
                        bi.setRGB(x, y, pr2.getArgb(x, y));
                javax.imageio.ImageIO.write(bi, "png",
                        new java.io.File("C:/Users/12426/AppData/Local/Temp/opencode/menu_snapshot.png"));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }

    private static void checkButtonPixel(javafx.scene.image.WritableImage img, Button b, String name) {
        javafx.scene.image.PixelReader pr = img.getPixelReader();
        javafx.geometry.Point2D p = b.localToScene(b.getWidth() / 2, b.getHeight() / 2);
        // 采样按钮中心 11x11 区域，统计可见像素（非全透明、非森林背景色）
        int visible = 0, total = 0;
        for (int dy = -5; dy <= 5; dy += 2) {
            for (int dx = -5; dx <= 5; dx += 2) {
                int x = (int) (p.getX() + dx), y = (int) (p.getY() + dy);
                if (x < 0 || y < 0 || x >= img.getWidth() || y >= img.getHeight()) continue;
                javafx.scene.paint.Color c = pr.getColor(x, y);
                total++;
                boolean cream = c.getRed() > 0.8 && c.getGreen() > 0.7 && c.getBlue() > 0.4;
                boolean green = c.getGreen() > 0.5 && c.getRed() > 0.3 && c.getBlue() < 0.7;
                if (cream || green) visible++;
            }
        }
        javafx.scene.paint.Color c = pr.getColor((int) p.getX(), (int) p.getY());
        System.out.printf("button %s center scene=(%.0f,%.0f) color=rgb(%.0f,%.0f,%.0f) visible=%d/%d%n",
                name, p.getX(), p.getY(), c.getRed() * 255, c.getGreen() * 255, c.getBlue() * 255, visible, total);
        assertTrue(visible >= total / 2, name + " 按钮区域应有一半以上可见底色，实际=" + visible + "/" + total);
    }

    @Test
    void menuButtonsAreHitTestable() throws Exception {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException ignored) {
        }
        ERR.set(null);
        fx(() -> {
            ForestBackground bg = new ForestBackground();
            bg.start();
            SettingsStore settings = new SettingsStore();
            MenuView menu = new MenuView(bg, settings, (m, d) -> { }, () -> { });
            javafx.stage.Stage st = new javafx.stage.Stage();
            javafx.scene.Scene sc = new javafx.scene.Scene(menu.getRoot(), 800, 900);
            st.setScene(sc);
            st.setX(-10000);
            st.show();
            javafx.scene.layout.StackPane mr = menu.getRoot();
            Button start = findButton(mr, "开始对局");
            Button pve = findButton(mr, "人机对战");
            Button pvp = findButton(mr, "双人对战");
            assertNotNull(start);
            assertNotNull(pve);
            assertNotNull(pvp);
            assertPick(mr, start, "开始对局");
            assertPick(mr, pve, "人机对战");
            assertPick(mr, pvp, "双人对战");
        });
    }

    /** 模拟 JavaFX 命中测试（pick），验证真实鼠标点击能命中按钮。 */
    private static void assertPick(javafx.scene.layout.StackPane root, Button b, String name) {
        javafx.geometry.Point2D c = b.localToScene(b.getWidth() / 2, b.getHeight() / 2);
        Node picked = pickAt(root, c.getX(), c.getY());
        System.out.printf("pick(%s) at scene(%.0f,%.0f) -> %s%n", name, c.getX(), c.getY(),
                picked == null ? "null" : picked.getClass().getSimpleName());
        assertNotNull(picked, name + " 应可被命中，实际命中为 null");
        assertTrue(isSelfOrDescendant(b, picked),
                name + " 应命中按钮本身或其后代，实际命中=" + describe(picked));
    }

    private static String describe(Node n) {
        StringBuilder sb = new StringBuilder();
        for (Node x = n; x != null; x = x.getParent()) {
            if (sb.length() > 0) sb.append(" -> ");
            sb.append(x.getClass().getSimpleName());
        }
        return sb.toString();
    }

    private static boolean isSelfOrDescendant(Node ancestor, Node n) {
        for (Node x = n; x != null; x = x.getParent()) {
            if (x == ancestor) return true;
        }
        return false;
    }

    private static Node pickAt(Node root, double sceneX, double sceneY) {
        if (root == null || root.isMouseTransparent() || !root.isVisible()) return null;
        javafx.geometry.Point2D local = root.sceneToLocal(sceneX, sceneY);
        if (!root.contains(local.getX(), local.getY())) return null;
        if (root instanceof javafx.scene.Parent) {
            javafx.scene.Parent p = (javafx.scene.Parent) root;
            java.util.List<Node> kids = p.getChildrenUnmodifiable();
            for (int i = kids.size() - 1; i >= 0; i--) {
                Node hit = pickAt(kids.get(i), sceneX, sceneY);
                if (hit != null) return hit;
            }
        }
        return root;
    }

    @Test
    void fullInteractionFlow() throws Exception {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException ignored) {
        }
        ERR.set(null);

        AtomicReference<ForestBackground> bgRef = new AtomicReference<>();
        AtomicReference<GameSession.Mode> modeRef = new AtomicReference<>();
        AtomicReference<GameSession.Difficulty> diffRef = new AtomicReference<>();
        AtomicReference<GameView> gameRef = new AtomicReference<>();

        fx(() -> {
            ForestBackground bg = new ForestBackground();
            bg.start();
            SettingsStore settings = new SettingsStore();
            MenuView menu = new MenuView(bg, settings,
                    (m, d) -> { modeRef.set(m); diffRef.set(d); }, () -> { });
            // 挂载真实 Stage+Scene，强制 layout
            javafx.stage.Stage st = new javafx.stage.Stage();
            javafx.scene.Scene sc = new javafx.scene.Scene(menu.getRoot(), 800, 900);
            st.setScene(sc);
            st.setX(-10000); // 屏幕外，避免闪烁
            st.show();
            javafx.scene.layout.StackPane mr = menu.getRoot();
            Button start = findButton(mr, "开始对局");
            assertNotNull(start, "找不到 开始对局 按钮");
            Button pve = findButton(mr, "人机对战");
            Button pvp = findButton(mr, "双人对战");
            assertNotNull(pve, "找不到 人机对战 按钮");
            assertNotNull(pvp, "找不到 双人对战 按钮");
            pvp.fire();
            start.fire();
            assertEquals(GameSession.Mode.PVP, modeRef.get(), "选双人后开始应进入 PVP");
            pve.fire();
            start.fire();
            assertEquals(GameSession.Mode.PVE, modeRef.get(), "选人机后开始应进入 PVE");
            start.fire();
            assertNotNull(modeRef.get(), "点击 开始对局 应触发回调");
            bgRef.set(bg);
            GameView gv = new GameView(bg, modeRef.get(), diffRef.get(), settings, () -> { });
            gameRef.set(gv);
            // 保持 Stage 打开，并把对局根挂载进去，保证动画脉冲持续
            sc.setRoot(gv.getRoot());
        });

        GameView game = gameRef.get();

        // 猜先：人可能持子（握1/握2）或猜子（单/双），AI 方自动。兼容两种随机分配。
        Button hold1 = awaitButton(game, "握 1 颗", 2500);
        if (hold1 != null) {
            fx(() -> hold1.fire());
            // 若人是猜子方，随后会出现「单数」按钮
            Button odd = awaitButton(game, "单数", 1500);
            if (odd != null) {
                fx(() -> odd.fire());
            }
        } else {
            Button odd = awaitButton(game, "单数", 5000);
            assertNotNull(odd, "猜先应显示 单数 按钮（mode=" + modeRef.get() + "）");
            fx(() -> odd.fire());
        }

        // 等待猜先弹窗关闭（揭晓 1150 + 判定 1700）
        long deadline = System.currentTimeMillis() + 8000;
        boolean closed = false;
        while (System.currentTimeMillis() < deadline) {
            closed = fxVal(() -> !hasOverlay(game.getRoot()));
            if (closed) break;
            Thread.sleep(150);
        }
        assertTrue(closed, "猜先弹窗应已关闭");

        // 若轮到 AI 先行（AI 执黑），等待 AI 落子完成后再由人落子
        long thinkDeadline = System.currentTimeMillis() + 6000;
        while (System.currentTimeMillis() < thinkDeadline) {
            int n = fxVal(() -> countStones(game));
            boolean isAiTurn = fxVal(() -> {
                try {
                    java.lang.reflect.Field f = GameView.class.getDeclaredField("session");
                    f.setAccessible(true);
                    GameSession s = (GameSession) f.get(game);
                    return s.getState() != org.example.gobang.model.GameState.FINISHED
                            && s.getCurrentColor() == s.aiColor();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            if (!isAiTurn) break;
            Thread.sleep(200);
        }

        // 人落子：点击棋盘 (350, 350) → 行7 列6（避免与 AI 首手天元冲突）
        Pane boardPane = fxVal(() -> findBoardPane(game.getRoot()));
        assertNotNull(boardPane, "找不到棋盘 pane");
        int before = fxVal(() -> countStones(game));
        fx(() -> fireClick(boardPane, 350, 350));
        Thread.sleep(300);
        int after = fxVal(() -> countStones(game));
        assertEquals(before + 1, after, "点击棋盘应落 1 子（before=" + before + " after=" + after + "）");
    }

    private static void fireClick(Node n, double x, double y) {
        n.fireEvent(new MouseEvent(MouseEvent.MOUSE_PRESSED, x, y, x, y, MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, true, true, false, null));
        n.fireEvent(new MouseEvent(MouseEvent.MOUSE_RELEASED, x, y, x, y, MouseButton.PRIMARY, 1,
                false, false, false, false, false, false, true, true, true, false, null));
        n.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, x, y, x, y, MouseButton.PRIMARY, 1,
                false, false, false, false, false, false, false, true, true, false, null));
    }
}