package org.example.gobang.fx;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.RotateTransition;
import javafx.animation.Timeline;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import org.example.gobang.audio.SettingsStore;
import org.example.gobang.audio.SoundManager;
import org.example.gobang.audio.SoundType;
import org.example.gobang.logic.GameSession;
import org.example.gobang.logic.ai.AICandidate;
import org.example.gobang.logic.ai.AIStrategy;
import org.example.gobang.logic.ai.EasyAI;
import org.example.gobang.logic.ai.HardAI;
import org.example.gobang.logic.ai.MediumAI;
import org.example.gobang.logic.ai.Point;
import org.example.gobang.logic.ai.ScoreEvaluator;
import org.example.gobang.model.Board;
import org.example.gobang.model.GameState;
import org.example.gobang.model.Move;
import org.example.gobang.model.MoveOutcome;

import java.util.List;

/**
 * 对局页：
 * 800×900 固定布局（顶栏 100 / 棋盘区 700 / 底栏 100）；
 * 棋盘原点 (50,100)，单元格 50px，棋子半径 21；
 * 悬停半透明预览、点击吸附、非法落子抖动+提示音；
 * AI 在 Task 后台线程计算，生成号防竞态，思考动画 + 落子呼吸光圈预览。
 */
public class GameView {

    // 棋盘几何（boardPane 局部坐标：棋盘原点 x=50,y=0）
    private static final int ORIGIN_X = 50;
    private static final int CELL = 50;
    private static final double STONE_R = 21;

    private final GameSession session;
    private final GameSession.Mode mode;
    private final GameSession.Difficulty difficulty;
    private final ForestBackground background;
    private final SettingsStore settings;
    private final Runnable onExit;

    private final StackPane root = new StackPane();
    private final Pane boardPane = new Pane();
    private final Group shakeGroup = new Group();
    private final Canvas boardCanvas = new Canvas(700, 700);
    private final Canvas stonesCanvas = new Canvas(700, 700);
    private final Canvas hoverCanvas = new Canvas(700, 700);
    private final Canvas fxCanvas = new Canvas(700, 700);
    private final ShakeEffect rootSway;
    private final WinEffect winEffect = new WinEffect(fxCanvas);

    private final Label turnLabel = new Label();
    private final Circle turnIcon = new Circle(13);
    private final HBox spinnerBox = new HBox(8);
    private RotateTransition spinnerRt;
    private final Label moveLabel = new Label();
    private final Label modeLabel = new Label();
    private final Button undoBtn;
    private final Button restartBtn;
    private final SettingsPanel settingsPanel;

    private boolean thinking;
    private int hoverRow = -1;
    private int hoverCol = -1;
    private long lastHoverSound;
    private PauseTransition previewDelay;
    private Timeline previewTimeline;
    private PauseTransition finishDelay;

    public GameView(ForestBackground bg, GameSession.Mode mode, GameSession.Difficulty difficulty,
                    SettingsStore settings, Runnable onExit) {
        this.background = bg;
        this.mode = mode;
        this.difficulty = difficulty;
        this.settings = settings;
        this.onExit = onExit;
        this.session = new GameSession(mode, difficulty);
        this.rootSway = new ShakeEffect(root);
        this.undoBtn = Ui.smallButton("悔棋");
        this.restartBtn = Ui.smallButton("重新开局");
        this.settingsPanel = new SettingsPanel(root, settings);
        build();
        drawBoard();
        renderStones();
        updateStatusUI();
        startGuess();
    }

    public StackPane getRoot() {
        return root;
    }

    // ---------- 布局 ----------

    private void build() {
        root.setPrefSize(800, 900);
        root.setMaxSize(800, 900);
        root.getChildren().add(background.getNode());

        VBox layout = new VBox();
        layout.setPrefSize(800, 900);
        layout.setMaxSize(800, 900);

        // 顶栏 0~100
        HBox top = new HBox(12);
        top.setPrefHeight(100);
        top.setMinHeight(100);
        top.setMaxHeight(100);
        top.setPadding(new Insets(18, 16, 8, 22));
        top.setAlignment(Pos.CENTER_LEFT);
        top.setStyle("-fx-background-color: rgba(22, 42, 24, 0.62); -fx-border-color: transparent transparent"
                + " #4a6b3a transparent; -fx-border-width: 0 0 2 0;");

        Label title = new Label("五子棋");
        title.setStyle("-fx-font-family: '" + Ui.FONT + "'; -fx-font-size: 28px; -fx-font-weight: bold;"
                + "-fx-text-fill: #f2e3bd;");
        turnIcon.setStroke(Color.web("#ffffff", 0.9));
        turnIcon.setStrokeWidth(1.5);
        turnLabel.setStyle("-fx-font-family: '" + Ui.FONT + "'; -fx-font-size: 20px; -fx-font-weight: bold;"
                + "-fx-text-fill: #f5e9cf;");

        Circle spin = new Circle(11);
        spin.setFill(null);
        spin.setStroke(Color.web("#f5e9cf"));
        spin.setStrokeWidth(3);
        spin.getStrokeDashArray().addAll(14.0, 10.0);
        spinnerRt = new RotateTransition(Duration.seconds(1), spin);
        spinnerRt.setFromAngle(0);
        spinnerRt.setToAngle(360);
        spinnerRt.setCycleCount(RotateTransition.INDEFINITE);
        spinnerRt.setInterpolator(Interpolator.LINEAR);
        spinnerBox.setAlignment(Pos.CENTER_LEFT);
        spinnerBox.getChildren().add(spin);
        spinnerBox.setVisible(false);

        Button gear = Ui.smallButton("⚙ 设置");
        gear.setOnAction(e -> settingsPanel.show());

        top.getChildren().addAll(title, turnIcon, turnLabel, spinnerBox, Ui.spacer(), gear, Ui.makerLabel());

        // 棋盘区 100~800
        boardPane.setPrefSize(800, 700);
        boardPane.setMinSize(800, 700);
        boardPane.setMaxSize(800, 700);
        shakeGroup.setLayoutX(ORIGIN_X);
        shakeGroup.setLayoutY(0);
        shakeGroup.getChildren().addAll(boardCanvas, stonesCanvas, hoverCanvas, fxCanvas);
        boardPane.getChildren().add(shakeGroup);
        bindMouse();

        // 底栏 800~900
        HBox bottom = new HBox(14);
        bottom.setPrefHeight(100);
        bottom.setMinHeight(100);
        bottom.setMaxHeight(100);
        bottom.setPadding(new Insets(10, 20, 10, 20));
        bottom.setAlignment(Pos.CENTER_LEFT);
        bottom.setStyle("-fx-background-color: rgba(22, 42, 24, 0.62); -fx-border-color: #4a6b3a transparent"
                + " transparent transparent; -fx-border-width: 2 0 0 0;");
        undoBtn.setOnAction(e -> onUndo());
        restartBtn.setOnAction(e -> onRestart());
        modeLabel.setStyle("-fx-font-family: '" + Ui.FONT + "'; -fx-font-size: 18px; -fx-text-fill: #e8d9b0;");
        moveLabel.setStyle("-fx-font-family: '" + Ui.FONT + "'; -fx-font-size: 18px; -fx-text-fill: #e8d9b0;");
        bottom.getChildren().addAll(undoBtn, restartBtn, Ui.spacer(), modeLabel, Ui.spacer(), moveLabel);

        layout.getChildren().addAll(top, boardPane, bottom);
        root.getChildren().add(layout);
    }

    private void bindMouse() {
        boardPane.setOnMouseMoved(e -> handleHover(e.getX(), e.getY()));
        boardPane.setOnMouseExited(e -> {
            clearHover();
            boardPane.setCursor(Cursor.DEFAULT);
        });
        boardPane.setOnMouseClicked(e -> handleClick(e.getX(), e.getY()));
    }

    // ---------- 坐标映射 ----------

    private static int clampIdx(int v) {
        return Math.max(0, Math.min(Board.SIZE - 1, v));
    }

    private static int rowAt(double y) {
        return clampIdx((int) Math.round(y / CELL));
    }

    private static int colAt(double x) {
        return clampIdx((int) Math.round((x - ORIGIN_X) / CELL));
    }

    // ---------- 渲染 ----------

    private void drawBoard() {
        GraphicsContext g = boardCanvas.getGraphicsContext2D();
        g.clearRect(0, 0, 700, 700);
        // 木质面板
        LinearGradient wood = new LinearGradient(0, 0, 700, 700, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#dcab64")),
                new Stop(0.5, Color.web("#c99452")),
                new Stop(1, Color.web("#ae7a3e")));
        g.setFill(wood);
        g.fillRoundRect(0, 0, 700, 700, 12, 12);
        g.setStroke(Color.web("#7a4e23"));
        g.setLineWidth(4);
        g.strokeRoundRect(2, 2, 696, 696, 12, 12);
        // 网格线
        g.setStroke(Color.web("#5d3a18"));
        g.setLineWidth(1.4);
        for (int i = 0; i < Board.SIZE; i++) {
            g.strokeLine(i * CELL, 0, i * CELL, 700);
            g.strokeLine(0, i * CELL, 700, i * CELL);
        }
        // 星位
        int[][] stars = {{7, 7}, {3, 3}, {11, 3}, {3, 11}, {11, 11}};
        g.setFill(Color.web("#5d3a18"));
        for (int[] s : stars) {
            g.fillOval(s[1] * CELL - 4, s[0] * CELL - 4, 8, 8);
        }
    }

    private void renderStones() {
        GraphicsContext g = stonesCanvas.getGraphicsContext2D();
        g.clearRect(0, 0, 700, 700);
        for (Move m : session.board.getHistory()) {
            drawStone(g, m.col * CELL, m.row * CELL, m.color);
        }
    }

    private void drawStone(GraphicsContext g, double cx, double cy, int color) {
        if (color == Board.BLACK) {
            RadialGradient rg = new RadialGradient(45, 0.35, cx - 8, cy - 9, 26, false, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.web("#8a8a8a")),
                    new Stop(0.35, Color.web("#3a3a3a")),
                    new Stop(1, Color.web("#050505")));
            g.setFill(rg);
            g.fillOval(cx - STONE_R, cy - STONE_R, STONE_R * 2, STONE_R * 2);
            g.setStroke(Color.web("#777777"));
            g.setLineWidth(1);
            g.strokeOval(cx - STONE_R, cy - STONE_R, STONE_R * 2, STONE_R * 2);
        } else {
            g.setFill(Color.rgb(0, 0, 0, 0.22));
            g.fillOval(cx - STONE_R + 2, cy - STONE_R + 3, STONE_R * 2, STONE_R * 2);
            RadialGradient rg = new RadialGradient(45, 0.3, cx - 8, cy - 9, 26, false, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.web("#ffffff")),
                    new Stop(0.6, Color.web("#e8e4dc")),
                    new Stop(1, Color.web("#c2bdb4")));
            g.setFill(rg);
            g.fillOval(cx - STONE_R, cy - STONE_R, STONE_R * 2, STONE_R * 2);
            g.setStroke(Color.web("#9a958c"));
            g.setLineWidth(1);
            g.strokeOval(cx - STONE_R, cy - STONE_R, STONE_R * 2, STONE_R * 2);
        }
    }

    private void clearFxCanvas() {
        fxCanvas.getGraphicsContext2D().clearRect(0, 0, 700, 700);
    }

    // ---------- 悬停 / 点击 ----------

    private void handleHover(double x, double y) {
        int r = rowAt(y);
        int c = colAt(x);
        boolean legal = session.getState() == GameState.PLAYING
                && !thinking
                && session.board.isEmpty(r, c)
                && (mode != GameSession.Mode.PVE || session.getCurrentColor() != session.aiColor());
        if (legal) {
            if (r != hoverRow || c != hoverCol) {
                hoverRow = r;
                hoverCol = c;
                long now = System.currentTimeMillis();
                if (now - lastHoverSound > 180) {
                    lastHoverSound = now;
                    SoundManager.play(SoundType.HOVER, 0.15);
                }
            }
            GraphicsContext hg = hoverCanvas.getGraphicsContext2D();
            hg.clearRect(0, 0, 700, 700);
            Color tint = session.getCurrentColor() == Board.BLACK
                    ? Color.rgb(0, 0, 0, 0.35) : Color.rgb(255, 255, 255, 0.38);
            hg.setFill(tint);
            hg.fillOval(c * CELL - STONE_R, r * CELL - STONE_R, STONE_R * 2, STONE_R * 2);
            boardPane.setCursor(Cursor.HAND);
        } else {
            if (hoverRow != -1) {
                clearHover();
            }
            boardPane.setCursor(Cursor.DEFAULT);
        }
    }

    private void clearHover() {
        hoverRow = -1;
        hoverCol = -1;
        hoverCanvas.getGraphicsContext2D().clearRect(0, 0, 700, 700);
    }

    private void handleClick(double x, double y) {
        if (thinking) return;
        if (session.getState() != GameState.PLAYING) return;
        if (mode == GameSession.Mode.PVE && session.getCurrentColor() == session.aiColor()) return;
        int r = rowAt(y);
        int c = colAt(x);
        if (!session.board.isEmpty(r, c)) {
            SoundManager.play(SoundType.INVALID, 0.8);
            return;
        }
        MoveOutcome oc = session.place(r, c, session.getCurrentColor());
        if (oc == null) return;
        afterMove(oc);
        if (oc.type == MoveOutcome.Type.NONE && mode == GameSession.Mode.PVE
                && session.getCurrentColor() == session.aiColor()) {
            startThinking();
        }
    }

    private void afterMove(MoveOutcome oc) {
        Move last = session.board.getLastMove();
        SoundManager.play(last.color == Board.BLACK ? SoundType.STONE_BLACK : SoundType.STONE_WHITE);
        renderStones();
        clearHover();
        updateStatusUI();
        if (oc.type == MoveOutcome.Type.WIN) {
            endGame(oc.winLine, last.color);
        } else if (oc.type == MoveOutcome.Type.DRAW) {
            endDraw();
        }
    }

    // ---------- AI 思考 ----------

    private AIStrategy createAI() {
        switch (difficulty) {
            case EASY: return new EasyAI();
            case HARD: return new HardAI();
            default: return new MediumAI();
        }
    }

    private void startThinking() {
        if (thinking) return;
        if (session.getState() != GameState.PLAYING) return;
        if (session.aiColor() == 0 || session.getCurrentColor() != session.aiColor()) return;
        thinking = true;
        clearHover();
        updateStatusUI();
        AIStrategy ai = createAI();
        int aiColor = session.aiColor();
        // 每个任务捕获自己的生成号 + 棋盘快照，杜绝：旧任务回调误落子新棋局、
        // AI 搜索线程与 FX 线程（悔棋/重开清盘）并发修改同一 Board。
        final int myGen = session.getGeneration();
        final Board snapshot = session.board.copy();
        Task<Point> task = new Task<>() {
            @Override
            protected Point call() {
                return ai.choose(snapshot, aiColor);
            }
        };
        task.setOnSucceeded(e -> {
            if (myGen != session.getGeneration()) return; // 过期任务，thinking 归新对局管理
            if (!thinking) {
                updateStatusUI();
                return;
            }
            Point p = task.getValue();
            if (p == null) {
                thinking = false;
                updateStatusUI();
                return;
            }
            previewAndPlace(myGen, p);
        });
        task.setOnFailed(e -> {
            // AI 计算异常：用启发式兜底落子，保证对局永不因异常卡死
            if (myGen != session.getGeneration()) return;
            if (!thinking) {
                updateStatusUI();
                return;
            }
            Point fallback = fallbackMove(snapshot, aiColor);
            if (fallback != null) {
                previewAndPlace(myGen, fallback);
            } else {
                thinking = false;
                updateStatusUI();
            }
        });
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    /** AI 计算异常时的兜底：按攻防合成分取最高点，保证总能落出合法一子。 */
    private Point fallbackMove(Board board, int color) {
        try {
            List<Point> cands = AICandidate.generate(board);
            if (cands.isEmpty()) return new Point(7, 7);
            Point best = null;
            int bestScore = Integer.MIN_VALUE;
            for (Point p : cands) {
                int s = ScoreEvaluator.combinedScore(board, p.row, p.col, color);
                if (s > bestScore) {
                    bestScore = s;
                    best = p;
                }
            }
            return best != null ? best : cands.get(0);
        } catch (Throwable t) {
            return new Point(7, 7);
        }
    }

    private void previewAndPlace(int myGen, Point p) {
        clearFxCanvas();
        final double cx = p.col * CELL;
        final double cy = p.row * CELL;
        GraphicsContext g = fxCanvas.getGraphicsContext2D();
        Color c = session.aiColor() == Board.BLACK ? Color.web("#ffd54a") : Color.web("#7ee0ff");
        previewTimeline = new Timeline(
                new KeyFrame(Duration.millis(0), e -> drawRing(g, cx, cy, 22, 4, c, 1)),
                new KeyFrame(Duration.millis(125), e -> drawRing(g, cx, cy, 24, 4, c, 0.45)),
                new KeyFrame(Duration.millis(250), e -> drawRing(g, cx, cy, 26, 4, c, 1)),
                new KeyFrame(Duration.millis(375), e -> drawRing(g, cx, cy, 28, 4, c, 0.45)),
                new KeyFrame(Duration.millis(500), e -> drawRing(g, cx, cy, 30, 4, c, 1))
        );
        previewTimeline.play();
        previewDelay = new PauseTransition(Duration.millis(500));
        previewDelay.setOnFinished(e -> {
            if (previewTimeline != null) {
                previewTimeline.stop();
            }
            clearFxCanvas();
            if (myGen != session.getGeneration()) return; // 过期预览，丢弃
            if (!thinking) {
                updateStatusUI();
                return;
            }
            thinking = false;
            MoveOutcome oc = session.place(p.row, p.col, session.aiColor());
            if (oc == null) {
                updateStatusUI();
                return;
            }
            afterMove(oc);
            if (oc.type == MoveOutcome.Type.NONE && mode == GameSession.Mode.PVE
                    && session.getCurrentColor() == session.aiColor()) {
                startThinking();
            }
        });
        previewDelay.play();
    }

    private void drawRing(GraphicsContext g, double cx, double cy, double r, double w, Color color, double a) {
        g.clearRect(0, 0, 700, 700);
        g.setStroke(Color.web(toHex(color), a * 0.25));
        g.setLineWidth(w + 5);
        g.strokeOval(cx - r, cy - r, r * 2, r * 2);
        g.setStroke(Color.web(toHex(color), a));
        g.setLineWidth(w);
        g.strokeOval(cx - r, cy - r, r * 2, r * 2);
    }

    private static String toHex(Color c) {
        return String.format("#%02x%02x%02x",
                (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255));
    }

    // ---------- 悔棋 / 重开 ----------

    private void onUndo() {
        if (thinking) return;
        if (session.getState() != GameState.PLAYING) return;
        List<Move> removed = session.undo();
        if (removed == null || removed.isEmpty()) return;
        SoundManager.play(SoundType.UNDO);
        clearFxCanvas();
        winEffect.stop();
        renderStones();
        updateStatusUI();
        // PvE：悔到 AI 回合（如 AI 执黑首步被悔）必须重新触发思考，否则卡死
        if (mode == GameSession.Mode.PVE && session.getState() == GameState.PLAYING
                && session.getCurrentColor() == session.aiColor()) {
            startThinking();
        }
    }

    private void onRestart() {
        showConfirm("重新开局？", "当前对局进度将丢失。", () -> {
            session.restart();
            cleanupEffects();
            renderStones();
            updateStatusUI();
            startGuess();
        });
    }

    private void startGuess() {
        thinking = false;
        GuessDialog dialog = new GuessDialog(session, root, () -> {
            updateStatusUI();
            if (session.aiColor() == Board.BLACK) {
                startThinking();
            }
        });
        dialog.show();
    }

    // ---------- 终局 ----------

    private void endGame(List<Move> winLine, int winnerColor) {
        boolean humanWins = mode == GameSession.Mode.PVP
                || (session.aiColor() != 0 && winnerColor != session.aiColor());
        SoundManager.play(humanWins ? SoundType.WIN : SoundType.LOSE);
        background.burstPetals();
        rootSway.shake(5, 600);
        winEffect.play(winLine);
        updateStatusUI();
        String title = winnerColor == Board.BLACK ? "黑方获胜！" : "白方获胜！";
        finishDelay = new PauseTransition(Duration.millis(1200));
        finishDelay.setOnFinished(e -> showFinish(title, "再来一局（交换黑白）", "返回菜单"));
        finishDelay.play();
    }

    private void endDraw() {
        SoundManager.play(SoundType.DRAW);
        background.burstPetals();
        updateStatusUI();
        finishDelay = new PauseTransition(Duration.millis(1200));
        finishDelay.setOnFinished(e -> showFinish("平局", "再来一局（交换黑白）", "返回菜单"));
        finishDelay.play();
    }

    private void showFinish(String title, String againText, String menuText) {
        VBox panel = basePanel();
        Label t = new Label(title);
        t.setStyle("-fx-font-family: '" + Ui.FONT + "'; -fx-font-size: 36px; -fx-font-weight: bold;"
                + "-fx-text-fill: #ffd54a;");
        Button again = Ui.styledButton(againText, 20);
        again.setOnAction(e -> {
            closeOverlay(panel);
            session.nextRound();
            cleanupEffects();
            renderStones();
            updateStatusUI();
            if (session.aiColor() == Board.BLACK) {
                startThinking();
            }
        });
        Button menu = Ui.styledButton(menuText, 20);
        menu.setOnAction(e -> {
            closeOverlay(panel);
            cleanup();
            onExit.run();
        });
        panel.getChildren().addAll(t, again, menu, Ui.makerLabel());
        overlayPanel(panel);
    }

    private void showConfirm(String title, String message, Runnable onOk) {
        VBox panel = basePanel();
        Label t = new Label(title);
        t.setStyle("-fx-font-family: '" + Ui.FONT + "'; -fx-font-size: 30px; -fx-font-weight: bold;"
                + "-fx-text-fill: #ffd54a;");
        Label msg = new Label(message);
        msg.setStyle("-fx-font-family: '" + Ui.FONT + "'; -fx-font-size: 18px; -fx-text-fill: #f0e0bd;");
        Button ok = Ui.styledButton("确认", 20);
        ok.setOnAction(e -> {
            closeOverlay(panel);
            onOk.run();
        });
        Button cancel = Ui.styledButton("取消", 20);
        cancel.setOnAction(e -> closeOverlay(panel));
        HBox row = new HBox(20);
        row.setAlignment(Pos.CENTER);
        row.getChildren().addAll(ok, cancel);
        panel.getChildren().addAll(t, msg, row, Ui.makerLabel());
        overlayPanel(panel);
    }

    private VBox basePanel() {
        VBox panel = new VBox(16);
        panel.setAlignment(Pos.CENTER);
        panel.setPadding(new Insets(30, 44, 22, 44));
        panel.setMaxWidth(520);
        panel.setStyle("-fx-background-color: rgba(56, 35, 15, 0.95); -fx-background-radius: 22;"
                + "-fx-border-color: #8a6a3a; -fx-border-radius: 22; -fx-border-width: 2;");
        return panel;
    }

    private void overlayPanel(VBox panel) {
        StackPane overlay = new StackPane();
        Rectangle dark = new Rectangle(800, 900, Color.rgb(0, 0, 0, 0.55));
        dark.setOnMouseClicked(e -> e.consume());
        overlay.getChildren().addAll(dark, panel);
        root.getChildren().add(overlay);
    }

    private void closeOverlay(VBox panel) {
        for (Node n : root.getChildren()) {
            if (n instanceof StackPane && ((StackPane) n).getChildren().contains(panel)) {
                root.getChildren().remove(n);
                return;
            }
        }
    }

    // ---------- 状态 UI ----------

    private void updateStatusUI() {
        int cur = session.getCurrentColor();
        GameState st = session.getState();
        if (st == GameState.FINISHED) {
            turnIcon.setFill(Color.web("#9aa5a0"));
            turnLabel.setText("对局结束");
            setSpinner(false);
        } else if (thinking) {
            turnIcon.setFill(Color.web("#c9b98f"));
            turnLabel.setText("AI 思考中…");
            setSpinner(true);
        } else {
            turnIcon.setFill(cur == Board.BLACK ? Color.web("#1c1c1c") : Color.web("#f4f4f4"));
            turnLabel.setText(cur == Board.BLACK ? "黑方回合" : "白方回合");
            setSpinner(false);
        }
        moveLabel.setText("第 " + session.board.getHistory().size() + " 手");
        modeLabel.setText(mode == GameSession.Mode.PVE
                ? "人机对战 · " + difficultyText() : "双人对战");
        boolean canUndo = st == GameState.PLAYING && !thinking
                && !session.board.getHistory().isEmpty();
        undoBtn.setDisable(!canUndo);
    }

    private String difficultyText() {
        switch (difficulty) {
            case EASY: return "简单";
            case HARD: return "困难";
            default: return "中等";
        }
    }

    private void cleanupEffects() {
        if (previewDelay != null) {
            previewDelay.stop();
            previewDelay = null;
        }
        if (previewTimeline != null) {
            previewTimeline.stop();
            previewTimeline = null;
        }
        if (finishDelay != null) {
            finishDelay.stop();
            finishDelay = null;
        }
        winEffect.stop();
        rootSway.stop();
        setSpinner(false);
        clearFxCanvas();
        clearHover();
    }

    private void setSpinner(boolean on) {
        spinnerBox.setVisible(on);
        if (on) {
            spinnerRt.play();
        } else {
            spinnerRt.pause();
        }
    }

    private void cleanup() {
        cleanupEffects();
        thinking = false;
    }
}