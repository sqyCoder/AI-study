package org.example.ui;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;

import org.example.game.Direction;
import org.example.game.GameEngine;
import org.example.game.MoveResult;
import org.example.game.ScoreStore;
import org.example.game.Tile;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * 主控制器（spec §4.3.3）：渲染、设置栏（尺寸/主题/语言/音效/撤销/统计）、
 * 无动画的纯逻辑驱动重绘。
 * <p>
 * M3 范围：开局渲染、设置开关即时生效、窗口拉伸只重排不重建；
 * 键盘输入与动画在 M4 / M5 接入；遮罩内容与计时在 M6 完善。
 */
public class GameController implements Initializable {

    /** Preferences 根节点（与 ScoreStore 共用）。 */
    private static final String PREFS_NODE = "2048game";

    @FXML
    private BorderPane root;
    @FXML
    private StackPane boardArea;
    @FXML
    private GridPane boardGrid;
    @FXML
    private Pane tileLayer;
    @FXML
    private StackPane overlay;

    @FXML
    private Label titleLabel;
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

    private GameEngine engine;
    private final ScoreStore scoreStore = new ScoreStore();
    private I18n i18n;
    private ThemeManager theme;
    private SoundPlayer sound;
    private Stage stage;

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

        // 静态文案绑定（语言切换自动刷新）
        i18n.bind(titleLabel, "app.title");
        i18n.bind(scoreCaptionLabel, "score");
        i18n.bind(bestCaptionLabel, "best");
        i18n.bind(stepsCaptionLabel, "steps");
        i18n.bind(timeCaptionLabel, "time");
        i18n.bind(sizeHintLabel, "boardSizeHint");
        i18n.bind(statsTitleLabel, "stats");

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
        overlay.setOnMouseClicked(e -> {
            if (e.getTarget() == overlay) {
                hideOverlay();
            }
        });
        upButton.setOnAction(e -> handleMove(Direction.UP));
        downButton.setOnAction(e -> handleMove(Direction.DOWN));
        leftButton.setOnAction(e -> handleMove(Direction.LEFT));
        rightButton.setOnAction(e -> handleMove(Direction.RIGHT));

        // 窗口拉伸：只重排不重建节点（spec §4.5）；首次尺寸就绪时自动重建
        boardArea.widthProperty().addListener((o, oldV, newV) -> relayout());
        boardArea.heightProperty().addListener((o, oldV, newV) -> relayout());

        // 初始文案与状态（attach 后再触发一次全量刷新）
        refreshStateTexts();
    }

    /**
     * 装配入口：由 App 在 Scene/Stage 就绪后调用。
     * 应用主题、按偏好语言刷新全部文案、设置窗口标题。
     */
    public void attach(Stage stage) {
        this.stage = stage;
        theme.apply(stage.getScene());
        i18n.setLang(i18n.getLang());
        stage.setTitle(i18n.t("app.title"));
        root.requestFocus();
    }

    // ==================== 渲染 ====================

    /**
     * 统一重建入口：清空底板与方块层，按当前引擎局面全量重绘。
     * 供"开局 / 切尺寸 / 撤销 / 每次移动（M5 起改为动画驱动）"复用。
     */
    private void rebuildBoard() {
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

        // 底板：N×N 空位色块（GridPane 自动定位，padding 与方块层公式一致）
        boardGrid.getChildren().clear();
        boardGrid.setPadding(new Insets(BoardLayout.GAP));
        boardGrid.setHgap(BoardLayout.GAP);
        boardGrid.setVgap(BoardLayout.GAP);
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                StackPane cellPane = new StackPane();
                cellPane.getStyleClass().add("cell");
                cellPane.setPrefSize(cell, cell);
                boardGrid.add(cellPane, c, r);
            }
        }

        // 方块层：非零块按引擎局面绝对定位
        tileLayer.getChildren().clear();
        Tile[][] grid = engine.getGrid();
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                if (grid[r][c].isEmpty()) {
                    continue;
                }
                StackPane tile = TileViewFactory.createTile(grid[r][c].value(), cell);
                tile.setUserData(new int[]{r, c});
                tile.setLayoutX(BoardLayout.cellX(c, cell));
                tile.setLayoutY(BoardLayout.cellY(r, cell));
                tileLayer.getChildren().add(tile);
            }
        }
        tileLayer.setPrefSize(board, board);

        updateLabels();
    }

    /** 窗口拉伸回调：尺寸变化只重排/缩放现有节点；节点缺失时全量重建。 */
    private void relayout() {
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
            rebuildBoard();
            return;
        }
        // 仅重排：底板格子缩放 + 方块移动/缩放/字号
        for (Node node : boardGrid.getChildren()) {
            ((Region) node).setPrefSize(cell, cell);
        }
        Tile[][] grid = engine.getGrid();
        for (Node node : tileLayer.getChildren()) {
            int[] rc = (int[]) node.getUserData();
            TileViewFactory.restyleTile((StackPane) node, grid[rc[0]][rc[1]].value(), cell);
            node.setLayoutX(BoardLayout.cellX(rc[1], cell));
            node.setLayoutY(BoardLayout.cellY(rc[0], cell));
        }
        tileLayer.setPrefSize(board, board);
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

    /** 移动（方向按钮与键盘共用入口，M4 起键盘接入）。 */
    private void handleMove(Direction dir) {
        MoveResult result = engine.move(dir);
        if (result.moved()) {
            rebuildBoard();
            sound.playClick();
        }
        root.requestFocus();
    }

    private void handleUndo() {
        if (engine.undo() == null) {
            return;
        }
        hideOverlay();
        rebuildBoard();
        sound.playClick();
    }

    /** 新开局 / 切换尺寸：重置引擎并重绘。 */
    private void startNewGame(int size) {
        engine.startNewGame(size);
        sizeBox.setValue(size);
        hideOverlay();
        rebuildBoard();
    }

    // ==================== 统计面板 ====================

    private void showStatsPanel() {
        updateStatsPanel();
        overlay.setVisible(true);
        overlay.toFront();
    }

    private void updateStatsPanel() {
        statsScoreLabel.setText(i18n.t("score") + ": " + engine.getScore());
        statsBestLabel.setText(i18n.t("best") + ": " + scoreStore.loadBestScore());
        statsStepsLabel.setText(i18n.t("steps") + ": " + engine.getSteps());
        statsTimeLabel.setText(i18n.t("time") + ": " + timeLabel.getText());
    }

    private void hideOverlay() {
        overlay.setVisible(false);
    }

    // ==================== 文案与状态刷新 ====================

    /** 语言切换 / 主题 / 音效开关后统一刷新（按钮文本、数值、窗口标题）。 */
    private void refreshStateTexts() {
        updateButtons();
        updateLabels();
        if (overlay.isVisible()) {
            updateStatsPanel();
        }
        if (stage != null) {
            stage.setTitle(i18n.t("app.title"));
        }
    }

    private void updateButtons() {
        themeButton.setText(i18n.t("theme." + theme.getCurrent()));
        langButton.setText(I18n.LANG_ZH.equals(i18n.getLang()) ? "中" : "EN");
        soundButton.setText(i18n.t(sound.isEnabled() ? "sound.on" : "sound.off"));
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
