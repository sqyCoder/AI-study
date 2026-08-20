package org.example.gobang.fx;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import org.example.gobang.audio.SettingsStore;
import org.example.gobang.logic.GameSession;

import java.util.function.BiConsumer;

/**
 * 主菜单：标题「五子棋」→「人机对战」「双人对战」→ 难度三选一（默认中等）→「开始对局」。
 * 页脚标注「制作：林森lsjs」，右上角齿轮进设置。
 */
public class MenuView {

    private final StackPane root = new StackPane();
    private final GameSession.Mode[] mode = new GameSession.Mode[1];
    private final GameSession.Difficulty[] difficulty = new GameSession.Difficulty[1];
    private final SettingsPanel settingsPanel;
    private final HBox diffBox = new HBox(16);

    public MenuView(ForestBackground bg, SettingsStore settings,
                    BiConsumer<GameSession.Mode, GameSession.Difficulty> onStart) {
        settingsPanel = new SettingsPanel(root, settings);
        mode[0] = GameSession.Mode.PVE;
        difficulty[0] = GameSession.Difficulty.MEDIUM;
        build(bg, onStart);
    }

    public StackPane getRoot() {
        return root;
    }

    private static final String BTN_STYLE =
            "-fx-background-color: linear-gradient(to bottom, #f2dcae, #d9b378);"
            + "-fx-background-radius: 16; -fx-border-color: #8a6a3a; -fx-border-radius: 16;"
            + "-fx-border-width: 1.5; -fx-font-family: '" + Ui.FONT + "'; -fx-font-weight: bold;"
            + "-fx-text-fill: #4a3010; -fx-cursor: hand; -fx-focus-traversable: false;"
            + "-fx-padding: 10 24 10 24; -fx-font-size: 26px;";
    private static final String BTN_HOVER =
            "-fx-background-color: linear-gradient(to bottom, #ffe8bc, #e6c384);"
            + "-fx-background-radius: 16; -fx-border-color: #8a6a3a; -fx-border-radius: 16;"
            + "-fx-border-width: 1.5; -fx-font-family: '" + Ui.FONT + "'; -fx-font-weight: bold;"
            + "-fx-text-fill: #4a3010; -fx-cursor: hand; -fx-focus-traversable: false;"
            + "-fx-padding: 10 24 10 24; -fx-font-size: 26px;";
    private static final String BTN_PRESSED =
            "-fx-background-color: linear-gradient(to bottom, #c99d5e, #b0813f);"
            + "-fx-background-radius: 16; -fx-border-color: #6e4f28; -fx-border-radius: 16;"
            + "-fx-border-width: 1.5; -fx-font-family: '" + Ui.FONT + "'; -fx-font-weight: bold;"
            + "-fx-text-fill: #3c2608; -fx-cursor: hand; -fx-focus-traversable: false;"
            + "-fx-padding: 10 24 10 24; -fx-font-size: 26px;";
    private static final String BTN_SELECTED = "-fx-background-color: #8fbf4f; -fx-text-fill: white;"
            + "-fx-background-radius: 16; -fx-border-color: #4c6b26; -fx-border-radius: 16;"
            + "-fx-border-width: 1.5; -fx-font-family: '" + Ui.FONT + "'; -fx-font-weight: bold;"
            + "-fx-cursor: hand; -fx-focus-traversable: false;"
            + "-fx-padding: 10 24 10 24; -fx-font-size: 26px;";

    private void applyModeStyle(Button b, boolean selected) {
        String style = selected ? BTN_SELECTED : BTN_STYLE;
        String hover = selected ? BTN_SELECTED : BTN_HOVER;
        String pressed = selected ? BTN_SELECTED : BTN_PRESSED;
        b.setStyle(style);
        b.setOnMouseEntered(e -> b.setStyle(hover));
        b.setOnMouseExited(e -> b.setStyle(style));
        b.setOnMousePressed(e -> b.setStyle(pressed));
        b.setOnMouseReleased(e -> b.setStyle(b.isHover() ? hover : style));
    }

    private void build(ForestBackground bg, BiConsumer<GameSession.Mode, GameSession.Difficulty> onStart) {
        root.getChildren().add(bg.getNode());

        // 顶部：齿轮进设置
        HBox top = new HBox();
        top.setPadding(new Insets(12, 16, 0, 0));
        top.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        Button gear = Ui.smallButton("⚙ 设置");
        gear.setOnAction(e -> settingsPanel.show());
        top.getChildren().add(gear);
        StackPane.setAlignment(top, Pos.TOP_RIGHT);

        // 中间：标题 + 按钮
        VBox center = new VBox(22);
        center.setAlignment(Pos.CENTER);
        center.setTranslateY(-30);

        Label title = new Label("五子棋");
        title.setStyle("-fx-font-family: '" + Ui.FONT + "'; -fx-font-size: 84px; -fx-font-weight: bold;"
                + "-fx-text-fill: #f7ecd0;");
        title.setEffect(new DropShadow(18, Color.rgb(30, 20, 5, 0.75)));

        Label sub = new Label("林间对弈 · 落子有声");
        sub.setStyle("-fx-font-family: '" + Ui.FONT + "'; -fx-font-size: 20px; -fx-text-fill: #e8d9b0;"
                + "-fx-effect: dropshadow(gaussian, rgba(30,20,5,0.7), 6, 0, 1, 1);");

        HBox modeRow = new HBox(24);
        modeRow.setAlignment(Pos.CENTER);
        Button pve = Ui.styledButton("人机对战", 26);
        Button pvp = Ui.styledButton("双人对战", 26);
        applyModeStyle(pve, true);
        applyModeStyle(pvp, false);
        modeRow.getChildren().addAll(pve, pvp);
        pve.setOnAction(e -> {
            mode[0] = GameSession.Mode.PVE;
            applyModeStyle(pve, true);
            applyModeStyle(pvp, false);
            diffBox.setVisible(true);
            diffBox.setManaged(true);
        });
        pvp.setOnAction(e -> {
            mode[0] = GameSession.Mode.PVP;
            applyModeStyle(pvp, true);
            applyModeStyle(pve, false);
            diffBox.setVisible(false);
            diffBox.setManaged(false);
        });

        // 难度选择
        diffBox.setAlignment(Pos.CENTER);
        ToggleGroup group = new ToggleGroup();
        ToggleButton easy = Ui.toggleButton("简单", group);
        ToggleButton medium = Ui.toggleButton("中等", group);
        ToggleButton hard = Ui.toggleButton("困难", group);
        medium.setSelected(true);
        easy.setOnAction(e -> difficulty[0] = GameSession.Difficulty.EASY);
        medium.setOnAction(e -> difficulty[0] = GameSession.Difficulty.MEDIUM);
        hard.setOnAction(e -> difficulty[0] = GameSession.Difficulty.HARD);
        diffBox.getChildren().addAll(easy, medium, hard);

        Button start = Ui.styledButton("开始对局", 26);
        start.setOnAction(e -> onStart.accept(mode[0], difficulty[0]));

        center.getChildren().addAll(title, sub, modeRow, diffBox, start);
        root.getChildren().addAll(center, top);

        // 底部标注
        VBox bottom = new VBox();
        bottom.setPadding(new Insets(0, 0, 10, 0));
        bottom.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        bottom.getChildren().add(Ui.makerLabel());
        StackPane.setAlignment(bottom, Pos.BOTTOM_CENTER);
        root.getChildren().add(bottom);
    }
}