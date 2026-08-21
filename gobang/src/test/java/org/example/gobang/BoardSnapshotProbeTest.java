package org.example.gobang;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.image.PixelReader;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import org.example.gobang.audio.SettingsStore;
import org.example.gobang.fx.ForestBackground;
import org.example.gobang.fx.GameView;
import org.example.gobang.fx.MenuView;
import org.example.gobang.logic.GameSession;
import org.example.gobang.model.Board;
import org.example.gobang.model.Move;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 视觉与全流程实测：验证修复后的棋盘渲染坐标正确，并跑通完整对局（人机/双人）。
 */
public class BoardSnapshotProbeTest {

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private static void fx(ThrowingRunnable r) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                r.run();
            } catch (Throwable t) {
                t.printStackTrace();
                throw new RuntimeException(t);
            } finally {
                latch.countDown();
            }
        });
        latch.await(20, TimeUnit.SECONDS);
    }

    private static <T> T fxVal(ThrowingSupplier<T> r) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<T> out = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                out.set(r.get());
            } catch (Throwable t) {
                t.printStackTrace();
                throw new RuntimeException(t);
            } finally {
                latch.countDown();
            }
        });
        latch.await(20, TimeUnit.SECONDS);
        return out.get();
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

    private static GameSession sessionOf(GameView gv) throws Exception {
        Field f = GameView.class.getDeclaredField("session");
        f.setAccessible(true);
        return (GameSession) f.get(gv);
    }

    // 新几何常量（spec2 §2.1）：shakeGroup 偏移 50 / 木框 INSET=GRID=28 / 格距 46
    private static final int PANE_OFF_X = 50;
    private static final int GRID = 28;
    private static final int CELL = 46;

    private static void click(Pane boardPane, int row, int col) {
        double x = PANE_OFF_X + GRID + col * (double) CELL + 1;
        double y = GRID + row * (double) CELL + 1;
        MouseEvent ev = new MouseEvent(MouseEvent.MOUSE_CLICKED, x, y, x, y,
                MouseButton.PRIMARY, 1, false, false, false, false,
                true, false, false, true, true, false, null);
        boardPane.fireEvent(ev);
    }

    private static void removeOverlays(GameView gv) {
        StackPane root = gv.getRoot();
        for (int i = root.getChildren().size() - 1; i >= 0; i--) {
            Node n = root.getChildren().get(i);
            if (n instanceof StackPane sp && sp.getChildren().stream()
                    .anyMatch(c -> c instanceof javafx.scene.shape.Rectangle)) {
                root.getChildren().remove(i);
            }
        }
    }

    private static javafx.scene.canvas.Canvas stonesCanvas(GameView gv) throws Exception {
        Field f = GameView.class.getDeclaredField("stonesCanvas");
        f.setAccessible(true);
        return (javafx.scene.canvas.Canvas) f.get(gv);
    }

    private static javafx.scene.canvas.Canvas boardCanvas(GameView gv) throws Exception {
        Field f = GameView.class.getDeclaredField("boardCanvas");
        f.setAccessible(true);
        return (javafx.scene.canvas.Canvas) f.get(gv);
    }

    private static javafx.scene.canvas.Canvas fxCanvas(GameView gv) throws Exception {
        Field f = GameView.class.getDeclaredField("fxCanvas");
        f.setAccessible(true);
        return (javafx.scene.canvas.Canvas) f.get(gv);
    }

    private static void rerender(GameView gv) throws Exception {
        Method drawBoard = GameView.class.getDeclaredMethod("drawBoard");
        drawBoard.setAccessible(true);
        drawBoard.invoke(gv);
        Method renderStones = GameView.class.getDeclaredMethod("renderStones", boolean.class);
        renderStones.setAccessible(true);
        renderStones.invoke(gv, false);
    }

    private static java.awt.Color colorAt(javafx.scene.canvas.Canvas c, int x, int y) {
        PixelReader pr = c.snapshot(new javafx.scene.SnapshotParameters(), null).getPixelReader();
        javafx.scene.paint.Color col = pr.getColor(x, y);
        return new java.awt.Color(
                (int) Math.round(col.getRed() * 255),
                (int) Math.round(col.getGreen() * 255),
                (int) Math.round(col.getBlue() * 255),
                (int) Math.round(col.getOpacity() * 255));
    }

    private static boolean isDark(java.awt.Color c) {
        return c.getRed() < 140 && c.getGreen() < 140 && c.getBlue() < 140;
    }

    private static boolean isLight(java.awt.Color c) {
        return c.getRed() > 200 && c.getGreen() > 200 && c.getBlue() > 200;
    }

    @Test
    void stoneRenderingPositionsAreCorrect() throws Exception {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException ignored) {
        }
        AtomicReference<GameView> ref = new AtomicReference<>();
        fx(() -> {
            ForestBackground bg = new ForestBackground();
            bg.start();
            GameView gv = new GameView(bg, GameSession.Mode.PVP, GameSession.Difficulty.MEDIUM,
                    new SettingsStore(), () -> { });
            ref.set(gv);
            Stage st = new Stage();
            Scene sc = new Scene(gv.getRoot(), 800, 900);
            st.setScene(sc);
            st.setX(-10000);
            st.show();
        });
        Thread.sleep(400);

        fx(() -> {
            GameView gv = ref.get();
            removeOverlays(gv);
            GameSession s = sessionOf(gv);
            s.applyGuess(false, false, 1, true);
            s.place(7, 3, Board.BLACK);
            s.place(5, 5, Board.WHITE);
            s.place(7, 4, Board.BLACK);
            s.place(6, 5, Board.WHITE);
            s.place(7, 5, Board.BLACK);
            rerender(gv);

            // 黑子中心 = (GRID+col*46, GRID+row*46)
            assertTrue(isDark(colorAt(stonesCanvas(gv), GRID + 3 * CELL, GRID + 7 * CELL)),
                    "黑子(7,3)应画在(" + (GRID + 3 * CELL) + "," + (GRID + 7 * CELL) + ")");
            assertTrue(isDark(colorAt(stonesCanvas(gv), GRID + 4 * CELL, GRID + 7 * CELL)),
                    "黑子(7,4)应画在(" + (GRID + 4 * CELL) + "," + (GRID + 7 * CELL) + ")");
            assertTrue(isDark(colorAt(stonesCanvas(gv), GRID + 5 * CELL, GRID + 7 * CELL)),
                    "黑子(7,5)应画在(" + (GRID + 5 * CELL) + "," + (GRID + 7 * CELL) + ")");
            // 白子
            assertTrue(isLight(colorAt(stonesCanvas(gv), GRID + 5 * CELL, GRID + 5 * CELL)),
                    "白子(5,5)应画在格中心");
            assertTrue(isLight(colorAt(stonesCanvas(gv), GRID + 5 * CELL, GRID + 6 * CELL)),
                    "白子(6,5)应画在格中心");
            // 反证：转置位置不应是黑子（row/col 不应混淆）
            assertFalse(isDark(colorAt(stonesCanvas(gv), 350, 150)),
                    "转置位置(350,150)不应是黑子（row/col 不应混淆）");
            // 星位 {11,3} 与 {3,11}
            assertTrue(isDark(colorAt(boardCanvas(gv), GRID + 3 * CELL, GRID + 11 * CELL)),
                    "星位(11,3)应在格中心");
            assertTrue(isDark(colorAt(boardCanvas(gv), GRID + 11 * CELL, GRID + 3 * CELL)),
                    "星位(3,11)应在格中心");
        });
    }

    @Test
    void fullPveGameplaySimulation() throws Exception {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException ignored) {
        }
        AtomicReference<GameView> ref = new AtomicReference<>();
        fx(() -> {
            ForestBackground bg = new ForestBackground();
            bg.start();
            GameView gv = new GameView(bg, GameSession.Mode.PVE, GameSession.Difficulty.EASY,
                    new SettingsStore(), () -> { });
            ref.set(gv);
            Stage st = new Stage();
            Scene sc = new Scene(gv.getRoot(), 800, 900);
            st.setScene(sc);
            st.setX(-10000);
            st.show();
        });
        Thread.sleep(400);

        // 关掉猜先弹窗，强制人执黑、AI 执白
        fx(() -> {
            GameView gv = ref.get();
            removeOverlays(gv);
            GameSession s = sessionOf(gv);
            s.applyGuess(false, true, 1, false); // 人执黑
            java.lang.reflect.Method updateUI = GameView.class.getDeclaredMethod("updateStatusUI");
            updateUI.setAccessible(true);
            updateUI.invoke(gv);
        });

        GameView gv = ref.get();
        Pane boardPane = fxVal(() -> findBoardPane(gv.getRoot()));
        assertNotNull(boardPane);

        Method hc = GameView.class.getDeclaredMethod("handleClick", double.class, double.class);
        hc.setAccessible(true);

        // 人执黑：连下三子，等待 AI 回应，验证不卡死、棋子出现在点击处
        int[] cells = {7, 3, 7, 4, 7, 5};
        for (int i = 0; i < 6; i += 2) {
            int r = cells[i], c = cells[i + 1];
            double x = PANE_OFF_X + GRID + c * (double) CELL + 1;
            double y = GRID + r * (double) CELL + 1;
            fx(() -> {
                try {
                    hc.invoke(gv, x, y);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });
            // 等待落子
            Thread.sleep(150);
            int hist = fxVal(() -> sessionOf(gv).board.getHistory().size());
            assertTrue(hist >= (i / 2) + 1, "第" + (i / 2 + 1) + "手后应有 " + (i / 2 + 1) + " 子，实际=" + hist);
            // 验证黑子画在点击处（格中心）
            final int fr = r, fc = c;
            boolean placed = fxVal(() -> {
                rerender(gv);
                return isDark(colorAt(stonesCanvas(gv), GRID + fc * CELL, GRID + fr * CELL));
            });
            assertTrue(placed, "黑子(" + r + "," + c + ")应画在点击处");
            // 等待 AI 回应（最多 3 秒）：轮到人且非思考中
            long deadline = System.currentTimeMillis() + 3000;
            while (System.currentTimeMillis() < deadline) {
                boolean done = fxVal(() -> {
                    GameSession s = sessionOf(gv);
                    return s.getState() != org.example.gobang.model.GameState.PLAYING
                            || (s.getCurrentColor() == Board.BLACK && !s.isAI(Board.BLACK));
                });
                if (done) break;
                Thread.sleep(100);
            }
            int n2 = fxVal(() -> sessionOf(gv).board.getHistory().size());
            assertTrue(n2 >= (i / 2) + 2, "AI 应在时限内落子（第 " + (i / 2 + 1) + " 轮），实际手数=" + n2);
        }

        // 悔棋：应撤 2 子，回到人回合
        fx(() -> {
            Button undo = (Button) findNode(gv.getRoot(), "↩ 悔棋");
            assertNotNull(undo);
            undo.fire();
        });
        Thread.sleep(200);
        int afterUndo = fxVal(() -> sessionOf(gv).board.getHistory().size());
        assertEquals(4, afterUndo, "悔棋后应剩 4 子（撤 2 子）");
        boolean humanTurn = fxVal(() -> sessionOf(gv).getCurrentColor() == Board.BLACK
                && !sessionOf(gv).isAI(Board.BLACK));
        assertTrue(humanTurn, "悔棋后应轮到人（黑）");

        // 连下五子强制终局（构造确定局面，绕开 AI 随机占位干扰）
        fx(() -> {
            GameSession s = sessionOf(gv);
            // 撤掉 2 子（AI 白 + 人黑），回到人回合
            s.undo();
            s.undo();
        });
        fx(() -> rerender(gv));
        fx(() -> {
            GameSession s = sessionOf(gv);
            // 直接构造：黑 (0,0)(0,1)(0,2)(0,3)，再补一白子让黑方落子，随后点击 (0,4) 成五连
            s.placeAny(0, 0, Board.BLACK);
            s.placeAny(0, 1, Board.BLACK);
            s.placeAny(0, 2, Board.BLACK);
            s.placeAny(0, 3, Board.BLACK);
            s.placeAny(14, 14, Board.WHITE);
            rerender(gv);
        });
        fx(() -> {
            try {
                hc.invoke(gv, PANE_OFF_X + GRID + 4 * (double) CELL + 1, GRID + 0 * (double) CELL + 1);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
        // 等待终局
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            if (fxVal(() -> sessionOf(gv).getState() == org.example.gobang.model.GameState.FINISHED)) break;
            Thread.sleep(100);
        }
        // 等待落子动画(360ms)+光带扫描完成（victory 内 t≈1150ms 后光带常亮）
        Thread.sleep(1500);
        boolean finished = fxVal(() -> sessionOf(gv).getState() == org.example.gobang.model.GameState.FINISHED);
        assertTrue(finished, "连五后应终局");

        // 验证胜利光带画在第 0 行（y=GRID，从 (GRID,GRID) 到 (GRID+4*46,GRID)），而非转置的第 0 列
        fx(() -> rerender(gv));
        Thread.sleep(300);
        java.awt.Color linePix = fxVal(() -> colorAt(fxCanvas(gv), GRID + 2 * CELL, GRID));
        java.awt.Color wrongPix = fxVal(() -> colorAt(fxCanvas(gv), 650, 650));
        assertTrue(linePix.getRed() > 200 && linePix.getBlue() < 200,
                "胜利连线应横贯第 0 行（格中心处有金色连线），实际=" + linePix);
        assertTrue(wrongPix.getBlue() > 200,
                "远离连线处不应有金色连线（空白区呈白底），实际=" + wrongPix);
    }

    @Test
    void menuLayoutPositionsAreReasonable() throws Exception {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException ignored) {
        }
        AtomicReference<MenuView> ref = new AtomicReference<>();
        fx(() -> {
            ForestBackground bg = new ForestBackground();
            bg.start();
            MenuView menu = new MenuView(bg, new SettingsStore(), (m, d) -> { }, () -> { });
            ref.set(menu);
            Stage st = new Stage();
            Scene sc = new Scene(menu.getRoot(), 800, 900);
            st.setScene(sc);
            st.setX(-10000);
            st.show();
        });
        Thread.sleep(300);
        fx(() -> {
            MenuView menu = ref.get();
            javafx.scene.layout.StackPane mr = menu.getRoot();
            Node gear = findNode(mr, "⚙ 设置");
            Node maker = findNode(mr, org.example.gobang.fx.Ui.MAKER);
            assertNotNull(gear, "应找到设置按钮");
            assertNotNull(maker, "应找到制作标注");
            javafx.geometry.Bounds gb = gear.localToScene(gear.getBoundsInLocal());
            javafx.geometry.Bounds mb = maker.localToScene(maker.getBoundsInLocal());
            // 设置按钮应在右上角：x 靠右（>600）、y 靠上（<120）
            assertTrue(gb.getMinX() > 600, "设置按钮应在右上角（x>600），实际 x=" + gb.getMinX());
            assertTrue(gb.getMinY() < 120, "设置按钮应在右上角（y<120），实际 y=" + gb.getMinY());
            // 制作标注应在底部居中：x 居中（300~500）、y 靠下（>820）
            assertTrue(mb.getMinX() > 300 && mb.getMinX() < 500,
                    "制作标注应底部居中（x 在 300~500），实际 x=" + mb.getMinX());
            assertTrue(mb.getMinY() > 820, "制作标注应靠下（y>820），实际 y=" + mb.getMinY());
            // 主标题应在中部（避免与顶栏/底栏重叠）
            Node title = findNode(mr, "五子棋");
            assertNotNull(title, "应找到标题");
            javafx.geometry.Bounds tb = title.localToScene(title.getBoundsInLocal());
            assertTrue(tb.getMinY() > 200 && tb.getMinY() < 600,
                    "标题应居中不重叠（y 在 200~600），实际 y=" + tb.getMinY());
        });
    }

    @Test
    void returnToMenuKeepsBackground() throws Exception {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException ignored) {
        }
        AtomicReference<Throwable> err = new AtomicReference<>();
        fx(() -> {
            try {
                org.example.gobang.Main main = new org.example.gobang.Main();
                Stage st = new Stage();
                javafx.scene.Scene sc = new javafx.scene.Scene(new javafx.scene.layout.StackPane(), 800, 900);
                st.setScene(sc);
                st.setX(-10000);
                main.start(st);
                st.show();
                javafx.scene.Scene actualScene = st.getScene();

                Method openGame = org.example.gobang.Main.class.getDeclaredMethod("openGame",
                        GameSession.Mode.class, GameSession.Difficulty.class);
                openGame.setAccessible(true);
                Field menuField = org.example.gobang.Main.class.getDeclaredField("menu");
                menuField.setAccessible(true);
                MenuView menuView = (MenuView) menuField.get(main);
                javafx.scene.layout.StackPane menuRoot = menuView.getRoot();
                openGame.invoke(main, GameSession.Mode.PVE, GameSession.Difficulty.EASY);
                // 切页为异步过渡（淡出 140ms）：轮询等待场景根切换为 GameView
                long waitDeadline = System.currentTimeMillis() + 3000;
                boolean switched = false;
                while (System.currentTimeMillis() < waitDeadline) {
                    Boolean isMenu = fxVal(() -> st.getScene().getRoot() == menuRoot);
                    if (!isMenu) {
                        switched = true;
                        break;
                    }
                    Thread.sleep(50);
                }
                assertTrue(switched, "开一局后场景根应是 GameView");

                Method openMenu = org.example.gobang.Main.class.getDeclaredMethod("openMenu");
                openMenu.setAccessible(true);
                openMenu.invoke(main);
                // 返回菜单同样异步：轮询等待
                waitDeadline = System.currentTimeMillis() + 3000;
                boolean back = false;
                while (System.currentTimeMillis() < waitDeadline) {
                    Boolean isMenu = fxVal(() -> st.getScene().getRoot() == menuRoot);
                    if (isMenu) {
                        back = true;
                        break;
                    }
                    Thread.sleep(50);
                }
                assertTrue(back, "返回后场景根应是菜单");
                javafx.scene.layout.StackPane mr = menuRoot;
                Field bgField = org.example.gobang.Main.class.getDeclaredField("background");
                bgField.setAccessible(true);
                ForestBackground bg = (ForestBackground) bgField.get(main);
                assertTrue(mr.getChildren().contains(bg.getNode()), "菜单根应重新包含背景节点");
                assertTrue(mr.getChildren().indexOf(bg.getNode()) == 0, "背景节点应在菜单根底部");
                // 快照验证菜单不是白屏：晨雾天空应呈冷色调（蓝>红，且非纯白）
                javafx.scene.image.PixelReader pr = mr.snapshot(
                        new javafx.scene.SnapshotParameters(), null).getPixelReader();
                javafx.scene.paint.Color sky = pr.getColor(400, 60);
                assertFalse(sky.getRed() > 0.92 && sky.getGreen() > 0.92 && sky.getBlue() > 0.92,
                        "菜单不应白屏，实际=" + sky);
                assertTrue(sky.getBlue() > 0.55 && sky.getBlue() > sky.getRed(),
                        "菜单顶部应为晨雾天空（蓝>红），实际=" + sky);
                st.close();
            } catch (Throwable t) {
                err.set(t);
            }
        });
        if (err.get() != null) {
            throw new RuntimeException(err.get());
        }
    }

    private static Node findNode(Node n, String text) {
        if (n instanceof Button b && text.equals(b.getText())) return b;
        if (n instanceof javafx.scene.control.Label l && text.equals(l.getText())) return l;
        if (n instanceof javafx.scene.Parent p) {
            for (Node c : p.getChildrenUnmodifiable()) {
                Node found = findNode(c, text);
                if (found != null) return found;
            }
        }
        return null;
    }
}