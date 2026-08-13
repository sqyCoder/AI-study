package org.example.ui;

import javafx.animation.Interpolator;
import javafx.animation.TranslateTransition;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

/**
 * 动态光晕背景（spec2 §4.2）：4 个高斯模糊的径向渐变圆缓慢漂移，
 * 形成"缓慢流动的渐变光晕"。仅用 TranslateTransition，不占 AnimationTimer；
 * 颜色随主题切换（浅色=暖杏/天蓝/粉/薄荷，暗色=靛蓝/青/紫）。
 * <p>
 * 挂在 root 的 bgLayer 层（鼠标穿透，不拦截任何事件）。
 */
public class GlowBackground extends Pane {

    private final Circle[] glows = new Circle[4];

    /** 每颗光晕：圆心所在 [宽比例, 高比例] 与半径。 */
    private static final double[][] SPOTS = {
            {0.10, 0.16, 240},
            {0.86, 0.20, 280},
            {0.82, 0.84, 230},
            {0.16, 0.86, 260}};

    /** 浅色主题 4 颗光晕的主色。 */
    private static final Color[] LIGHT_COLORS = {
            Color.web("#ffc98f"), Color.web("#a7c8ff"),
            Color.web("#ffb6d9"), Color.web("#b8e6c9")};

    /** 暗黑主题 4 颗光晕的主色。 */
    private static final Color[] DARK_COLORS = {
            Color.web("#32409c"), Color.web("#1785a8"),
            Color.web("#6a4bbf"), Color.web("#4b3a86")};

    /** 光晕漂移幅度（px）。 */
    private static final double DRIFT = 48;

    public GlowBackground(String theme) {
        setMouseTransparent(true);
        for (int i = 0; i < glows.length; i++) {
            Circle c = new Circle(SPOTS[i][2]);
            c.setEffect(new GaussianBlur(90));
            // 随窗口尺寸变化保持相对位置（拉伸不变形）
            c.centerXProperty().bind(widthProperty().multiply(SPOTS[i][0]));
            c.centerYProperty().bind(heightProperty().multiply(SPOTS[i][1]));
            glows[i] = c;
            getChildren().add(c);
        }
        applyTheme(theme);
        startDrift();
    }

    /** 按主题刷新光晕配色（浅色/暗黑两套）。 */
    public void applyTheme(String theme) {
        Color[] palette = ThemeManager.DARK.equals(theme) ? DARK_COLORS : LIGHT_COLORS;
        double opacity = ThemeManager.DARK.equals(theme) ? 0.5 : 0.38;
        for (int i = 0; i < glows.length; i++) {
            Circle c = glows[i];
            c.setFill(radial(palette[i]));
            c.setOpacity(opacity);
        }
    }

    /** 径向渐变：中心主色 → 渐隐至透明（柔和光晕）。 */
    private static RadialGradient radial(Color center) {
        return new RadialGradient(0, 0, 0.5, 0.5, 1.0, true, CycleMethod.NO_CYCLE,
                new Stop(0, center),
                new Stop(0.55, new Color(center.getRed(), center.getGreen(), center.getBlue(), 0.28)),
                new Stop(1, Color.TRANSPARENT));
    }

    /** 每颗光晕以不同周期/相位缓慢漂移并往返（6~11s，EASE_BOTH）。 */
    private void startDrift() {
        for (int i = 0; i < glows.length; i++) {
            TranslateTransition t = new TranslateTransition(Duration.seconds(6.5 + i * 1.5), glows[i]);
            t.setByX((i % 2 == 0 ? 1 : -1) * DRIFT);
            t.setByY((i < 2 ? 1 : -1) * DRIFT * 0.8);
            t.setAutoReverse(true);
            t.setCycleCount(TranslateTransition.INDEFINITE);
            t.setInterpolator(Interpolator.EASE_BOTH);
            t.setDelay(Duration.seconds(i * 0.9));
            t.play();
        }
    }
}