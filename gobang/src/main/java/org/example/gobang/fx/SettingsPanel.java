package org.example.gobang.fx;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import org.example.gobang.audio.MusicManager;
import org.example.gobang.audio.SettingsStore;
import org.example.gobang.audio.SoundManager;
import org.example.gobang.audio.SoundType;

/**
 * 设置面板（右上角齿轮进入）：音乐/音效独立开关 + 独立音量滑条，
 * 修改即写入 SettingsStore，重启生效。
 */
public class SettingsPanel {

    private final Pane parent;
    private final SettingsStore settings;
    private final StackPane overlay = new StackPane();
    private boolean shown = false;

    public SettingsPanel(Pane parent, SettingsStore settings) {
        this.parent = parent;
        this.settings = settings;
    }

    public void show() {
        if (shown) return;
        shown = true;
        build();
        parent.getChildren().add(overlay);
    }

    private void close() {
        if (!shown) return;
        shown = false;
        parent.getChildren().remove(overlay);
    }

    private void build() {
        overlay.getChildren().clear(); // 防重复打开时叠加多个暗罩/面板
        Rectangle dark = new Rectangle(800, 900, Color.rgb(0, 0, 0, 0.55));
        dark.setOnMouseClicked(e -> e.consume());
        overlay.getChildren().add(dark);

        VBox panel = new VBox(16);
        panel.setAlignment(Pos.CENTER);
        panel.setPadding(new Insets(28, 36, 24, 36));
        panel.setMaxWidth(460);
        panel.setStyle("-fx-background-color: rgba(56, 35, 15, 0.94); -fx-background-radius: 22;"
                + "-fx-border-color: #8a6a3a; -fx-border-radius: 22; -fx-border-width: 2;");

        Label title = new Label("设置");
        title.setStyle("-fx-font-family: '" + Ui.FONT + "'; -fx-font-size: 30px; -fx-font-weight: bold;"
                + "-fx-text-fill: #f5e9cf;");

        panel.getChildren().addAll(title, row("音乐音量", musicRow()), row("音效音量", sfxRow()),
                toggleRow("开启音乐", !settings.isMusicMuted(), v -> {
                    settings.setMusicMuted(!v);
                    MusicManager.applySettings(true);
                    settings.persist();
                }),
                toggleRow("开启音效", !settings.isSfxMuted(), v -> {
                    settings.setSfxMuted(!v);
                    settings.persist();
                    if (v) {
                        SoundManager.play(SoundType.STONE_BLACK);
                    }
                }));

        Button closeBtn = Ui.smallButton("关闭");
        closeBtn.setOnAction(e -> close());
        panel.getChildren().add(closeBtn);
        panel.getChildren().add(Ui.makerLabel());

        overlay.getChildren().add(panel);
    }

    private HBox row(String name, Node control) {
        HBox h = new HBox(12);
        h.setAlignment(Pos.CENTER_LEFT);
        Label l = new Label(name);
        l.setStyle("-fx-font-family: '" + Ui.FONT + "'; -fx-font-size: 18px; -fx-text-fill: #f0e0bd;");
        l.setPrefWidth(90);
        h.getChildren().addAll(l, control);
        return h;
    }

    private Slider musicRow() {
        Slider s = new Slider(0, 100, settings.getMusicVolume() * 100);
        s.setPrefWidth(260);
        s.valueProperty().addListener((ob, o, n) -> {
            settings.setMusicVolume(n.doubleValue() / 100);
            MusicManager.applyInstant();
        });
        s.setOnMouseReleased(e -> settings.persist());
        return s;
    }

    private Slider sfxRow() {
        Slider s = new Slider(0, 100, settings.getSfxVolume() * 100);
        s.setPrefWidth(260);
        s.valueProperty().addListener((ob, o, n) -> {
            settings.setSfxVolume(n.doubleValue() / 100);
        });
        s.setOnMouseReleased(e -> {
            settings.persist();
            SoundManager.play(SoundType.STONE_WHITE);
        });
        return s;
    }

    private HBox toggleRow(String text, boolean selected, java.util.function.Consumer<Boolean> onChange) {
        CheckBox cb = new CheckBox(text);
        cb.setSelected(selected);
        cb.setStyle("-fx-font-family: '" + Ui.FONT + "'; -fx-font-size: 18px; -fx-text-fill: #f0e0bd;"
                + "-fx-focus-traversable: false;");
        cb.setOnAction(e -> onChange.accept(cb.isSelected()));
        HBox h = new HBox();
        h.setAlignment(Pos.CENTER_LEFT);
        h.getChildren().add(cb);
        return h;
    }
}