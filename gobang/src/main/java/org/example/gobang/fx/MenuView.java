package org.example.gobang.fx;

import javafx.animation.TranslateTransition;
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
import javafx.util.Duration;

import org.example.gobang.audio.SettingsStore;
import org.example.gobang.logic.GameSession;

import java.util.function.BiConsumer;

/**
 * 主菜单（spec2 阶段A 精修）：
 * 标题书法字体 + 悬浮呼吸；模式/难度/开始三按钮金系；
 * 页脚标注「制作：林森lsjs」，右上角齿轮进设置。
 */
public class MenuView {

    private final StackPane root = new StackPane();
    private final GameSession.Mode[] mode = new GameSession.Mode[1];
    private final GameSession.Difficulty[] difficulty = new GameSession.Difficulty[1];
    private final SettingsPanel settingsPanel;
    private final HBox diffBox = new HBox(16);
    private final Runnable onOpenLobby;

    public MenuView(ForestBackground bg, SettingsStore settings,
                    BiConsumer<GameSession.Mode, GameSession.Difficulty> onStart,
                    Runnable onOpenLobby) {
        this.onOpenLobby = onOpenLobby;
        settingsPanel = new SettingsPanel(root, settings);
        mode[0] = GameSession.Mode.PVE;
        difficulty[0] = GameSession.Difficulty.MEDIUM;
        build(bg, onStart);
    }

    public StackPane getRoot() {
        return root;
    }

    private void build(ForestBackground bg, BiConsumer<GameSession.Mode, GameSession.Difficulty> onStart) {
        Theme.applyCss(root);
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

        Label title = Theme.titleLabel("五子棋", 84, Theme.CREAM);
        title.setEffect(new DropShadow(22, Color.rgb(0, 0, 0, 0.8)));
        // 标题悬浮呼吸
        TranslateTransition floatTt = new TranslateTransition(Duration.millis(2600), title);
        floatTt.setFromY(0);
        floatTt.setToY(-7);
        floatTt.setAutoReverse(true);
        floatTt.setCycleCount(TranslateTransition.INDEFINITE);
        floatTt.setInterpolator(javafx.animation.Interpolator.EASE_BOTH);
        floatTt.play();

        Label sub = Theme.label("林间对弈 · 落子有声", 20, "#4a3520", true);
        sub.setStyle(sub.getStyle()
                + "-fx-effect: dropshadow(gaussian, rgba(255,250,230,0.85), 8, 0, 0, 1);");

        HBox modeRow = new HBox(24);
        modeRow.setAlignment(Pos.CENTER);
        Button pve = Ui.styledButton("人机对战", 26);
        Button pvp = Ui.styledButton("双人对战", 26);
        Button online = Ui.styledButton("联机对战", 26);
        markSelected(pve, true);
        markSelected(pvp, false);
        modeRow.getChildren().addAll(pve, pvp, online);
        online.setOnAction(e -> onOpenLobby.run());
        pve.setOnAction(e -> {
            mode[0] = GameSession.Mode.PVE;
            markSelected(pve, true);
            markSelected(pvp, false);
            diffBox.setVisible(true);
            diffBox.setManaged(true);
        });
        pvp.setOnAction(e -> {
            mode[0] = GameSession.Mode.PVP;
            markSelected(pvp, true);
            markSelected(pve, false);
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

    /** 选中态：金色描边加亮 + 轻微放大，未选恢复。 */
    private void markSelected(Button b, boolean selected) {
        b.setScaleX(selected ? 1.04 : 1.0);
        b.setScaleY(selected ? 1.04 : 1.0);
        b.setOpacity(selected ? 1.0 : 0.82);
    }
}
