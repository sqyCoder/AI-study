package org.example.ui;

import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;

import org.example.game.Direction;
import org.example.game.GameEngine;
import org.example.game.HistoryEntry;
import org.example.game.MoveResult;
import org.example.game.ScoreStore;
import org.example.game.Tile;
import org.example.game.TileMove;
import org.example.game.TileSpawn;
import org.example.ui.effect.EffectManager;

import javafx.animation.Animation;
import javafx.animation.AnimationTimer;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * 主控制器（spec §4.3.3）：渲染、设置栏（尺寸/主题/语言/音效/撤销/统计）、
 * 计时、三态遮罩（失败/胜利/统计）、动画驱动的无动作重绘。
 * <p>
 * M3 范围：开局渲染、设置开关即时生效、窗口拉伸只重排不重建；
 * 键盘与动画在 M4 / M5 接入；遮罩内容、计时与榜单在 M6 完善。
 */
public class GameController implements Initializable {

    /** Preferences 根节点（与 ScoreStore 共用）。 */
    private static final String PREFS_NODE = "2048game";

    /** 滑动动画时长（ms，spec §4.3.4 + spec2 §4.4 微调）。 */
    private static final long MOVE_ANIM_MS = 140;
    /** 合并弹出动画时长（ms）。 */
    private static final long MERGE_ANIM_MS = 120;
    /** 生成块动画时长（ms）。 */
    private static final long SPAWN_ANIM_MS = 180;

    @FXML
    private StackPane root;
    @FXML
    private StackPane bgLayer;
    @FXML
    private StackPane boardArea;
    @FXML
    private StackPane glassCard;
    @FXML
    private Pane cellLayer;
    @FXML
    private Pane tileLayer;
    @FXML
    private Pane confettiLayer;
    @FXML
    private StackPane overlay;

    @FXML
    private Label scoreCaptionLabel;
    @FXML
    private Label scoreLabel;
    @FXML
    private Label bestCaptionLabel;
    @FXML
    private Label bestLabel;
    @FXML
    private Label stepsCaptionLabel;
    @FXML
    private Label stepsLabel;
    @FXML
    private Label timeCaptionLabel;
    @FXML
    private Label timeLabel;
    @FXML
    private Label sizeHintLabel;

    @FXML
    private Label statsTitleLabel;
    @FXML
    private Label statsScoreLabel;
    @FXML
    private Label statsBestLabel;
    @FXML
    private Label statsStepsLabel;
    @FXML
    private Label statsTimeLabel;
    @FXML
    private Label historyCaptionLabel;
    @FXML
    private VBox statsHistoryBox;

    @FXML
    private VBox gameOverBox;
    @FXML
    private Label gameOverTitleLabel;
    @FXML
    private Label gameOverScoreLabel;
    @FXML
    private Label gameOverNewBestLabel;
    @FXML
    private Label gameOverSubtitleLabel;
    @FXML
    private Button tryAgainButton;
    @FXML
    private VBox winBox;
    @FXML
    private Label winTitleLabel;
    @FXML
    private Label winSubtitleLabel;
    @FXML
    private Label winScoreLabel;
    @FXML
    private Button keepGoingButton;
    @FXML
    private Button winNewGameButton;
    @FXML
    private Button winBackMenuButton;
    @FXML
    private VBox statsBox;

    @FXML
    private ComboBox<Integer> sizeBox;
    @FXML
    private Button newGameButton;
    @FXML
    private Button themeButton;
    @FXML
    private Button langButton;
    @FXML
    private Button soundButton;
    @FXML
    private Button undoButton;
    @FXML
    private Button statsButton;
    @FXML
    private Button closeStatsButton;
    @FXML
    private Button upButton;
    @FXML
    private Button downButton;
    @FXML
    private Button leftButton;
    @FXML
    private Button rightButton;
    @FXML
    private Button backButton;
    @FXML
    private Label authorLabel;

    private GameEngine engine;
    private final ScoreStore scoreStore = new ScoreStore();
    private I18n i18n;
    private ThemeManager theme;
    private SoundPlayer sound;
    private GlowBackground glow;
    private Stage stage;
    /** 主菜单传入的初始棋盘尺寸（null 表示不覆盖，维持默认 4×4）。 */
    private Integer initialSize;
    /** 返回主菜单回调（由 App 注入；null 时隐藏返回按钮）。 */
    private Runnable onBackToMenu;
    /** 动画进行中：屏蔽新输入，保证逻辑与画面一致（spec NFR-3，防快速连按错乱）。 */
    private boolean animationLock;

    /** 已累计计时（暂停时保留；撤销回空棋盘 / 新开局清零）。 */
    private long elapsedMillis;
    /** 当前计时段的起始时刻（nanoTime）。 */
    private long segmentStartNanos;
    /** 计时是否运行中。 */
    private boolean timerRunning;

    /** 计时驱动：每秒刷新一次 timeLabel（spec §4.6：首步有效移动启动）。 */
    private final AnimationTimer timer = new AnimationTimer() {
        private long lastRefresh = -1;

        @Override
        public void handle(long nowNanos) {
            if (lastRefresh < 0 || nowNanos - lastRefresh >= 1_000_000_000L) {
                lastRefresh = nowNanos;
                updateTimeLabel();
            }
        }
    };

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        engine = new GameEngine(GameEngine.MIN_SIZE + 1); // 默认 4×4
        i18n = new I18n(readPref("lang", I18n.LANG_ZH));
        theme = new ThemeManager(readPref("theme", ThemeManager.LIGHT));
        sound = new SoundPlayer(readPrefBool("sound", true));

        // 尺寸下拉：3×3 ~ 8×8
        sizeBox.setItems(FXCollections.observableArrayList(3, 4, 5, 6, 7, 8));
        sizeBox.setCellFactory(cb -> sizeCell());
        sizeBox.setButtonCell(sizeCell());
        sizeBox.setValue(engine.getSize());

        // 动态光晕背景（spec2 §4.2）：StackPane 自动拉伸填满 bgLayer，随主题联动
        glow = new GlowBackground(theme.getCurrent());
        bgLayer.getChildren().add(glow);

        // 静态文案绑定（语言切换自动刷新）
        i18n.bind(scoreCaptionLabel, "score");
        i18n.bind(bestCaptionLabel, "best");
        i18n.bind(stepsCaptionLabel, "steps");
        i18n.bind(timeCaptionLabel, "time");
        i18n.bind(sizeHintLabel, "boardSizeHint");
        i18n.bind(statsTitleLabel, "stats");
        i18n.bind(historyCaptionLabel, "history");
        i18n.bind(gameOverTitleLabel, "gameOver.title");
        i18n.bind(gameOverSubtitleLabel, "gameOver.subtitle");
        i18n.bind(winTitleLabel, "win.title");
        i18n.bind(winSubtitleLabel, "win.subtitle");
        i18n.bind(tryAgainButton, "gameOver.tryAgain");
        i18n.bind(keepGoingButton, "win.keepGoing");
        i18n.bind(winNewGameButton, "win.newGame");
        i18n.bind(winBackMenuButton, "win.backMenu");
        i18n.bind(authorLabel, "author");
        i18n.bind(newGameButton, "newGame");
        i18n.bind(undoButton, "undo");
        i18n.bind(statsButton, "stats");
        i18n.bind(closeStatsButton, "close");

        // 语言切换时需按当前主题/音效状态重刷按钮文本与数值
        i18n.addRefreshCallback(this::refreshStateTexts);

        // ---- 事件绑定 ----
        newGameButton.setOnAction(e -> {
            startNewGame(engine.getSize());
            sound.playClick();
            root.requestFocus();
        });
        sizeBox.valueProperty().addListener((o, old, nv) -> {
            if (nv != null) {
                startNewGame(nv);
                sound.playClick();
            }
        });
        themeButton.setOnAction(e -> {
            theme.toggle(stage.getScene());
            glow.applyTheme(theme.getCurrent()); // 光晕随主题联动（spec2 §4.2）
            refreshStateTexts();
            sound.playClick();
            root.requestFocus();
        });
        langButton.setOnAction(e -> {
            i18n.setLang(I18n.LANG_EN.equals(i18n.getLang()) ? I18n.LANG_ZH : I18n.LANG_EN);
            writePref("lang", i18n.getLang());
            sound.playClick();
            root.requestFocus();
        });
        soundButton.setOnAction(e -> {
            sound.toggle();
            writePrefBool("sound", sound.isEnabled());
            refreshStateTexts();
            sound.playClick();
            root.requestFocus();
        });
        undoButton.setOnAction(e -> {
            handleUndo();
            root.requestFocus();
        });
        statsButton.setOnAction(e -> {
            showStatsPanel();
            sound.playClick();
            root.requestFocus();
        });
        closeStatsButton.setOnAction(e -> hideOverlay());
        // 遮罩空白点击：仅统计面板可点灭；失败/胜利遮罩必须走按钮（防止误关后方向键失效）
        overlay.setOnMouseClicked(e -> {
            if (e.getTarget() == overlay && statsBox.isVisible()) {
                hideOverlay();
            }
        });
        tryAgainButton.setOnAction(e -> {
            startNewGame(engine.getSize());
            sound.playClick();
            root.requestFocus();
        });
        keepGoingButton.setOnAction(e -> {
            engine.continueAfterWin();
            hideOverlay();
            startTimer(); // 继续游戏：恢复计时
            sound.playClick();
            root.requestFocus();
        });
        winNewGameButton.setOnAction(e -> {
            startNewGame(engine.getSize());
            sound.playClick();
            root.requestFocus();
        });
        winBackMenuButton.setOnAction(e -> backToMenu());
        backButton.setOnAction(e -> backToMenu());
        upButton.setOnAction(e -> {
            sound.playClick();
            handleMove(Direction.UP);
        });
        downButton.setOnAction(e -> {
            sound.playClick();
            handleMove(Direction.DOWN);
        });
        leftButton.setOnAction(e -> {
            sound.playClick();
            handleMove(Direction.LEFT);
        });
        rightButton.setOnAction(e -> {
            sound.playClick();
            handleMove(Direction.RIGHT);
        });

        // 窗口拉伸：只重排不重建节点（spec §4.5）；首次尺寸就绪时自动重建
        boardArea.widthProperty().addListener((o, oldV, newV) -> relayout());
        boardArea.heightProperty().addListener((o, oldV, newV) -> relayout());

        // 初始文案与状态（attach 后再触发一次全量刷新）
        refreshStateTexts();
    }

    /**
     * 装配入口：由 App 在 Scene/Stage 就绪后调用。
     * 应用主题、按偏好语言刷新全部文案、设置窗口标题、注册全局按键。
     */
    public void attach(Stage stage) {
        this.stage = stage;
        FontKit.load(); // 注册内置 MiSans 字体（失败静默回退，spec2 §6）
        theme.apply(stage.getScene());
        if (initialSize != null) {
            startNewGame(initialSize); // 主菜单选定尺寸开局（spec3 §二）
        }
        i18n.setLang(i18n.getLang());
        stage.setTitle(i18n.t("app.title"));
        // 键盘监听放 Scene 过滤器（spec §4.3.3/§5 焦点对策）：
        // 无论焦点在哪个控件，方向键/WASD/R/Z 均直达，不会被按钮/ComboBox 吞掉。
        stage.getScene().addEventFilter(KeyEvent.KEY_PRESSED, this::onKeyPressed);
        backButton.setVisible(onBackToMenu != null);
        backButton.setManaged(onBackToMenu != null);
        root.requestFocus();
    }

    /**
     * 注入主菜单选定的棋盘尺寸（须在 attach 之前调用，spec3 §二）。
     * 首局面在 attach 中按该尺寸开局，其余行为与默认一致。
     */
    public void setInitialSize(int size) {
        this.initialSize = size;
    }

    /** 注入"返回主菜单"回调（null 则隐藏返回按钮，spec3 §二）。 */
    public void setOnBackToMenu(Runnable callback) {
        this.onBackToMenu = callback;
    }

    /** 返回主菜单：暂停计时、停掉后台动画、触发回调（由 App 切换场景）。 */
    private void backToMenu() {
        stopTimer();
        if (onBackToMenu != null) {
            sound.playClick();
            onBackToMenu.run();
        }
    }

    // ==================== 键盘 ====================

    /**
     * 全局按键处理（spec §4.3.3）：
     * 方向键 / WASD → handleMove；Z / Ctrl+Z → 撤销；R → 新游戏；其余不拦截。
     * ComboBox 弹出中时方向键放行给列表选择。
     */
    private void onKeyPressed(KeyEvent e) {
        if (sizeBox.isShowing()) {
            return;
        }
        KeyCode code = e.getCode();
        boolean handled = true;
        if (code == KeyCode.UP || code == KeyCode.W) {
            handleMove(Direction.UP);
        } else if (code == KeyCode.DOWN || code == KeyCode.S) {
            handleMove(Direction.DOWN);
        } else if (code == KeyCode.LEFT || code == KeyCode.A) {
            handleMove(Direction.LEFT);
        } else if (code == KeyCode.RIGHT || code == KeyCode.D) {
            handleMove(Direction.RIGHT);
        } else if (code == KeyCode.Z) {
            handleUndo();
        } else if (code == KeyCode.R) {
            handleNewGame();
        } else {
            handled = false;
        }
        if (handled) {
            e.consume();
        }
    }

    /** R 键 / 新游戏按钮：以当前尺寸重开。 */
    private void handleNewGame() {
        startNewGame(engine.getSize());
        sound.playClick();
        root.requestFocus();
    }

    // ==================== 渲染 ====================

    /**
     * 无动画全量重绘：清空底板与方块层，按当前引擎局面重建。
     * 供"开局 / 切尺寸 / 撤销 / 动画兜底"复用（spec §4.3.3：撤销直接重建不播动画）。
     */
    private void renderBoard() {
        double w = boardArea.getWidth();
        double h = boardArea.getHeight();
        if (w <= 0 || h <= 0) {
            return; // 尺寸未就绪，首次布局回调会再次触发
        }
        int n = engine.getSize();
        double cell = BoardLayout.cellSize(w, h, n);
        if (cell <= 0) {
            return;
        }
        double board = BoardLayout.boardSide(n, cell);

        // 底格层：N×N 空位色块，与方块层同一坐标公式绝对定位（居中偏移同源，杜绝错位）
        cellLayer.getChildren().clear();
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                StackPane cellPane = new StackPane();
                cellPane.getStyleClass().add("cell");
                cellPane.setPrefSize(cell, cell);
                cellPane.setUserData(new int[]{r, c});
                cellPane.setLayoutX(boardOffsetX(cell) + BoardLayout.cellX(c, cell));
                cellPane.setLayoutY(boardOffsetY(cell) + BoardLayout.cellY(r, cell));
                cellLayer.getChildren().add(cellPane);
            }
        }
        cellLayer.setPrefSize(board, board);

        // 方块层：非零块按引擎局面绝对定位
        for (Node node : tileLayer.getChildren()) {
            if (node instanceof StackPane sp) {
                EffectManager.stopGlow(sp); // 全量重建前清掉呼吸光晕（飘字 Label 除外）
            }
        }
        tileLayer.getChildren().clear();
        Tile[][] grid = engine.getGrid();
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                if (grid[r][c].isEmpty()) {
                    continue;
                }
                StackPane tile = TileViewFactory.createTile(grid[r][c].value(), cell);
                tile.setUserData(new int[]{r, c});
                tile.setLayoutX(boardOffsetX(cell) + BoardLayout.cellX(c, cell));
                tile.setLayoutY(boardOffsetY(cell) + BoardLayout.cellY(r, cell));
                tileLayer.getChildren().add(tile);
            }
        }
        tileLayer.setPrefSize(board, board);

        updateMaxTileStyle();
        updateLabels();
    }

    /** 窗口拉伸回调：尺寸变化只重排/缩放现有节点；节点缺失时全量重建。 */
    private void relayout() {
        if (animationLock) {
            return; // 动画期间忽略拉伸：动画收尾的 syncBoardWithEngine 会统一按新尺寸校正
        }
        double w = boardArea.getWidth();
        double h = boardArea.getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        int n = engine.getSize();
        double cell = BoardLayout.cellSize(w, h, n);
        if (cell <= 0) {
            return;
        }
        double board = BoardLayout.boardSide(n, cell);

        if (tileLayer.getChildren().size() != countNonZero()) {
            renderBoard();
            return;
        }
        // 仅重排：底格缩放移动 + 方块移动/缩放/字号（动画节点 translate 归零后同步）
        for (Node node : cellLayer.getChildren()) {
            int[] rc = (int[]) node.getUserData();
            node.setLayoutX(boardOffsetX(cell) + BoardLayout.cellX(rc[1], cell));
            node.setLayoutY(boardOffsetY(cell) + BoardLayout.cellY(rc[0], cell));
            ((Region) node).setPrefSize(cell, cell);
        }
        Tile[][] grid = engine.getGrid();
        for (Node node : tileLayer.getChildren()) {
            int[] rc = (int[]) node.getUserData();
            if (rc == null) {
                continue;
            }
            TileViewFactory.restyleTile((StackPane) node, grid[rc[0]][rc[1]].value(), cell);
            node.setLayoutX(boardOffsetX(cell) + BoardLayout.cellX(rc[1], cell));
            node.setLayoutY(boardOffsetY(cell) + BoardLayout.cellY(rc[0], cell));
            node.setTranslateX(0);
            node.setTranslateY(0);
        }
        tileLayer.setPrefSize(board, board);
        updateMaxTileStyle();
    }

    private int countNonZero() {
        int count = 0;
        for (Tile[] row : engine.getGrid()) {
            for (Tile t : row) {
                if (!t.isEmpty()) {
                    count++;
                }
            }
        }
        return count;
    }

    // ==================== 交互 ====================

    /**
     * 移动（方向按钮与键盘共用入口，spec §4.3.3）。
     * 动画期间屏蔽新输入（animationLock，NFR-3）；有效移动：首步启动计时、
     * 播合并音、走动画流程；胜负判定在动画结束后处理（afterMove）。
     */
    private void handleMove(Direction dir) {
        if (animationLock) {
            return;
        }
        MoveResult result = engine.move(dir);
        if (result.moved()) {
            if (!timerRunning) {
                startTimer(); // 第一步 / 撤销复活后的下一步 / 继续游戏后
            }
            if (result.moves().isEmpty()) {
                renderBoard(); // 防御兜底：无移动记录但引擎已变化
            } else {
                animateMove(result);
            }
            if (result.scoreDelta() > 0) {
                sound.playMerge();
            }
        }
        root.requestFocus();
    }

    /**
     * 撤销（spec §4.3.3）：动画中忽略；成功后隐藏遮罩直接重建不播动画。
     * 计时规则（§4.6）：撤销前已 game over/won（复活）→ 暂停计时；
     * 撤销后棋盘为空（回到首步前）→ 计时复位清零。
     */
    private void handleUndo() {
        if (animationLock) {
            return;
        }
        var undo = engine.undo();
        if (undo == null) {
            return;
        }
        if (undo.revived()) {
            stopTimer();
        }
        hideOverlay();
        renderBoard();
        if (countNonZero() == 0) {
            resetTimer();
        }
        sound.playClick();
    }

    /** 新开局 / 切换尺寸：重置引擎、隐藏遮罩、计时复位清零并重绘。 */
    private void startNewGame(int size) {
        if (animationLock) {
            // 动画期间忽略重开（spec §5：宁可丢，不可乱），下拉框回弹避免显示与引擎不一致
            sizeBox.setValue(engine.getSize());
            return;
        }
        engine.startNewGame(size);
        sizeBox.setValue(size);
        resetTimer();
        hideOverlay();
        renderBoard();
    }

    // ==================== 动画编排（spec §4.3.4） ====================

    /**
     * 动画驱动重绘：依据 MoveResult.moves 批量播放滑移（140ms），
     * 结束后重建合并块（缩放弹出）、生成块淡入，最后与引擎做一致性校正。
     */
    private void animateMove(MoveResult result) {
        animationLock = true;
        double cell = currentCellSize();

        Map<String, StackPane> byPos = indexTiles();
        List<Animation> transitions = new ArrayList<>();
        Map<String, Integer> merges = new HashMap<>();

        for (TileMove m : result.moves()) {
            StackPane node = byPos.get(key(m.fromRow(), m.fromCol()));
            if (node == null) {
                continue; // 节点缺失：由结束后的校正兜底
            }
            double fromX = boardOffsetX(cell) + BoardLayout.cellX(m.fromCol(), cell);
            double fromY = boardOffsetY(cell) + BoardLayout.cellY(m.fromRow(), cell);
            double toX = boardOffsetX(cell) + BoardLayout.cellX(m.toCol(), cell);
            double toY = boardOffsetY(cell) + BoardLayout.cellY(m.toRow(), cell);
            // 先落到目标格（最终态），再以 translate 反向补差形成滑移
            node.setLayoutX(toX);
            node.setLayoutY(toY);
            node.setTranslateX(fromX - toX);
            node.setTranslateY(fromY - toY);
            node.setUserData(new int[]{m.toRow(), m.toCol()});
            TranslateTransition t = new TranslateTransition(Duration.millis(MOVE_ANIM_MS), node);
            t.setInterpolator(Interpolator.EASE_BOTH);
            t.setToX(0);
            t.setToY(0);
            transitions.add(t);
            if (m.isMerge()) {
                merges.put(key(m.toRow(), m.toCol()), m.value());
            }
        }

        ParallelTransition moveAll = new ParallelTransition(transitions.toArray(new Animation[0]));
        moveAll.setOnFinished(e -> finishMoveAnimation(result, merges, cell));
        moveAll.play();
    }

    /** 滑移完成后：合并块重建弹出 + 生成块淡入，最后一致性校正并解锁。 */
    private void finishMoveAnimation(MoveResult result, Map<String, Integer> merges, double cell) {
        List<Animation> fx = new ArrayList<>();

        // 合并块：移除原节点，在目标位新建合并值节点，缩放弹出（0.5→1.1→1.0）
        for (Map.Entry<String, Integer> e : merges.entrySet()) {
            int[] rc = parseKey(e.getKey());
            removeTilesAt(rc[0], rc[1]);
            StackPane merged = TileViewFactory.createTile(e.getValue(), cell);
            merged.setUserData(rc);
            merged.setLayoutX(boardOffsetX(cell) + BoardLayout.cellX(rc[1], cell));
            merged.setLayoutY(boardOffsetY(cell) + BoardLayout.cellY(rc[0], cell));
            tileLayer.getChildren().add(merged);
            // 合并爆点 + 飘字（spec2 §4.4）：爆点粒子与 "+N" 飘字并行于弹出动画
            double cx = merged.getLayoutX() + cell / 2;
            double cy = merged.getLayoutY() + cell / 2;
            EffectManager.mergeBurst(tileLayer, cx, cy, EffectManager.tileColor(merged));
            EffectManager.scorePopup(tileLayer, cx - 14, cy - cell * 0.45, "+" + e.getValue());
            Timeline timeline = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(merged.scaleXProperty(), 0.5),
                            new KeyValue(merged.scaleYProperty(), 0.5)),
                    new KeyFrame(Duration.millis(MERGE_ANIM_MS * 0.6),
                            new KeyValue(merged.scaleXProperty(), 1.1),
                            new KeyValue(merged.scaleYProperty(), 1.1)),
                    new KeyFrame(Duration.millis(MERGE_ANIM_MS),
                            new KeyValue(merged.scaleXProperty(), 1.0),
                            new KeyValue(merged.scaleYProperty(), 1.0)));
            fx.add(timeline);
        }

        // 生成块：目标位新建节点，淡入 + 缩放（160ms）
        if (result.spawned() != null) {
            TileSpawn s = result.spawned();
            StackPane spawn = TileViewFactory.createTile(s.value(), cell);
            spawn.setUserData(new int[]{s.row(), s.col()});
            spawn.setLayoutX(boardOffsetX(cell) + BoardLayout.cellX(s.col(), cell));
            spawn.setLayoutY(boardOffsetY(cell) + BoardLayout.cellY(s.row(), cell));
            spawn.setOpacity(0);
            spawn.setScaleX(0.5);
            spawn.setScaleY(0.5);
            tileLayer.getChildren().add(spawn);
            EffectManager.spawnGlow(spawn); // 生成块微光脉冲（spec2 §4.4）
            FadeTransition fade = new FadeTransition(Duration.millis(SPAWN_ANIM_MS), spawn);
            fade.setFromValue(0);
            fade.setToValue(1);
            ScaleTransition scale = new ScaleTransition(Duration.millis(SPAWN_ANIM_MS), spawn);
            scale.setFromX(0.5);
            scale.setFromY(0.5);
            scale.setToX(1.0);
            scale.setToY(1.0);
            scale.setInterpolator(Interpolator.EASE_OUT);
            fx.add(new ParallelTransition(fade, scale));
        }

        ParallelTransition all = new ParallelTransition(fx.toArray(new Animation[0]));
        all.setOnFinished(e -> afterMove(result));
        all.play();
    }

    /**
     * 动画收尾（spec §4.3.3 步骤 5-8）：与引擎一致性校正、解锁，并按结果弹遮罩：
     * winReached → 胜利遮罩（含"继续游戏"）；gameOver → 榜单结算 + 失败遮罩；计时暂停。
     */
    private void afterMove(MoveResult result) {
        syncBoardWithEngine();
        animationLock = false;
        if (result.winReached()) {
            stopTimer();
            sound.playVictory(); // 盛大胜利音效（spec3 §五）
            winScoreLabel.setText(i18n.t("score") + ": " + engine.getScore());
            showPanel(winBox);
            // spec3 §五：大风量三波次全屏撒花（替代原 26 根单波）
            EffectManager.confettiCelebration(confettiLayer, overlay.getWidth(), overlay.getHeight());
            pulseScore(winScoreLabel); // 胜利分数放大脉冲
        } else if (result.gameOver()) {
            stopTimer();
            int bestBefore = scoreStore.loadBestScore();
            scoreStore.reportGameOver(engine.getScore(), engine.getSize());
            sound.playGameOver();
            updateLabels(); // best 可能已被榜单刷新
            gameOverScoreLabel.setText(i18n.t("score") + ": " + engine.getScore());
            // 超越最佳提示（spec3 §五：游戏结束同样加强反馈）
            boolean newBest = engine.getScore() > 0 && engine.getScore() >= bestBefore;
            gameOverNewBestLabel.setVisible(newBest);
            gameOverNewBestLabel.setManaged(newBest);
            if (newBest) {
                gameOverNewBestLabel.setText(i18n.t("gameOver.newBest"));
            }
            showPanel(gameOverBox);
        }
    }

    /**
     * 分数放大脉冲（spec3 §五）：1.0→1.18→1.0 循环 3 次，突出"新纪录/胜利"的
     * 视觉反馈；一次性 Timeline，播完自动结束，无节点/动画泄漏。
     */
    private void pulseScore(Label label) {
        Timeline t = new Timeline();
        for (int i = 0; i < 3; i++) {
            t.getKeyFrames().addAll(
                    new KeyFrame(Duration.millis(i * 200),
                            new KeyValue(label.scaleXProperty(), 1.0),
                            new KeyValue(label.scaleYProperty(), 1.0)),
                    new KeyFrame(Duration.millis(i * 200 + 100),
                            new KeyValue(label.scaleXProperty(), 1.18),
                            new KeyValue(label.scaleYProperty(), 1.18)));
        }
        t.getKeyFrames().add(
                new KeyFrame(Duration.millis(600),
                        new KeyValue(label.scaleXProperty(), 1.0),
                        new KeyValue(label.scaleYProperty(), 1.0)));
        t.play();
    }

    /** 一致性校正：tileLayer 与引擎网格逐格对齐（防御动画残留/缺失，spec M5 验收）。 */
    private void syncBoardWithEngine() {
        int n = engine.getSize();
        double cell = currentCellSize();
        Tile[][] grid = engine.getGrid();

        // 移除与引擎不符的节点（引擎为空或坐标失效）
        tileLayer.getChildren().removeIf(node -> {
            int[] rc = (int[]) node.getUserData();
            boolean stale = rc == null || rc[0] < 0 || rc[0] >= n || rc[1] < 0 || rc[1] >= n
                    || grid[rc[0]][rc[1]].isEmpty();
            if (stale && node instanceof StackPane sp) {
                EffectManager.stopGlow(sp); // 停止呼吸光晕，防节点泄漏（飘字 Label 无 userData，先行排除）
            }
            return stale;
        });

        // 校正位置 / 值 / 补齐缺失
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                if (grid[r][c].isEmpty()) {
                    continue;
                }
                StackPane node = findTileAt(r, c);
                if (node == null) {
                    node = TileViewFactory.createTile(grid[r][c].value(), cell);
                    node.setUserData(new int[]{r, c});
                    tileLayer.getChildren().add(node);
                } else {
                    TileViewFactory.restyleTile(node, grid[r][c].value(), cell);
                }
                node.setLayoutX(boardOffsetX(cell) + BoardLayout.cellX(c, cell));
                node.setLayoutY(boardOffsetY(cell) + BoardLayout.cellY(r, cell));
                node.setTranslateX(0);
                node.setTranslateY(0);
                node.setOpacity(1);
                node.setScaleX(1);
                node.setScaleY(1);
            }
        }
        tileLayer.setPrefSize(BoardLayout.boardSide(n, cell), BoardLayout.boardSide(n, cell));
        updateMaxTileStyle();
        updateLabels();
    }

    // ==================== 渲染工具 ====================

    private double currentCellSize() {
        return BoardLayout.cellSize(boardArea.getWidth(), boardArea.getHeight(), engine.getSize());
    }

    /**
     * 棋盘内容在方块层内的水平居中偏移。
     * 以 boardArea 为基准：监听器回调触发时其宽高即为最终值（确定无竞态），
     * 而 tileLayer 的实时尺寸在布局脉冲中会读到中间值，导致一次性错位。
     */
    private double boardOffsetX(double cell) {
        return BoardLayout.boardX(boardArea.getWidth(), BoardLayout.boardSide(engine.getSize(), cell));
    }

    /** 垂直居中偏移，同 {@link #boardOffsetX}。 */
    private double boardOffsetY(double cell) {
        return BoardLayout.boardY(boardArea.getHeight(), BoardLayout.boardSide(engine.getSize(), cell));
    }

    /** 按节点 userData 坐标索引现有方块。 */
    private Map<String, StackPane> indexTiles() {
        Map<String, StackPane> map = new HashMap<>();
        for (Node node : tileLayer.getChildren()) {
            int[] rc = (int[]) node.getUserData();
            if (rc != null) {
                map.put(key(rc[0], rc[1]), (StackPane) node);
            }
        }
        return map;
    }

    private StackPane findTileAt(int r, int c) {
        for (Node node : tileLayer.getChildren()) {
            int[] rc = (int[]) node.getUserData();
            if (rc != null && rc[0] == r && rc[1] == c) {
                return (StackPane) node;
            }
        }
        return null;
    }

    /** 移除指定格上的所有方块节点（合并后重建前调用）。 */
    private void removeTilesAt(int r, int c) {
        tileLayer.getChildren().removeIf(node -> {
            int[] rc = (int[]) node.getUserData();
            boolean hit = rc != null && rc[0] == r && rc[1] == c;
            if (hit) {
                EffectManager.stopGlow((StackPane) node);
            }
            return hit;
        });
    }

    private static String key(int r, int c) {
        return r + "," + c;
    }

    /**
     * maxTile 样式更新（spec §4.3.4 步骤 5）：当前棋盘最高块（≥128）加发光高亮，
     * 其余去除；重绘/拉伸/动画收尾后调用，避免 restyle 清掉样式类后遗留错乱。
     */
    private void updateMaxTileStyle() {
        Tile[][] grid = engine.getGrid();
        int max = 0;
        for (Tile[] row : grid) {
            for (Tile t : row) {
                max = Math.max(max, t.value());
            }
        }
        for (Node node : tileLayer.getChildren()) {
            int[] rc = (int[]) node.getUserData();
            boolean isMax = false;
            if (rc != null && max >= 128 && rc[0] < grid.length && rc[1] < grid[rc[0]].length) {
                isMax = grid[rc[0]][rc[1]].value() == max;
            }
            if (isMax) {
                node.getStyleClass().add("tile-max");
                EffectManager.pulseGlow((StackPane) node); // 呼吸光晕（幂等）
            } else {
                node.getStyleClass().remove("tile-max");
                EffectManager.stopGlow((StackPane) node);
            }
        }
    }

    private static int[] parseKey(String k) {
        String[] parts = k.split(",");
        return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
    }

    // ==================== 统计面板 ====================

    private void showStatsPanel() {
        updateStatsPanel();
        showPanel(statsBox);
    }

    /** 统计面板：本局四项 + 历史 Top5（"分数 × 尺寸 · 日期"，spec §4.1.8）。 */
    private void updateStatsPanel() {
        statsScoreLabel.setText(i18n.t("score") + ": " + engine.getScore());
        statsBestLabel.setText(i18n.t("best") + ": " + scoreStore.loadBestScore());
        statsStepsLabel.setText(i18n.t("steps") + ": " + engine.getSteps());
        statsTimeLabel.setText(i18n.t("time") + ": " + timeLabel.getText());
        statsHistoryBox.getChildren().clear();
        for (HistoryEntry h : scoreStore.loadHistory()) {
            Label line = new Label(h.score() + " × " + h.size() + " · " + formatDate(h.date()));
            line.getStyleClass().add("history-entry");
            statsHistoryBox.getChildren().add(line);
        }
    }

    /**
     * 三态遮罩轮换：显示指定面板，其余隐藏（spec §4.3.3 遮罩）；彩带层恒不隐藏。
     * V5 打磨：遮罩淡入 + 面板缩放弹出的渐入过渡。
     */
    private void showPanel(Node box) {
        for (Node n : overlay.getChildren()) {
            if (n == confettiLayer) {
                continue;
            }
            n.setVisible(n == box);
        }
        overlay.setVisible(true);
        overlay.toFront();
        overlay.setOpacity(0);
        box.setScaleX(0.94);
        box.setScaleY(0.94);
        FadeTransition fade = new FadeTransition(Duration.millis(160), overlay);
        fade.setToValue(1);
        ScaleTransition pop = new ScaleTransition(Duration.millis(220), box);
        pop.setToX(1);
        pop.setToY(1);
        pop.setInterpolator(Interpolator.EASE_OUT);
        fade.play();
        pop.play();
    }

    private void hideOverlay() {
        overlay.setVisible(false);
    }

    // ==================== 计时（spec §4.6） ====================

    /** 启动/恢复计时（幂等）。 */
    private void startTimer() {
        if (timerRunning) {
            return;
        }
        timerRunning = true;
        segmentStartNanos = System.nanoTime();
        timer.start();
        updateTimeLabel();
    }

    /** 暂停计时并结算累计（win / game over 遮罩弹出时）。 */
    private void stopTimer() {
        if (!timerRunning) {
            return;
        }
        elapsedMillis += (System.nanoTime() - segmentStartNanos) / 1_000_000;
        timerRunning = false;
        timer.stop();
        updateTimeLabel();
    }

    /** 复位计时为零（新开局 / 切尺寸 / 撤销回空棋盘）。 */
    private void resetTimer() {
        timerRunning = false;
        elapsedMillis = 0;
        timer.stop();
        timeLabel.setText("00:00");
    }

    private void updateTimeLabel() {
        long ms = elapsedMillis;
        if (timerRunning) {
            ms += (System.nanoTime() - segmentStartNanos) / 1_000_000;
        }
        timeLabel.setText(formatTime(ms));
    }

    private static String formatTime(long millis) {
        long totalSec = millis / 1000;
        return String.format("%02d:%02d", totalSec / 60, totalSec % 60);
    }

    private static String formatDate(long epochMillis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(epochMillis));
    }

    // ==================== 文案与状态刷新 ====================

    /** 语言切换 / 主题 / 音效开关后统一刷新（按钮文本、数值、窗口标题）。 */
    private void refreshStateTexts() {
        updateButtons();
        updateLabels();
        if (overlay.isVisible()) {
            if (winBox.isVisible()) {
                winScoreLabel.setText(i18n.t("score") + ": " + engine.getScore());
            } else if (gameOverBox.isVisible()) {
                gameOverScoreLabel.setText(i18n.t("score") + ": " + engine.getScore());
            }
            updateStatsPanel();
        }
        if (stage != null) {
            stage.setTitle(i18n.t("app.title"));
        }
    }

    /**
     * 图标按钮状态刷新（spec2 §4.3）：主题/音效用 SVG 图标 + Tooltip，
     * 语言按钮保留文字（中/EN）；Tooltip 文案走 i18n，切换语言即时刷新。
     */
    private void updateButtons() {
        boolean dark = ThemeManager.DARK.equals(theme.getCurrent());
        themeButton.setGraphic(Icons.theme(dark));
        themeButton.setTooltip(new Tooltip(i18n.t(dark ? "theme.dark" : "theme.light")));
        soundButton.setGraphic(sound.isEnabled() ? Icons.soundOn() : Icons.soundOff());
        soundButton.setTooltip(new Tooltip(i18n.t(sound.isEnabled() ? "sound.on" : "sound.off")));
        statsButton.setGraphic(Icons.stats());
        statsButton.setTooltip(new Tooltip(i18n.t("stats")));
        undoButton.setGraphic(Icons.undo());
        undoButton.setTooltip(new Tooltip(i18n.t("undo")));
        backButton.setGraphic(Icons.back());
        backButton.setTooltip(new Tooltip(i18n.t("menu.back")));
        langButton.setText(I18n.LANG_ZH.equals(i18n.getLang()) ? "中" : "EN");
    }

    private void updateLabels() {
        scoreLabel.setText(String.valueOf(engine.getScore()));
        bestLabel.setText(String.valueOf(scoreStore.loadBestScore()));
        stepsLabel.setText(String.valueOf(engine.getSteps()));
        undoButton.setDisable(!engine.canUndo());
    }

    // ==================== 工具 ====================

    private ListCell<Integer> sizeCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Integer n, boolean empty) {
                super.updateItem(n, empty);
                setText(empty || n == null ? null : i18n.format("sizeFormat", n));
            }
        };
    }

    private static Preferences prefs() {
        return Preferences.userRoot().node(PREFS_NODE);
    }

    private static String readPref(String key, String def) {
        try {
            return prefs().get(key, def);
        } catch (Exception e) {
            return def;
        }
    }

    private static boolean readPrefBool(String key, boolean def) {
        try {
            return prefs().getBoolean(key, def);
        } catch (Exception e) {
            return def;
        }
    }

    private static void writePref(String key, String value) {
        try {
            prefs().put(key, value);
            prefs().flush();
        } catch (Exception e) {
            // 受限环境静默降级，不影响游戏
        }
    }

    private static void writePrefBool(String key, boolean value) {
        try {
            prefs().putBoolean(key, value);
            prefs().flush();
        } catch (Exception e) {
            // 受限环境静默降级，不影响游戏
        }
    }
}
