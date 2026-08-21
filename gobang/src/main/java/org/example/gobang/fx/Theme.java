package org.example.gobang.fx;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.util.Duration;

/**
 * 设计系统单一来源（spec2 §1）：
 * 色彩 token / 字体 token / 按钮四态工厂 / 玻璃拟态面板工厂 / 弹窗动画工具。
 * 所有视图禁止再写内联按钮样式，一律经由本类。
 */
public final class Theme {

    // ---------- 色彩 token（CSS 字符串用 HEX_，Canvas 用 Color 常量） ----------
    public static final String BG_DEEP = "#17100a";
    public static final String BG_MID = "#241812";
    public static final String WOOD_LIGHT = "#d4a768";
    public static final String WOOD_MID = "#b98a4e";
    public static final String WOOD_DARK = "#a1723c";
    public static final String FRAME_DARK = "#6e4a26";
    public static final String GRID_LINE = "#52351a";
    public static final String GRID_BOLD = "#4a3018";
    public static final String GOLD = "#e8c47a";
    public static final String GOLD_BRIGHT = "#ffd54a";
    public static final String CREAM = "#f5e9cf";
    public static final String TEXT_MAIN = "#efe2c2";
    public static final String TEXT_SUB = "#c9b58f";
    public static final String TEXT_DIM = "#968266";
    public static final String FX_BLUE = "#7ea8ff";

    public static final Color C_GOLD_BRIGHT = Color.web(GOLD_BRIGHT);
    public static final Color C_GOLD = Color.web(GOLD);
    public static final Color C_FX_BLUE = Color.web(FX_BLUE);

    // ---------- 字体 token ----------
    public static final String FONT_BODY = "Microsoft YaHei";
    private static String fontTitle = FONT_BODY;

    /** 自定义 spring 插值（easeOutBack，轻微过冲）。 */
    public static final Interpolator EASE_OUT_BACK = new Interpolator() {
        @Override
        protected double curve(double t) {
            return easeOutBack(t);
        }
    };

    private Theme() {
    }

    /** easeOutBack 曲线值（供 Canvas 帧动画直接取值）。 */
    public static double easeOutBack(double t) {
        double c1 = 1.70158;
        double c3 = c1 + 1;
        return 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2);
    }

    /** 在 Main.start 最先调用；加载失败回退雅黑，游戏不受影响。 */
    public static void loadFonts() {
        try {
            Font f = Font.loadFont(Theme.class.getResource("/fonts/LXGWWenKaiLite-Medium.ttf").toExternalForm(), 12);
            if (f != null && f.getFamily() != null && !f.getFamily().isBlank()) {
                fontTitle = f.getFamily();
            }
        } catch (Throwable ignored) {
            fontTitle = FONT_BODY;
        }
    }

    public static String fontTitle() {
        return fontTitle;
    }

    // ---------- 按钮四态工厂 ----------

    private static String btnBase(boolean gold, double size) {
        String bg = gold
                ? "linear-gradient(to bottom, #fce8ad, #ecc57a)"
                : "linear-gradient(to bottom, #3a2a1a, #241811)";
        String text = gold ? "#3c2608" : "#f0e3c8";
        return "-fx-background-color: " + bg + ";"
                + "-fx-background-radius: 14;"
                + "-fx-border-color: rgba(232,196,122,0.75);"
                + "-fx-border-radius: 14;"
                + "-fx-border-width: 1.2;"
                + "-fx-font-family: '" + FONT_BODY + "';"
                + (gold ? "" : "-fx-font-weight: bold;")
                + "-fx-text-fill: " + text + ";"
                + "-fx-cursor: hand;"
                + "-fx-focus-traversable: false;"
                + "-fx-padding: 10 24 10 24;"
                + "-fx-font-size: " + size + "px;";
    }

    private static String btnHover(boolean gold, double size) {
        String bg = gold
                ? "linear-gradient(to bottom, #fff0c0, #f2cd85)"
                : "linear-gradient(to bottom, #46331f, #2c1e14)";
        return btnBase(gold, size).replaceFirst(
                "-fx-background-color: [^;]+;", "-fx-background-color: " + bg + ";");
    }

    private static String btnPressed(boolean gold, double size) {
        String bg = gold
                ? "linear-gradient(to bottom, #d9b268, #c39540)"
                : "linear-gradient(to bottom, #2c2013, #1b120b)";
        return btnBase(gold, size).replaceFirst(
                "-fx-background-color: [^;]+;", "-fx-background-color: " + bg + ";");
    }

    /** 主按钮：金渐变深字（CTA / 主要动作）。 */
    public static Button primaryButton(String text, double fontSize) {
        return buildButton(text, fontSize, true);
    }

    /** 次按钮：深木奶油字（次要动作）。 */
    public static Button darkButton(String text, double fontSize) {
        return buildButton(text, fontSize, false);
    }

    private static Button buildButton(String text, double fontSize, boolean gold) {
        Button b = new Button(text);
        String normal = btnBase(gold, fontSize);
        String hover = btnHover(gold, fontSize);
        String pressed = btnPressed(gold, fontSize);
        b.setStyle(normal);
        TranslateTransition lift = new TranslateTransition(Duration.millis(80), b);
        lift.setToY(-1);
        TranslateTransition sink = new TranslateTransition(Duration.millis(60), b);
        sink.setToY(1);
        TranslateTransition reset = new TranslateTransition(Duration.millis(80), b);
        reset.setToY(0);
        b.setOnMouseEntered(e -> {
            if (b.isDisabled()) {
                return;
            }
            b.setStyle(hover);
            lift.stop();
            lift.playFromStart();
        });
        b.setOnMouseExited(e -> {
            b.setStyle(normal);
            reset.stop();
            reset.playFromStart();
        });
        b.setOnMousePressed(e -> {
            if (b.isDisabled()) {
                return;
            }
            b.setStyle(pressed);
            sink.stop();
            sink.playFromStart();
        });
        b.setOnMouseReleased(e -> b.setStyle(b.isHover() ? hover : normal));
        b.disabledProperty().addListener((ob, o, n) -> {
            b.setOpacity(n ? 0.45 : 1);
            if (n) {
                b.setStyle(normal);
            }
        });
        // 用事件过滤器而非 setOnAction：调用方后续 setOnAction 不会覆盖点击音效
        b.addEventHandler(ActionEvent.ACTION, e -> org.example.gobang.audio.SoundManager.play(
                org.example.gobang.audio.SoundType.CLICK));
        b.setCursor(javafx.scene.Cursor.HAND);
        return b;
    }

    /** 难度切换胶囊：未选=深木，选中=翠绿。 */
    public static ToggleButton toggleButton(String text, ToggleGroup group) {
        ToggleButton b = new ToggleButton(text);
        b.setToggleGroup(group);
        b.setFocusTraversable(false);
        b.setCursor(javafx.scene.Cursor.HAND);
        String off = btnBase(false, 16);
        String offHover = btnHover(false, 16);
        String on = ("-fx-background-color: linear-gradient(to bottom, " + SEL_GREEN_TOP + ", " + SEL_GREEN_BOT + ");"
                + "-fx-background-radius: 14;"
                + "-fx-border-color: " + SEL_BORDER + ";"
                + "-fx-border-radius: 14;"
                + "-fx-border-width: 1.2;"
                + "-fx-font-family: '" + FONT_BODY + "';"
                + "-fx-font-weight: bold;"
                + "-fx-text-fill: #ffffff;"
                + "-fx-cursor: hand;"
                + "-fx-focus-traversable: false;"
                + "-fx-padding: 10 24 10 24;"
                + "-fx-font-size: 16px;");
        b.setStyle(off);
        b.selectedProperty().addListener((ob, o, n) -> b.setStyle(n ? on : off));
        b.setOnMouseEntered(e -> {
            if (!b.isSelected()) {
                b.setStyle(offHover);
            }
        });
        b.setOnMouseExited(e -> {
            if (!b.isSelected()) {
                b.setStyle(off);
            }
        });
        b.addEventHandler(ActionEvent.ACTION, e -> org.example.gobang.audio.SoundManager.play(
                org.example.gobang.audio.SoundType.CLICK));
        return b;
    }

    public static final String SEL_GREEN_TOP = "#7fae57";
    public static final String SEL_GREEN_BOT = "#5d9440";
    public static final String SEL_BORDER = "#3f6b2a";

    // ---------- 文本 ----------

    public static Label makerLabel() {
        Label l = new Label(org.example.gobang.fx.Ui.MAKER);
        l.setStyle("-fx-font-family: '" + FONT_BODY + "'; -fx-font-size: 12px; -fx-text-fill: " + TEXT_DIM + ";");
        return l;
    }

    public static Label label(String text, double size, String color, boolean bold) {
        Label l = new Label(text);
        l.setStyle("-fx-font-family: '" + FONT_BODY + "'; -fx-font-size: " + size + "px;"
                + (bold ? "-fx-font-weight: bold;" : "") + "-fx-text-fill: " + color + ";");
        return l;
    }

    /** 标题专用（书法字体）。 */
    public static Label titleLabel(String text, double size, String color) {
        Label l = new Label(text);
        l.setStyle("-fx-font-family: '" + fontTitle + "', '" + FONT_BODY + "'; -fx-font-size: " + size
                + "px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
        return l;
    }

    // ---------- 布局工具 ----------

    public static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    public static Region vSpacer() {
        Region r = new Region();
        VBox.setVgrow(r, Priority.ALWAYS);
        return r;
    }

    // ---------- 玻璃拟态面板（spec §1.4） ----------

    /** 双层描边玻璃拟态面板：外金 1px + 内高光 1px（inset 4）。 */
    public static VBox panel(double maxWidth) {
        VBox p = new VBox(16);
        p.setAlignment(Pos.CENTER);
        p.setPadding(new javafx.geometry.Insets(30, 44, 22, 44));
        p.setMaxWidth(maxWidth);
        p.setStyle("-fx-background-color: rgba(22,28,18,0.88);"
                + "-fx-background-radius: 20, 16;"
                + "-fx-border-color: rgba(232,196,122,0.45), rgba(255,230,180,0.08);"
                + "-fx-border-radius: 20, 16;"
                + "-fx-border-width: 1, 1;"
                + "-fx-border-insets: 0, 4;");
        p.setEffect(new DropShadow(24, Color.rgb(0, 0, 0, 0.6)));
        return p;
    }

    /** 面板顶部装饰：金渐变分隔线 + 中央菱形。 */
    public static HBox divider(double width) {
        Region left = new Region();
        left.setPrefHeight(1);
        left.setPrefWidth(width);
        left.setStyle("-fx-background-color: linear-gradient(to right, transparent, "
                + GOLD + ");");
        Region right = new Region();
        right.setPrefHeight(1);
        right.setPrefWidth(width);
        right.setStyle("-fx-background-color: linear-gradient(to right, " + GOLD + ", transparent);");
        StackPane diamond = new StackPane();
        Rectangle d = new Rectangle(6, 6);
        d.setRotate(45);
        d.setFill(Color.web(GOLD));
        diamond.getChildren().add(d);
        HBox box = new HBox(8, left, diamond, right);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    // ---------- 遮罩与弹窗动画（spec §3.5） ----------

    /** 径向渐变遮罩：中心 0.50 → 边缘 0.68，消费点击防穿透。 */
    public static Rectangle radialMask() {
        Rectangle dark = new Rectangle(800, 900);
        dark.setFill(new javafx.scene.paint.RadialGradient(0, 0, 400, 450, 640,
                false, javafx.scene.paint.CycleMethod.NO_CYCLE,
                new javafx.scene.paint.Stop(0, Color.rgb(0, 0, 0, 0.50)),
                new javafx.scene.paint.Stop(1, Color.rgb(0, 0, 0, 0.68))));
        dark.setOnMouseClicked(e -> e.consume());
        return dark;
    }

    /** spring 弹入：scale 0.82→1 + fade，320ms easeOutBack。 */
    public static void springIn(Node n) {
        n.setScaleX(0.82);
        n.setScaleY(0.82);
        n.setOpacity(0);
        ScaleTransition st = new ScaleTransition(Duration.millis(320), n);
        st.setToX(1);
        st.setToY(1);
        st.setInterpolator(EASE_OUT_BACK);
        FadeTransition ft = new FadeTransition(Duration.millis(200), n);
        ft.setToValue(1);
        st.play();
        ft.play();
    }

    /** 淡出后从父容器移除；期间由调用方保证不再触发。 */
    public static void fadeOutRemove(Pane parent, Node overlay) {
        FadeTransition ft = new FadeTransition(Duration.millis(150), overlay);
        ft.setToValue(0);
        ft.setOnFinished(e -> parent.getChildren().remove(overlay));
        ft.play();
    }

    /** 给任意根挂上全局 css（滑条等控件精修）。 */
    public static void applyCss(Pane root) {
        var url = Theme.class.getResource("/css/theme.css");
        if (url != null && !root.getStylesheets().contains(url.toExternalForm())) {
            root.getStylesheets().add(url.toExternalForm());
        }
    }
}
