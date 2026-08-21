package org.example.gobang.fx;

import javafx.animation.AnimationTimer;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.PauseTransition;
import javafx.animation.RotateTransition;
import javafx.application.Platform;
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
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
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
import org.example.gobang.net.Link;
import org.example.gobang.net.NetLink;
import org.example.gobang.net.Protocol;
import org.example.gobang.model.Board;
import org.example.gobang.model.GameState;
import org.example.gobang.model.Move;
import org.example.gobang.model.MoveOutcome;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 对局页（spec2 全面精修）：
 * 800×900 固定布局（顶栏 100 / 棋盘区 700 / 底栏 100）；
 * 棋盘几何：木框 INSET=28，格距 CELL=46，网格起点 GRID=28，棋子半径 20；
 * 云子/象牙材质、坐标标注、最后一手呼吸金环；
 * 落子全套物理动效（StoneAnimator）、悬停 ghost+参考线+四角括号、
 * AI 瞄准环锁定、终局大片演出（VictorySequence）、玻璃拟态弹窗 spring 弹入。
 */
public class GameView {

    // ---------- 棋盘几何（boardPane 局部坐标：shakeGroup 位于 x=PANE_OFF_X） ----------
    private static final double PANE_OFF_X = 50;
    private static final double OFF_Y = 100;
    private static final double INSET = 28;
    private static final double CELL = 46;
    private static final double GRID = 28;
    static final double STONE_R = 20;
    private static final int[] STARS = {707, 303, 1103, 311, 1111}; // row*100+col

    private final GameSession session;
    private final GameSession.Mode mode;
    private final GameSession.Difficulty difficulty;
    private final ForestBackground background;
    private final SettingsStore settings;
    private final Runnable onExit;
    private final Random rnd = new Random();

    private final StackPane root = new StackPane();
    private final Pane boardPane = new Pane();
    private final Group shakeGroup = new Group();
    private final Canvas boardCanvas = new Canvas(700, 700);
    private final Canvas stonesCanvas = new Canvas(700, 700);
    private final Canvas markCanvas = new Canvas(700, 700);
    private final Canvas hoverCanvas = new Canvas(700, 700);
    private final Canvas fxCanvas = new Canvas(700, 700);
    private final ShakeEffect rootSway;
    private final ShakeEffect boardSway;
    private final StoneAnimator stoneAnimator;
    private final VictorySequence victory;

    private final Label turnLabel = new Label();
    private final Circle turnIcon = new Circle(11);
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
    private PauseTransition finishDelay;
    private StackPane currentOverlay;

    // AI 瞄准环状态
    private AnimationTimer aimTimer;
    private long aimStart = -1;
    private Point aimPoint;
    private int aimGen;
    private Color aimColor;
    private boolean aimLocked;

    private boolean victoryActive;

    // ---------- 联机状态（spec3 §5 / spec4 §1） ----------
    private final Link link;             // null = 本地模式；P2P 或 Supabase 传输
    private int myColor;                 // ONLINE：猜先结果写入（0=未定）
    private Consumer<Protocol.Message> guessHandler;
    private boolean pendingUndo;         // 已发悔棋请求等待回应
    private PauseTransition undoTimeout;
    private boolean rematchWaiting;
    private boolean disconnected;        // 断线弹窗已弹出（幂等）
    private String statusHint;           // 顶栏临时提示（对方拒绝悔棋等）
    private PauseTransition hintTimer;
    private final Circle connDot = new Circle(6);

    public GameView(ForestBackground bg, GameSession.Mode mode, GameSession.Difficulty difficulty,
                    SettingsStore settings, Runnable onExit) {
        this(bg, mode, difficulty, settings, onExit, null);
    }

    /** 联机构造（spec3 §5 / spec4 §1）：link 非 null 即 ONLINE 模式，difficulty 传 null。 */
    public GameView(ForestBackground bg, GameSession.Mode mode, GameSession.Difficulty difficulty,
                    SettingsStore settings, Runnable onExit, Link link) {
        this.background = bg;
        this.mode = mode;
        this.difficulty = difficulty;
        this.settings = settings;
        this.onExit = onExit;
        this.link = link;
        this.session = new GameSession(mode, difficulty);
        this.rootSway = new ShakeEffect(root);
        this.boardSway = new ShakeEffect(shakeGroup);
        this.stoneAnimator = new StoneAnimator(new StoneAnimator.CanvasFx() {
            @Override
            public GraphicsContext g() {
                return fxCanvas.getGraphicsContext2D();
            }

            @Override
            public void clear() {
                clearFxCanvas();
            }
        });
        this.victory = new VictorySequence(fxCanvas, new VictorySequence.Host() {
            @Override
            public void dimStones(Set<Long> winKeys, double dimAlpha) {
                GameView.this.dimStones(winKeys, dimAlpha);
            }

            @Override
            public void shake(double amp, double durMs) {
                rootSway.shake(amp, durMs);
            }

            @Override
            public Node sinkNode() {
                return root;
            }

            @Override
            public Particles particles() {
                return Particles.get();
            }
        }, CELL, GRID, STONE_R);
        this.undoBtn = Ui.smallButton("↩ 悔棋");
        this.restartBtn = Ui.smallButton("⟳ 重新开局");
        this.settingsPanel = new SettingsPanel(root, settings);
        build();
        drawBoard();
        renderStones(false);
        updateStatusUI();
        if (link != null) {
            startNet();
        }
        startGuess();
        // 最后一手呼吸标记：复用全局粒子 timer（spec2 §3.3）
        Particles.get().addFrameHook(this::drawMarker);
    }

    public StackPane getRoot() {
        return root;
    }

    // ---------- 布局 ----------

    private void build() {
        Theme.applyCss(root);
        root.setPrefSize(800, 900);
        root.setMaxSize(800, 900);
        root.getChildren().add(background.getNode());

        VBox layout = new VBox();
        layout.setPrefSize(800, 900);
        layout.setMaxSize(800, 900);

        // 顶栏 0~100：玻璃拟态横条 + 底缘金渐变分隔线
        HBox top = new HBox(12);
        top.setPrefHeight(100);
        top.setMinHeight(100);
        top.setMaxHeight(100);
        top.setPadding(new Insets(18, 16, 8, 22));
        top.setAlignment(Pos.CENTER_LEFT);
        top.setStyle("-fx-background-color: rgba(14,26,16,0.62);");

        Region topLine = new Region();
        topLine.setPrefHeight(1);
        topLine.setMaxHeight(1);
        topLine.setStyle("-fx-background-color: linear-gradient(to right, transparent,"
                + " rgba(232,196,122,0.5), transparent);");
        StackPane.setAlignment(topLine, Pos.BOTTOM_CENTER);

        StackPane topWrap = new StackPane(top, topLine);
        topWrap.setPrefSize(800, 100);
        topWrap.setMaxSize(800, 100);

        Label title = Theme.titleLabel("五子棋", 26, Theme.CREAM);
        turnIcon.setStroke(Color.web("#ffffff", 0.9));
        turnIcon.setStrokeWidth(1.5);
        // 联机连接指示点（spec3 §5-12）：绿=正常 红=断开
        connDot.setFill(Color.web("#3fae4f"));
        connDot.setStroke(Color.web("#ffffff", 0.6));
        connDot.setStrokeWidth(1);
        if (mode != GameSession.Mode.ONLINE) {
            connDot.setVisible(false);
            connDot.setManaged(false);
        }
        turnLabel.setStyle("-fx-font-family: '" + Theme.FONT_BODY + "'; -fx-font-size: 20px;"
                + "-fx-font-weight: bold; -fx-text-fill: " + Theme.CREAM + ";");
        HBox turnCapsule = new HBox(8, turnIcon, turnLabel, connDot);
        turnCapsule.setAlignment(Pos.CENTER_LEFT);
        turnCapsule.setPadding(new Insets(6, 14, 6, 14));
        turnCapsule.setStyle("-fx-background-color: rgba(18,30,20,0.66);"
                + "-fx-background-radius: 18; -fx-border-color: rgba(232,196,122,0.45);"
                + "-fx-border-radius: 18; -fx-border-width: 1;");

        Circle spin = new Circle(11);
        spin.setFill(null);
        spin.setStroke(Color.web(Theme.GOLD_BRIGHT));
        spin.setStrokeWidth(3);
        spin.getStrokeDashArray().addAll(14.0, 10.0);
        spin.setEffect(new DropShadow(8, Color.web(Theme.GOLD_BRIGHT, 0.8)));
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

        top.getChildren().addAll(title, turnCapsule, spinnerBox, Theme.spacer(), gear, Ui.makerLabel());

        // 棋盘区 100~800
        boardPane.setPrefSize(800, 700);
        boardPane.setMinSize(800, 700);
        boardPane.setMaxSize(800, 700);
        shakeGroup.setLayoutX(PANE_OFF_X);
        shakeGroup.setLayoutY(0);
        // 棋盘悬浮投影（落在林间草地上）
        shakeGroup.setEffect(new DropShadow(28, 10, 0, Color.rgb(0, 0, 0, 0.5)));
        shakeGroup.getChildren().addAll(boardCanvas, stonesCanvas, markCanvas, hoverCanvas, fxCanvas);
        boardPane.getChildren().add(shakeGroup);
        bindMouse();

        // 底栏 800~900
        HBox bottom = new HBox(14);
        bottom.setPrefHeight(100);
        bottom.setMinHeight(100);
        bottom.setMaxHeight(100);
        bottom.setPadding(new Insets(10, 20, 10, 20));
        bottom.setAlignment(Pos.CENTER_LEFT);
        bottom.setStyle("-fx-background-color: rgba(14,26,16,0.62);");

        Region bottomLine = new Region();
        bottomLine.setPrefHeight(1);
        bottomLine.setMaxHeight(1);
        bottomLine.setStyle("-fx-background-color: linear-gradient(to right, transparent,"
                + " rgba(232,196,122,0.5), transparent);");
        StackPane.setAlignment(bottomLine, Pos.TOP_CENTER);

        StackPane bottomWrap = new StackPane(bottom, bottomLine);
        bottomWrap.setPrefSize(800, 100);
        bottomWrap.setMaxSize(800, 100);

        undoBtn.setOnAction(e -> onUndo());
        restartBtn.setOnAction(e -> onRestart());
        modeLabel.setStyle("-fx-font-family: '" + Theme.FONT_BODY + "'; -fx-font-size: 16px;"
                + "-fx-text-fill: " + Theme.TEXT_SUB + ";");
        moveLabel.setStyle("-fx-font-family: '" + Theme.FONT_BODY + "'; -fx-font-size: 16px;"
                + "-fx-text-fill: " + Theme.TEXT_SUB + ";");
        bottom.getChildren().addAll(undoBtn, restartBtn, Theme.spacer(), modeLabel, Theme.spacer(), moveLabel);
        // 联机模式隐藏「重新开局」（中途重开可被滥用，终局再来一局已覆盖，spec3 §5-7）
        if (mode == GameSession.Mode.ONLINE) {
            restartBtn.setVisible(false);
            restartBtn.setManaged(false);
        }

        layout.getChildren().addAll(topWrap, boardPane, bottomWrap);
        root.getChildren().add(layout);
        // 全局粒子层置顶（光尘/火星/喷泉）
        root.getChildren().add(Particles.get().getCanvas());
    }

    private void bindMouse() {
        boardPane.setOnMouseMoved(e -> handleHover(e.getX(), e.getY()));
        boardPane.setOnMouseExited(e -> {
            clearHover();
            boardPane.setCursor(Cursor.DEFAULT);
        });
        boardPane.setOnMouseClicked(e -> handleClick(e.getX(), e.getY()));
    }

    // ---------- 坐标映射（spec2 §2.1） ----------

    private static int clampIdx(int v) {
        return Math.max(0, Math.min(Board.SIZE - 1, v));
    }

    private static int rowAt(double paneY) {
        return clampIdx((int) Math.round((paneY - GRID) / CELL));
    }

    private static int colAt(double paneX) {
        return clampIdx((int) Math.round((paneX - PANE_OFF_X - GRID) / CELL));
    }

    private static double cxOf(int col) {
        return GRID + col * CELL;
    }

    private static double cyOf(int row) {
        return GRID + row * CELL;
    }

    // ---------- 渲染 ----------

    private void drawBoard() {
        GraphicsContext g = boardCanvas.getGraphicsContext2D();
        g.clearRect(0, 0, 700, 700);
        Random gr = new Random(46); // 固定种子：每次启动纹理一致

        // 1. 底色对角渐变
        LinearGradient wood = new LinearGradient(0, 0, 700, 700, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web(Theme.WOOD_LIGHT)),
                new Stop(0.5, Color.web(Theme.WOOD_MID)),
                new Stop(1, Color.web(Theme.WOOD_DARK)));
        g.setFill(wood);
        g.fillRoundRect(0, 0, 700, 700, 18, 18);

        // 2. 程序木纹：26 条水平贝塞尔长曲线
        for (int i = 0; i < 26; i++) {
            double baseY = gr.nextDouble() * 700;
            double amp = 2 + gr.nextDouble() * 4;
            boolean dark = gr.nextBoolean();
            g.setStroke(dark
                    ? Color.web("#8a5f33", 0.05 + gr.nextDouble() * 0.07)
                    : Color.web("#e0b87a", 0.05 + gr.nextDouble() * 0.07));
            g.setLineWidth(1 + gr.nextDouble() * 1.5);
            g.beginPath();
            g.moveTo(-10, baseY);
            double midY1 = baseY + (gr.nextDouble() - 0.5) * amp * 2;
            double midY2 = baseY + (gr.nextDouble() - 0.5) * amp * 2;
            g.bezierCurveTo(200, midY1, 480, midY2, 710, baseY + (gr.nextDouble() - 0.5) * 6);
            g.stroke();
        }

        // 3. 细噪点：木材毛孔
        for (int i = 0; i < 1200; i++) {
            double x = gr.nextDouble() * 700;
            double y = gr.nextDouble() * 700;
            g.setFill(i % 2 == 0 ? Color.rgb(0, 0, 0, 0.03) : Color.rgb(255, 255, 255, 0.03));
            g.fillRect(x, y, 1, 1);
        }

        // 4. 网格：外圈粗线 + 内部细线
        double span = (Board.SIZE - 1) * CELL;
        g.setStroke(Color.web(Theme.GRID_BOLD));
        g.setLineWidth(2.5);
        g.strokeRect(GRID, GRID, span, span);
        g.setStroke(Color.web(Theme.GRID_LINE, 0.85));
        g.setLineWidth(1.1);
        for (int i = 1; i < Board.SIZE - 1; i++) {
            double p = GRID + i * CELL;
            g.strokeLine(p, GRID, p, GRID + span);
            g.strokeLine(GRID, p, GRID + span, p);
        }

        // 5. 星位 + 微高光
        for (int code : STARS) {
            int r = code / 100;
            int c = code % 100;
            double sx = cxOf(c);
            double sy = cyOf(r);
            g.setFill(Color.web(Theme.GRID_BOLD));
            g.fillOval(sx - 4, sy - 4, 8, 8);
            g.setFill(Color.rgb(255, 255, 255, 0.25));
            g.fillOval(sx, sy - 2, 1.4, 1.4);
        }

        // 6. 坐标标注：列 A~P（跳 I），行 15~1
        g.setFont(Font.font(Theme.FONT_BODY, 13));
        g.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
        g.setTextBaseline(javafx.geometry.VPos.CENTER);
        g.setFill(Color.rgb(74, 48, 16, 0.75));
        String letters = "ABCDEFGHJKLMNOPQ";
        for (int i = 0; i < Board.SIZE; i++) {
            double x = cxOf(i);
            double yTop = INSET / 2;
            double yBot = 700 - INSET / 2;
            g.fillText(String.valueOf(letters.charAt(i)), x, yTop);
            g.fillText(String.valueOf(letters.charAt(i)), x, yBot);
            double y = cyOf(i);
            g.fillText(String.valueOf(Board.SIZE - i), INSET / 2, y);
            g.fillText(String.valueOf(Board.SIZE - i), 700 - INSET / 2, y);
        }

        // 7. 外框倒角：深描边 + 内侧受光亮线
        g.setStroke(Color.web(Theme.FRAME_DARK));
        g.setLineWidth(3);
        g.strokeRoundRect(1.5, 1.5, 697, 697, 16, 16);
        g.setStroke(Color.rgb(255, 230, 180, 0.35));
        g.setLineWidth(1);
        g.strokeRoundRect(3.5, 3.5, 693, 693, 14, 14);
    }

    /** 重绘静态棋子层。excludeLast=true 时跳过最后一手（动画层接管）。 */
    private void renderStones(boolean excludeLast) {
        GraphicsContext g = stonesCanvas.getGraphicsContext2D();
        g.clearRect(0, 0, 700, 700);
        List<Move> history = session.board.getHistory();
        int limit = excludeLast ? history.size() - 1 : history.size();
        for (int i = 0; i < limit; i++) {
            Move m = history.get(i);
            paintStone(g, cxOf(m.col), cyOf(m.row), m.color, 1);
        }
    }

    /** 终局压暗重绘：五连子全亮，其余 dimAlpha。 */
    private void dimStones(Set<Long> winKeys, double dimAlpha) {
        GraphicsContext g = stonesCanvas.getGraphicsContext2D();
        g.clearRect(0, 0, 700, 700);
        for (Move m : session.board.getHistory()) {
            long key = (long) m.row * 1000 + m.col;
            paintStone(g, cxOf(m.col), cyOf(m.row), m.color, winKeys.contains(key) ? 1 : dimAlpha);
        }
    }

    /** 标准棋子材质（spec2 §3.3）：软阴影+主体渐变+双层高光+环境反光+描边。 */
    private void paintStone(GraphicsContext g, double cx, double cy, int color, double alpha) {
        g.save();
        g.setGlobalAlpha(alpha);
        double r = STONE_R;
        if (color == Board.BLACK) {
            // 软阴影
            g.setFill(stoneShadow(cx + 2.5, cy + 3.5, r * 1.05, 0.32));
            g.fillOval(cx - r * 1.05 + 2.5, cy - r * 1.05 + 3.5, r * 2.1, r * 2.1);
            // 主体：云子径向渐变（焦点左上）
            g.setFill(new javafx.scene.paint.RadialGradient(45, 0.35,
                    cx - r * 0.38, cy - r * 0.42, r * 1.5, false, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.web("#787882")),
                    new Stop(0.42, Color.web("#2e2e34")),
                    new Stop(0.75, Color.web("#101014")),
                    new Stop(1, Color.web("#000000"))));
            g.fillOval(cx - r, cy - r, r * 2, r * 2);
            // 顶部柔高光
            g.setFill(highlight(cx - r * 0.32, cy - r * 0.40, r * 0.62, r * 0.40, 0.5));
            g.fillOval(cx - r * 0.32 - r * 0.31, cy - r * 0.40 - r * 0.20, r * 0.62, r * 0.40);
            // 底部环境反光（木桌反光）
            g.setStroke(Color.web(Theme.WOOD_LIGHT, 0.10));
            g.setLineWidth(2);
            g.strokeArc(cx - r + 2, cy - r + 2, r * 2 - 4, r * 2 - 4, 200, 140, javafx.scene.shape.ArcType.OPEN);
            // 描边
            g.setStroke(Color.rgb(140, 140, 150, 0.5));
            g.setLineWidth(0.8);
            g.strokeOval(cx - r, cy - r, r * 2, r * 2);
        } else {
            g.setFill(stoneShadow(cx + 2.5, cy + 3.5, r * 1.05, 0.26));
            g.fillOval(cx - r * 1.05 + 2.5, cy - r * 1.05 + 3.5, r * 2.1, r * 2.1);
            g.setFill(new javafx.scene.paint.RadialGradient(45, 0.3,
                    cx - r * 0.38, cy - r * 0.42, r * 1.5, false, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.web("#fffdf6")),
                    new Stop(0.5, Color.web("#f3ecda")),
                    new Stop(0.82, Color.web("#d8cfb8")),
                    new Stop(1, Color.web("#bdb298"))));
            g.fillOval(cx - r, cy - r, r * 2, r * 2);
            g.setFill(highlight(cx - r * 0.30, cy - r * 0.38, r * 0.55, r * 0.36, 0.85));
            g.fillOval(cx - r * 0.30 - r * 0.275, cy - r * 0.38 - r * 0.18, r * 0.55, r * 0.36);
            g.setStroke(Color.web(Theme.GOLD, 0.15));
            g.setLineWidth(2);
            g.strokeArc(cx - r + 2, cy - r + 2, r * 2 - 4, r * 2 - 4, 200, 140, javafx.scene.shape.ArcType.OPEN);
            g.setStroke(Color.rgb(160, 150, 130, 0.6));
            g.setLineWidth(0.8);
            g.strokeOval(cx - r, cy - r, r * 2, r * 2);
        }
        g.restore();
    }

    private javafx.scene.paint.RadialGradient stoneShadow(double cx, double cy, double r, double a) {
        return new javafx.scene.paint.RadialGradient(0, 0, cx, cy, r, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(0, 0, 0, a)),
                new Stop(1, Color.rgb(0, 0, 0, 0)));
    }

    private javafx.scene.paint.RadialGradient highlight(double cx, double cy, double rx, double ry, double a) {
        return new javafx.scene.paint.RadialGradient(0, 0, cx, cy, Math.max(rx, ry), false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(255, 255, 255, a)),
                new Stop(1, Color.rgb(255, 255, 255, 0)));
    }

    /** 最后一手呼吸金环（由全局粒子 timer 每帧驱动）。 */
    private void drawMarker() {
        GraphicsContext mg = markCanvas.getGraphicsContext2D();
        mg.clearRect(0, 0, 700, 700);
        List<Move> history = session.board.getHistory();
        if (history.isEmpty() || stoneAnimator.isActive() || victoryActive) {
            return;
        }
        Move m = history.get(history.size() - 1);
        double a = 0.35 + 0.35 * Math.sin(System.currentTimeMillis() / 1000.0 * 2 * Math.PI / 1.6);
        double cx = cxOf(m.col);
        double cy = cyOf(m.row);
        double r = STONE_R + 3;
        mg.setStroke(Color.web(Theme.GOLD_BRIGHT, a));
        mg.setLineWidth(2);
        mg.strokeOval(cx - r, cy - r, r * 2, r * 2);
    }

    private void clearFxCanvas() {
        fxCanvas.getGraphicsContext2D().clearRect(0, 0, 700, 700);
    }

    // ---------- 悬停 / 点击 ----------

    /** 当前回合是否可由本地玩家落子（spec3 §5-1）。 */
    private boolean localTurn() {
        if (mode == GameSession.Mode.PVE) {
            return session.getCurrentColor() != session.aiColor();
        }
        if (mode == GameSession.Mode.ONLINE) {
            return session.getCurrentColor() == myColor;
        }
        return true;
    }

    private void handleHover(double x, double y) {
        int r = rowAt(y);
        int c = colAt(x);
        boolean legal = session.getState() == GameState.PLAYING
                && !thinking
                && !disconnected
                && session.board.isEmpty(r, c)
                && localTurn();
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
            drawHover(cxOf(c), cyOf(r), session.getCurrentColor());
            boardPane.setCursor(Cursor.HAND);
        } else {
            if (hoverRow != -1) {
                clearHover();
            }
            boardPane.setCursor(Cursor.DEFAULT);
        }
    }

    /** 悬停三件套（spec2 §4.2）：ghost 子 + 十字参考线 + 四角括号。 */
    private void drawHover(double cx, double cy, int color) {
        GraphicsContext hg = hoverCanvas.getGraphicsContext2D();
        hg.clearRect(0, 0, 700, 700);
        // 十字参考线（虚线至木框内缘）
        hg.save();
        hg.setLineDashes(6, 6);
        hg.setStroke(Color.web(Theme.GOLD, 0.14));
        hg.setLineWidth(1);
        hg.strokeLine(cx, GRID, cx, 700 - GRID);
        hg.strokeLine(GRID, cy, 700 - GRID, cy);
        hg.restore();
        // 真实材质 ghost 子
        paintStone(hg, cx, cy, color, 0.45);
        // 吸附点四角括号
        double d = STONE_R + 6;
        double len = 8;
        hg.setStroke(Color.web(Theme.GOLD_BRIGHT, 0.8));
        hg.setLineWidth(2);
        double[][] corners = {
                {-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
        for (double[] cn : corners) {
            double px = cx + cn[0] * d;
            double py = cy + cn[1] * d;
            hg.beginPath();
            hg.moveTo(px, py - cn[1] * len);
            hg.lineTo(px, py);
            hg.lineTo(px - cn[0] * len, py);
            hg.stroke();
        }
    }

    private void clearHover() {
        hoverRow = -1;
        hoverCol = -1;
        hoverCanvas.getGraphicsContext2D().clearRect(0, 0, 700, 700);
    }

    private void handleClick(double x, double y) {
        if (thinking || disconnected) {
            return;
        }
        if (session.getState() != GameState.PLAYING) {
            return;
        }
        if (!localTurn()) {
            return;
        }
        int r = rowAt(y);
        int c = colAt(x);
        if (!session.board.isEmpty(r, c)) {
            SoundManager.play(SoundType.INVALID, 0.8);
            return;
        }
        int color = session.getCurrentColor();
        MoveOutcome oc = session.place(r, c, color);
        if (oc == null) {
            return;
        }
        // 先本地后发送（spec3 §5-2）；发送失败已触发断开流程
        if (link != null && !link.send(Protocol.move(r, c, color))) {
            return;
        }
        afterMove(oc);
    }

    /** 落子后统一入口：物理动效 → 终局判定/AI 触发都在动画完成后串联。 */
    private void afterMove(MoveOutcome oc) {
        Move last = session.board.getLastMove();
        clearHover();
        updateStatusUI();
        final MoveOutcome foc = oc;
        final Move flast = last;
        stoneAnimator.play(cxOf(last.col), cyOf(last.row), last.color,
                new StoneAnimator.Host() {
                    @Override
                    public GraphicsContext fx() {
                        return fxCanvas.getGraphicsContext2D();
                    }

                    @Override
                    public void renderStonesExcludingLast() {
                        renderStones(true);
                    }

                    @Override
                    public void paintStoneAt(GraphicsContext g, double cx, double cy,
                                             int color, double alpha) {
                        GameView.this.paintStone(g, cx, cy, color, alpha);
                    }

                    @Override
                    public void onImpact(double cx, double cy, int color) {
                        SoundManager.play(color == Board.BLACK
                                ? SoundType.STONE_BLACK : SoundType.STONE_WHITE);
                        Particles.get().sparks(PANE_OFF_X + cx, OFF_Y + cy, 8 + rnd.nextInt(3));
                        boardSway.shakeDamped(1.5, 110);
                    }

                    @Override
                    public void onDone() {
                        renderStones(false);
                        if (foc.type == MoveOutcome.Type.WIN) {
                            endGame(foc.winLine, flast.color);
                        } else if (foc.type == MoveOutcome.Type.DRAW) {
                            endDraw();
                        } else {
                            maybeAiTurn();
                        }
                    }
                });
    }

    private void maybeAiTurn() {
        if (session.getState() == GameState.PLAYING && mode == GameSession.Mode.PVE
                && session.getCurrentColor() == session.aiColor()) {
            startThinking();
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
        if (thinking) {
            return;
        }
        if (session.getState() != GameState.PLAYING) {
            return;
        }
        if (session.aiColor() == 0 || session.getCurrentColor() != session.aiColor()) {
            return;
        }
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

    // ---------- AI 瞄准环（spec2 §4.3，替代旧呼吸圈） ----------

    private void previewAndPlace(int myGen, Point p) {
        clearFxCanvas();
        aimPoint = p;
        aimGen = myGen;
        aimColor = session.aiColor() == Board.BLACK
                ? Color.web(Theme.GOLD_BRIGHT) : Color.web("#7ee0ff");
        aimLocked = false;
        aimStart = -1;
        double cx = cxOf(p.col);
        double cy = cyOf(p.row);
        aimTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                aimFrame(now, cx, cy);
            }
        };
        aimTimer.start();
    }

    private void aimFrame(long now, double cx, double cy) {
        if (aimStart < 0) {
            aimStart = now;
        }
        long t = (now - aimStart) / 1_000_000;
        GraphicsContext g = fxCanvas.getGraphicsContext2D();
        g.clearRect(0, 0, 700, 700);

        final int SHRINK_MS = 440;
        final int FLASH_MS = 60;
        final int PAUSE_MS = 80;

        if (!aimLocked && t >= SHRINK_MS) {
            aimLocked = true;
            SoundManager.play(SoundType.GUESS_PICK, 0.6);
        }
        if (t >= SHRINK_MS + FLASH_MS + PAUSE_MS) {
            stopAim();
            clearFxCanvas();
            completeAiPlace();
            return;
        }

        if (t < SHRINK_MS) {
            // 收缩旋转虚线环
            double k = easeOutQuad(t / (double) SHRINK_MS);
            double r = 30 - (30 - (STONE_R + 4)) * k;
            g.save();
            g.setLineDashes(10, 7);
            g.setLineDashOffset(t * 0.08);
            g.setStroke(aimColor);
            g.setLineWidth(3);
            g.strokeOval(cx - r, cy - r, r * 2, r * 2);
            g.restore();
        } else if (t <= SHRINK_MS + FLASH_MS) {
            // 锁定闪白一帧
            double r = STONE_R + 4;
            g.setStroke(Color.WHITE);
            g.setLineWidth(5);
            g.strokeOval(cx - r, cy - r, r * 2, r * 2);
        } else {
            // 锁定保持
            double r = STONE_R + 4;
            g.setStroke(aimColor);
            g.setLineWidth(3);
            g.strokeOval(cx - r, cy - r, r * 2, r * 2);
        }
    }

    private void stopAim() {
        if (aimTimer != null) {
            aimTimer.stop();
            aimTimer = null;
        }
    }

    private void completeAiPlace() {
        int myGen = aimGen;
        Point p = aimPoint;
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
    }

    private static double easeOutQuad(double t) {
        return 1 - (1 - t) * (1 - t);
    }

    // ---------- 联机网络（spec3 §5/§7） ----------

    private void startNet() {
        link.start(myName(), new NetLink.Listener() {
            @Override
            public void onMessage(String line) {
                Platform.runLater(() -> handleNetMessage(line));
            }

            @Override
            public void onDisconnected(String reason) {
                Platform.runLater(() -> handleDisconnect(reason));
            }
        });
    }

    private String myName() {
        String n = settings.getNetName();
        if (n == null || n.isBlank()) {
            n = "棋客" + (10 + rnd.nextInt(90));
        }
        return n.length() > Protocol.MAX_NAME ? n.substring(0, Protocol.MAX_NAME) : n;
    }

    /** 游戏级消息路由（FX 线程）。HELLO/PING/PONG/BYE 已被 NetLink 过滤。 */
    private void handleNetMessage(String line) {
        if (disconnected) {
            return;
        }
        Protocol.Message m;
        try {
            m = Protocol.decode(line);
        } catch (Protocol.ProtocolException e) {
            protocolViolation();
            return;
        }
        // 猜先期：GUESS_* 交给对话框，其余越序即违规
        if (guessHandler != null
                && (m.type() == Protocol.Type.GUESS_COMMIT
                || m.type() == Protocol.Type.GUESS_CHOICE
                || m.type() == Protocol.Type.GUESS_REVEAL)) {
            guessHandler.accept(m);
            return;
        }
        switch (m.type()) {
            case MOVE -> onRemoteMove(m);
            case UNDO_REQ -> onUndoRequest();
            case UNDO_OK -> onUndoAnswer(true);
            case UNDO_DENY -> onUndoAnswer(false);
            case REMATCH_REQ -> onRematchRequest();
            case REMATCH_OK -> doRematch();
            default -> protocolViolation(); // 时序越序（spec3 §0.1 fail-fast）
        }
    }

    /** 对方落子：全部经 session 校验（防作弊），非法即断开。 */
    private void onRemoteMove(Protocol.Message m) {
        int r = Integer.parseInt(m.get("r"));
        int c = Integer.parseInt(m.get("c"));
        int color = Integer.parseInt(m.get("color"));
        if (session.getState() != GameState.PLAYING
                || myColor == 0
                || color != (3 - myColor)
                || color != session.getCurrentColor()
                || !session.board.isEmpty(r, c)) {
            protocolViolation();
            return;
        }
        MoveOutcome oc = session.place(r, c, color);
        if (oc == null) {
            protocolViolation();
            return;
        }
        afterMove(oc);
    }

    private void protocolViolation() {
        if (link != null && link.isActive()) {
            link.close("协议违规");
        }
        handleDisconnect("协议违规");
    }

    /** 断线统一收敛（spec3 §7）：停一切活动 → 弹窗 → 仅可返回菜单。 */
    private void handleDisconnect(String reason) {
        if (disconnected) {
            return;
        }
        disconnected = true;
        thinking = false;
        pendingUndo = false;
        rematchWaiting = false;
        guessHandler = null;
        if (undoTimeout != null) {
            undoTimeout.stop();
            undoTimeout = null;
        }
        cleanupEffects();
        connDot.setFill(Color.web("#e05a4a"));
        closeOverlay();
        VBox panel = Theme.panel(520);
        Label t = Theme.titleLabel("连接已断开", 34, Theme.GOLD_BRIGHT);
        Label msg = Theme.label("原因：" + reason, 18, Theme.TEXT_MAIN, false);
        Button menu = Ui.styledButton("返回菜单", 20);
        menu.setOnAction(e -> exitToMenu());
        panel.getChildren().addAll(t, Theme.divider(300), msg, menu, Ui.makerLabel());
        overlayPanel(panel);
        updateStatusUI();
    }

    /** 返回菜单统一出口：关链路 → 清理 → 切页。 */
    private void exitToMenu() {
        if (link != null && link.isActive()) {
            link.close("返回菜单");
        }
        closeOverlay();
        cleanup();
        onExit.run();
    }

    // ---------- 悔棋 / 重开 ----------

    private void onUndo() {
        if (thinking || disconnected) return;
        if (session.getState() != GameState.PLAYING) return;
        if (session.board.getHistory().isEmpty()) return;
        if (mode == GameSession.Mode.ONLINE) {
            // 悔棋协商（spec3 §6.1）：请求 → 对方同意才执行；10s 超时自动撤销
            if (pendingUndo) return;
            pendingUndo = true;
            link.send(Protocol.undoReq());
            undoTimeout = new PauseTransition(Duration.millis(10000));
            undoTimeout.setOnFinished(e -> {
                if (pendingUndo) {
                    pendingUndo = false;
                    undoTimeout = null;
                    flashHint("对方未响应");
                }
            });
            undoTimeout.play();
            updateStatusUI();
            return;
        }
        applyLocalUndo();
    }

    /** 本地直接悔棋（PVE/PVP）：撤 2 子 + 重绘 + AI 回合补偿。 */
    private void applyLocalUndo() {
        List<Move> removed = session.undo();
        if (removed == null || removed.isEmpty()) return;
        SoundManager.play(SoundType.UNDO);
        if (stoneAnimator.isActive()) {
            stoneAnimator.cancel();
        }
        clearFxCanvas();
        renderStones(false);
        updateStatusUI();
        // PvE：悔到 AI 回合（如 AI 执黑首步被悔）必须重新触发思考，否则卡死
        maybeAiTurn();
    }

    /** 收到对方悔棋请求：弹窗审批，不阻塞己方落子。 */
    private void onUndoRequest() {
        if (disconnected) return;
        if (pendingUndo || session.getState() != GameState.PLAYING || currentOverlay != null) {
            link.send(Protocol.undoDeny()); // 忙碌/已终局：语义拒绝
            return;
        }
        VBox panel = Theme.panel(520);
        Label t = Theme.titleLabel("悔棋请求", 30, Theme.GOLD_BRIGHT);
        Label msg = Theme.label("对方请求悔棋（将撤销最近 2 子）", 18, Theme.TEXT_MAIN, false);
        Button ok = Ui.styledButton("同意", 20);
        ok.setOnAction(e -> {
            closeOverlay();
            link.send(Protocol.undoOk());
        });
        Button no = Ui.styledButton("拒绝", 20);
        no.setOnAction(e -> {
            closeOverlay();
            link.send(Protocol.undoDeny());
        });
        HBox row = new HBox(20, ok, no);
        row.setAlignment(Pos.CENTER);
        panel.getChildren().addAll(t, Theme.divider(300), msg, row, Ui.makerLabel());
        overlayPanel(panel);
    }

    /** 收到悔棋答复：OK 双侧对称撤子；DENY 提示后恢复。 */
    private void onUndoAnswer(boolean accepted) {
        if (!pendingUndo || disconnected) return;
        pendingUndo = false;
        if (undoTimeout != null) {
            undoTimeout.stop();
            undoTimeout = null;
        }
        if (accepted) {
            applyLocalUndo();
        } else {
            flashHint("对方拒绝了悔棋");
        }
    }

    /** 顶栏临时提示 2 秒。 */
    private void flashHint(String text) {
        statusHint = text;
        if (hintTimer == null) {
            hintTimer = new PauseTransition(Duration.millis(2000));
            hintTimer.setOnFinished(e -> {
                statusHint = null;
                updateStatusUI();
            });
        }
        hintTimer.stop();
        hintTimer.playFromStart();
        updateStatusUI();
    }

    private void onRestart() {
        showConfirm("重新开局？", "当前对局进度将丢失。", () -> {
            session.restart();
            cleanupEffects();
            renderStones(false);
            updateStatusUI();
            startGuess();
        });
    }

    private void startGuess() {
        thinking = false;
        if (mode == GameSession.Mode.ONLINE && link != null) {
            // 远程猜先（spec3 §4）：房主=持子方，客人=猜子方
            RemoteGuessDialog dialog = new RemoteGuessDialog(session, root, link, link.isHost(),
                    myColor -> {
                        guessHandler = null;
                        this.myColor = myColor;
                        updateStatusUI();
                        maybeAiTurn();
                    },
                    () -> handleDisconnect("猜先中断"));
            guessHandler = dialog::onMessage;
            dialog.show();
        } else {
            GuessDialog dialog = new GuessDialog(session, root, () -> {
                updateStatusUI();
                maybeAiTurn();
            });
            dialog.show();
        }
    }

    // ---------- 终局 ----------

    private void endGame(List<Move> winLine, int winnerColor) {
        boolean humanWins;
        if (mode == GameSession.Mode.ONLINE) {
            humanWins = winnerColor == myColor; // 联机：以我方视角定演出（spec3 §5-8）
        } else {
            humanWins = mode == GameSession.Mode.PVP
                    || (session.aiColor() != 0 && winnerColor != session.aiColor());
        }
        victoryActive = true;
        victory.play(humanWins ? VictorySequence.Kind.WIN : VictorySequence.Kind.LOSE, winLine);
        updateStatusUI();
        String title = winnerColor == Board.BLACK ? "黑方获胜！" : "白方获胜！";
        long delay = humanWins ? 2200 : 1600;
        finishDelay = new PauseTransition(Duration.millis(delay));
        finishDelay.setOnFinished(e -> showFinish(title, "再来一局（交换黑白）", "返回菜单"));
        finishDelay.play();
    }

    private void endDraw() {
        victoryActive = true;
        victory.play(VictorySequence.Kind.DRAW, null);
        updateStatusUI();
        finishDelay = new PauseTransition(Duration.millis(1200));
        finishDelay.setOnFinished(e -> showFinish("平局", "再来一局（交换黑白）", "返回菜单"));
        finishDelay.play();
    }

    private void showFinish(String title, String againText, String menuText) {
        VBox panel = Theme.panel(520);
        Label t = Theme.titleLabel(title, 40, Theme.GOLD_BRIGHT);
        Button again = Ui.styledButton(againText, 20);
        again.setOnAction(e -> {
            if (mode == GameSession.Mode.ONLINE) {
                // 再来一局协商（spec3 §6.2）：请求后等待，双方同意同步重开
                if (rematchWaiting) {
                    return;
                }
                rematchWaiting = true;
                link.send(Protocol.rematchReq());
                again.setText("等待对方…");
                again.setDisable(true);
                return;
            }
            closeOverlay();
            session.nextRound();
            cleanupEffects();
            renderStones(false);
            updateStatusUI();
            maybeAiTurn();
        });
        Button menu = Ui.styledButton(menuText, 20);
        menu.setOnAction(e -> exitToMenu());
        panel.getChildren().addAll(t, Theme.divider(300), again, menu, Ui.makerLabel());
        overlayPanel(panel);
    }

    /** 收到对方再来一局请求：直接视为达成一致并回执。 */
    private void onRematchRequest() {
        if (disconnected) return;
        link.send(Protocol.rematchOk());
        doRematch();
    }

    /** 双侧对称执行重开；以 FINISHED 状态守卫重复消息。 */
    private void doRematch() {
        if (disconnected || session.getState() != GameState.FINISHED) {
            return;
        }
        rematchWaiting = false;
        closeOverlay();
        session.nextRound();
        myColor = 3 - myColor; // 交换黑白
        cleanupEffects();
        renderStones(false);
        updateStatusUI();
        maybeAiTurn();
    }

    private void showConfirm(String title, String message, Runnable onOk) {
        VBox panel = Theme.panel(520);
        Label t = Theme.titleLabel(title, 30, Theme.GOLD_BRIGHT);
        Label msg = Theme.label(message, 18, Theme.TEXT_MAIN, false);
        Button ok = Ui.styledButton("确认", 20);
        ok.setOnAction(e -> {
            closeOverlay();
            onOk.run();
        });
        Button cancel = Ui.styledButton("取消", 20);
        cancel.setOnAction(e -> closeOverlay());
        HBox row = new HBox(20);
        row.setAlignment(Pos.CENTER);
        row.getChildren().addAll(ok, cancel);
        panel.getChildren().addAll(t, Theme.divider(300), msg, row, Ui.makerLabel());
        overlayPanel(panel);
    }

    private void overlayPanel(VBox panel) {
        StackPane overlay = new StackPane();
        overlay.getChildren().addAll(Theme.radialMask(), panel);
        root.getChildren().add(overlay);
        currentOverlay = overlay;
        Theme.springIn(panel);
    }

    private void closeOverlay() {
        if (currentOverlay != null && root.getChildren().contains(currentOverlay)) {
            StackPane ov = currentOverlay;
            currentOverlay = null;
            Theme.fadeOutRemove(root, ov);
        }
    }

    // ---------- 状态 UI ----------

    private void updateStatusUI() {
        int cur = session.getCurrentColor();
        GameState st = session.getState();
        if (disconnected) {
            turnIcon.setFill(Color.web("#9aa5a0"));
            turnLabel.setText("连接已断开");
            setSpinner(false);
        } else if (statusHint != null) {
            turnIcon.setFill(Color.web("#c9b98f"));
            turnLabel.setText(statusHint);
            setSpinner(false);
        } else if (pendingUndo) {
            turnIcon.setFill(Color.web("#c9b98f"));
            turnLabel.setText("等待对方回应悔棋…");
            setSpinner(false);
        } else if (st == GameState.FINISHED) {
            turnIcon.setFill(Color.web("#9aa5a0"));
            turnLabel.setText("对局结束");
            setSpinner(false);
        } else if (thinking) {
            turnIcon.setFill(Color.web("#c9b98f"));
            turnLabel.setText("AI 落子推演中…");
            setSpinner(true);
        } else {
            turnIcon.setFill(cur == Board.BLACK ? Color.web("#1c1c1c") : Color.web("#f4f4f4"));
            if (mode == GameSession.Mode.ONLINE) {
                turnLabel.setText(cur == myColor ? "你的回合" : "等待对方落子…");
            } else {
                turnLabel.setText(cur == Board.BLACK ? "黑方回合" : "白方回合");
            }
            // 回合切换图标淡入
            FadeTransition ft = new FadeTransition(Duration.millis(200), turnIcon);
            ft.setFromValue(0.2);
            ft.setToValue(1);
            ft.play();
            setSpinner(false);
        }
        moveLabel.setText("第 " + session.board.getHistory().size() + " 手");
        modeLabel.setText(mode == GameSession.Mode.PVE
                ? "人机对战 · " + difficultyText()
                : mode == GameSession.Mode.ONLINE
                ? "联机对战 · vs " + peerDisplay()
                : "双人对战");
        boolean canUndo = st == GameState.PLAYING && !thinking && !pendingUndo
                && !session.board.getHistory().isEmpty();
        undoBtn.setDisable(!canUndo);
    }

    private String peerDisplay() {
        return link != null && !link.peerName().isEmpty() ? link.peerName() : "对方";
    }

    private String difficultyText() {
        switch (difficulty) {
            case EASY: return "简单";
            case HARD: return "困难";
            default: return "中等";
        }
    }

    private void cleanupEffects() {
        stopAim();
        if (finishDelay != null) {
            finishDelay.stop();
            finishDelay = null;
        }
        stoneAnimator.cancel();
        victory.stop();
        victoryActive = false;
        rootSway.stop();
        boardSway.stop();
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
        if (link != null && link.isActive()) {
            link.close("退出");
        }
    }

    /** 窗口关闭时的 best-effort 告别（spec3 §7）。 */
    public void shutdownForWindowClose() {
        if (link != null && link.isActive()) {
            link.close("退出");
        }
    }
}
