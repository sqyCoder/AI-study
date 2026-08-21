package org.example.gobang.fx;

import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import org.example.gobang.audio.MusicManager;
import org.example.gobang.audio.SettingsStore;
import org.example.gobang.audio.SoundManager;
import org.example.gobang.audio.SoundType;

/**
 * 设置面板（spec2 §3.5 精修）：玻璃拟态面板 + spring 弹入；
 * 音乐/音效独立开关（自绘胶囊开关）+ 独立音量滑条（木轨金钮）。
 * 修改即写入 SettingsStore。
 */
public class SettingsPanel {

    private final Pane parent;
    private final SettingsStore settings;
    private final StackPane overlay = new StackPane();
    private boolean shown = false;
    private boolean closing = false;

    public SettingsPanel(Pane parent, SettingsStore settings) {
        this.parent = parent;
        this.settings = settings;
    }

    public void show() {
        if (shown) {
            return;
        }
        shown = true;
        closing = false;
        build();
        parent.getChildren().add(overlay);
        Theme.springIn(overlay.getChildren().get(overlay.getChildren().size() - 1));
    }

    private void close() {
        if (!shown || closing) {
            return;
        }
        closing = true;
        shown = false;
        Theme.fadeOutRemove(parent, overlay);
    }

    private void build() {
        overlay.getChildren().clear(); // 防重复打开时叠加多个暗罩/面板
        overlay.getChildren().add(Theme.radialMask());

        VBox panel = Theme.panel(460);

        Label title = Theme.titleLabel("设置", 30, Theme.CREAM);
        panel.getChildren().addAll(title, Theme.divider(280));

        panel.getChildren().addAll(row("音乐音量", musicRow()), row("音效音量", sfxRow()),
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
        Label l = Theme.label(name, 18, Theme.TEXT_MAIN, false);
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

    /** 自绘胶囊开关：选中=翠绿，未选=深木，knob 平移动画。 */
    private HBox toggleRow(String text, boolean selected, java.util.function.Consumer<Boolean> onChange) {
        StackPane track = new StackPane();
        track.setPrefSize(46, 24);
        track.setMaxSize(46, 24);
        String onStyle = "-fx-background-color: linear-gradient(to bottom, "
                + Theme.SEL_GREEN_TOP + ", " + Theme.SEL_GREEN_BOT + ");"
                + "-fx-background-radius: 12; -fx-border-color: " + Theme.SEL_BORDER
                + "; -fx-border-radius: 12; -fx-border-width: 1;";
        String offStyle = "-fx-background-color: linear-gradient(to bottom, #3a2a1a, #241811);"
                + "-fx-background-radius: 12; -fx-border-color: rgba(232,196,122,0.5);"
                + "-fx-border-radius: 12; -fx-border-width: 1;";
        Circle knob = new Circle(9);
        knob.setFill(Color.web("#f0e3c8"));
        knob.setStroke(Color.rgb(0, 0, 0, 0.35));
        StackPane.setAlignment(knob, Pos.CENTER_LEFT);
        StackPane.setMargin(knob, new Insets(0, 0, 0, 4));
        track.getChildren().add(knob);
        final boolean[] state = {selected};
        track.setStyle(state[0] ? onStyle : offStyle);
        TranslateTransition tt = new TranslateTransition(Duration.millis(140), knob);
        tt.setToX(state[0] ? 22 : 0);
        tt.play();
        track.setOnMouseClicked(e -> {
            state[0] = !state[0];
            track.setStyle(state[0] ? onStyle : offStyle);
            tt.setToX(state[0] ? 22 : 0);
            tt.playFromStart();
            onChange.accept(state[0]);
            SoundManager.play(SoundType.CLICK, 0.7);
        });
        Label l = Theme.label(text, 18, Theme.TEXT_MAIN, false);
        HBox h = new HBox(14, track, l);
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }
}
