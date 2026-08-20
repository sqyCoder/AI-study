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

    private static void click(Pane boardPane, int row, int col) {
        double x = 50 + col * 50.0 + 1;
        double y = row * 50.0 + 1;
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
        Method renderStones = GameView.class.getDeclaredMethod("renderStones");
        renderStones.setAccessible(true);
        renderStones.invoke(gv);
    }

    private static java.awt.Color colorAt(javafx.scene.canvas.Canvas c, int x, int y) {
        PixelReader pr = c.snapshot(new javafx.scene.SnapshotParameters(), null).getPixelReader();
        javafx.scene.paint.Color col = pr.getColor(x, y);
        return new java.awt.Color(
                (int) Math.round(col.getRed() * 255),
                (int) Math.round(col.getGreen() * 255),
                (int) Math.round(col.getBlue() * 255));
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

            // 黑子应在 (col*50, row*50)
            assertTrue(isDark(colorAt(stonesCanvas(gv), 3 * 50, 7 * 50)), "黑子(7,3)应画在(150,350)");
            assertTrue(isDark(colorAt(stonesCanvas(gv), 4 * 50, 7 * 50)), "黑子(7,4)应画在(200,350)");
            assertTrue(isDark(colorAt(stonesCanvas(gv), 5 * 50, 7 * 50)), "黑子(7,5)应画在(250,350)");
            // 白子应在 (col*50, row*50)
            assertTrue(isLight(colorAt(stonesCanvas(gv), 5 * 50, 5 * 50)), "白子(5,5)应画在(250,250)");
            assertTrue(isLight(colorAt(stonesCanvas(gv), 5 * 50, 6 * 50)), "白子(6,5)应画在(250,300)");
            // 反证：转置位置(350,150)等处应是棋盘木色(深)而非黑子
            // 转置位置(7*50,3*50)=(350,150) 处不应是黑子
            assertFalse(isDark(colorAt(stonesCanvas(gv), 7 * 50, 3 * 50)),
                    "转置位置(350,150)不应是黑子（row/col 不应混淆）");
            // 星位正确：{11,3} 应在(150,550)，{3,11} 应在(550,150)
            assertTrue(isDark(colorAt(boardCanvas(gv), 3 * 50, 11 * 50)), "星位(11,3)应画在(150,550)");
            assertTrue(isDark(colorAt(boardCanvas(gv), 11 * 50, 3 * 50)), "星位(3,11)应画在(550,150)");
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
            double x = 50 + c * 50.0 + 1;
            double y = r * 50.0 + 1;
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
            // 验证黑子画在点击处
            final int fr = r, fc = c;
            boolean placed = fxVal(() -> {
                rerender(gv);
                return isDark(colorAt(stonesCanvas(gv), fc * 50, fr * 50));
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
            Button undo = (Button) findNode(gv.getRoot(), "悔棋");
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
                hc.invoke(gv, 50 + 4 * 50.0 + 1, 0 * 50.0 + 1);
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
        Thread.sleep(600);
        boolean finished = fxVal(() -> sessionOf(gv).getState() == org.example.gobang.model.GameState.FINISHED);
        assertTrue(finished, "连五后应终局");

        // 验证胜利连线画在第 0 行（y=0，从 (0,0) 到 (200,0)），而非转置的第 0 列
        fx(() -> rerender(gv));
        Thread.sleep(300);
        java.awt.Color linePix = fxVal(() -> colorAt(fxCanvas(gv), 100, 0));
        java.awt.Color wrongPix = fxVal(() -> colorAt(fxCanvas(gv), 0, 100));
        assertTrue(linePix.getRed() > 200 && linePix.getBlue() < 200,
                "胜利连线应横贯第 0 行（(100,0) 处有金色连线），实际=" + linePix);
        assertTrue(wrongPix.getBlue() > 200,
                "胜利连线不应画在转置的第 0 列（(0,100) 应为空白），实际=" + wrongPix);
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
            MenuView menu = new MenuView(bg, new SettingsStore(), (m, d) -> { });
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
                // 开一局后，背景节点应被 GameView 拿走（不在菜单根中）
                assertFalse(actualScene.getRoot() == menuRoot, "开一局后场景根应是 GameView");

                Method openMenu = org.example.gobang.Main.class.getDeclaredMethod("openMenu");
                openMenu.setAccessible(true);
                openMenu.invoke(main);
                // 返回菜单后：场景根是菜单根，且背景节点放回底部
                assertTrue(actualScene.getRoot() == menuRoot, "返回后场景根应是菜单");
                javafx.scene.layout.StackPane mr = menuRoot;
                Field bgField = org.example.gobang.Main.class.getDeclaredField("background");
                bgField.setAccessible(true);
                ForestBackground bg = (ForestBackground) bgField.get(main);
                assertTrue(mr.getChildren().contains(bg.getNode()), "菜单根应重新包含背景节点");
                assertTrue(mr.getChildren().indexOf(bg.getNode()) == 0, "背景节点应在菜单根底部");
                // 快照验证菜单不是白屏：天空区域应呈蓝色调
                javafx.scene.image.PixelReader pr = mr.snapshot(
                        new javafx.scene.SnapshotParameters(), null).getPixelReader();
                javafx.scene.paint.Color sky = pr.getColor(400, 60);
                assertTrue(sky.getBlue() > 0.4, "菜单天空应呈蓝色而非白屏，实际=" + sky);
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