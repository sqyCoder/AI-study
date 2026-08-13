package org.example.ui.effect;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.RotateTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Paint;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import org.example.ui.FontKit;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 特效管理器（spec2 §4.4，中等特效）：
 * 合并爆点粒子、合计分飘字、生成块微光、胜利彩带、max 块呼吸光晕。
 * <p>
 * 全部纯 JavaFX Transition/Timeline；粒子数量上限、onFinished 即从父节点移除，
 * 杜绝节点泄漏；由 GameController 在动画收尾扩展点触发，受 animationLock 时序约束。
 */
public final class EffectManager {

    /** 正在呼吸光晕的 max 块及其循环动画（WeakKey 不阻断 GC；节点移除时须 stopGlow）。 */
    private static final Map<StackPane, Timeline> PULSES = new HashMap<>();

    /** 彩带配色（明亮现代色板）。 */
    private static final Color[] CONFETTI_COLORS = {
            Color.web("#ff9f43"), Color.web("#ff6b6b"), Color.web("#22d3ee"),
            Color.web("#7c6cf0"), Color.web("#f7c522"), Color.web("#4ade80"),
            Color.web("#ffb6d9")};

    private EffectManager() {
        // 工具类，禁止实例化
    }

    // ==================== 合并爆点 ====================

    /**
     * 合并爆点：在目标格中心迸发 8 颗同色小圆向四周飞散并渐隐（~170ms）。
     *
     * @param parent 特效挂载容器（方块层，坐标与之对齐）
     * @param cx     爆点中心 x（相对 parent）
     * @param cy     爆点中心 y
     * @param color  粒子主色（取合并后方块的主色，保持同色光效）
     */
    public static void mergeBurst(Pane parent, double cx, double cy, Color color) {
        Random rnd = new Random();
        for (int i = 0; i < 8; i++) {
            Circle p = new Circle(2.2 + rnd.nextDouble(), color);
            p.setLayoutX(cx);
            p.setLayoutY(cy);
            parent.getChildren().add(p);
            double angle = i * (Math.PI / 4) + rnd.nextDouble() * 0.5;
            double dist = 20 + rnd.nextDouble() * 16;
            TranslateTransition move = new TranslateTransition(Duration.millis(170), p);
            move.setToX(Math.cos(angle) * dist);
            move.setToY(Math.sin(angle) * dist);
            move.setInterpolator(Interpolator.EASE_OUT);
            FadeTransition fade = new FadeTransition(Duration.millis(170), p);
            fade.setToValue(0);
            ParallelTransition pt = new ParallelTransition(move, fade);
            pt.setOnFinished(ev -> parent.getChildren().remove(p));
            pt.play();
        }
    }

    // ==================== 合计分飘字 ====================

    /**
     * 合并分数飘字：在合并处上方上浮 "+N" 并渐隐（~700ms）。
     * 字号与样式走 CSS 类 score-popup，字体用内置 MiSans。
     */
    public static void scorePopup(Pane parent, double x, double y, String text) {
        Label label = new Label(text);
        label.getStyleClass().add("score-popup");
        label.setFont(FontKit.bold(16));
        label.setLayoutX(x);
        label.setLayoutY(y);
        parent.getChildren().add(label);
        TranslateTransition up = new TranslateTransition(Duration.millis(700), label);
        up.setToY(-34);
        up.setInterpolator(Interpolator.EASE_OUT);
        FadeTransition fade = new FadeTransition(Duration.millis(700), label);
        fade.setToValue(0);
        fade.setOnFinished(ev -> parent.getChildren().remove(label));
        new ParallelTransition(up, fade).play();
    }

    // ==================== 生成块微光 ====================

    /**
     * 生成块微光：短暂的光晕脉冲后恢复（240ms 内 4→18→8 半径）。
     * 结束时清空程序化 effect，交还 CSS 默认效果。
     */
    public static void spawnGlow(StackPane tile) {
        Color base = tileColor(tile);
        DropShadow shadow = new DropShadow(10, base);
        tile.setEffect(shadow);
        Timeline t = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(shadow.radiusProperty(), 4),
                        new KeyValue(shadow.spreadProperty(), 0.35)),
                new KeyFrame(Duration.millis(180),
                        new KeyValue(shadow.radiusProperty(), 18),
                        new KeyValue(shadow.spreadProperty(), 0.6)),
                new KeyFrame(Duration.millis(240),
                        new KeyValue(shadow.radiusProperty(), 8),
                        new KeyValue(shadow.spreadProperty(), 0.3)));
        t.setOnFinished(ev -> {
            if (tile.getEffect() == shadow) {
                tile.setEffect(null); // 交还 CSS 效果
            }
        });
        t.play();
    }

    // ==================== 胜利彩带 ====================

    /**
     * 胜利彩带：26 根彩色小矩形自容器顶部旋转下落并渐隐，衬在胜利遮罩之后。
     *
     * @param parent 彩带容器（遮罩层内的独立 Pane，绝对坐标）
     */
    public static void confetti(Pane parent, double width, double height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        Random rnd = new Random();
        for (int i = 0; i < 26; i++) {
            Rectangle r = new Rectangle(7 + rnd.nextDouble() * 5, 12 + rnd.nextDouble() * 6,
                    CONFETTI_COLORS[rnd.nextInt(CONFETTI_COLORS.length)]);
            r.setLayoutX(rnd.nextDouble() * width);
            r.setLayoutY(-20 - rnd.nextDouble() * 50);
            r.setRotate(rnd.nextDouble() * 360);
            r.setArcWidth(2);
            r.setArcHeight(2);
            parent.getChildren().add(r);
            double dur = 1400 + rnd.nextDouble() * 900;
            TranslateTransition fall = new TranslateTransition(Duration.millis(dur), r);
            fall.setToY(height + 40);
            fall.setInterpolator(Interpolator.EASE_IN);
            RotateTransition spin = new RotateTransition(Duration.millis(dur), r);
            spin.setByAngle(360 + rnd.nextDouble() * 540);
            FadeTransition fade = new FadeTransition(Duration.millis(1500), r);
            fade.setDelay(Duration.millis(1000));
            fade.setToValue(0);
            ParallelTransition all = new ParallelTransition(fall, spin, fade);
            all.setOnFinished(ev -> parent.getChildren().remove(r));
            all.play();
        }
    }

    // ==================== max 块呼吸光晕 ====================

    /**
     * 启动/保持 max 块的呼吸光晕（幂等）：DropShadow 半径 10→22→10 循环（1.8s）。
     */
    public static void pulseGlow(StackPane tile) {
        if (PULSES.containsKey(tile)) {
            return;
        }
        DropShadow shadow = new DropShadow(12, tileColor(tile));
        tile.setEffect(shadow);
        Timeline t = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(shadow.radiusProperty(), 10),
                        new KeyValue(shadow.spreadProperty(), 0.5)),
                new KeyFrame(Duration.millis(900),
                        new KeyValue(shadow.radiusProperty(), 22),
                        new KeyValue(shadow.spreadProperty(), 0.75)),
                new KeyFrame(Duration.millis(1800),
                        new KeyValue(shadow.radiusProperty(), 10),
                        new KeyValue(shadow.spreadProperty(), 0.5)));
        t.setCycleCount(Timeline.INDEFINITE);
        PULSES.put(tile, t);
        t.play();
    }

    /**
     * 停止并清除某方块的呼吸光晕（节点失去 max 身份 / 从层中移除时调用）。
     * 置空 effect 交还 CSS 默认效果。
     */
    public static void stopGlow(StackPane tile) {
        Timeline t = PULSES.remove(tile);
        if (t != null) {
            t.stop();
        }
        if (tile.getEffect() != null) {
            tile.setEffect(null);
        }
    }

    // ==================== 工具 ====================

    /**
     * 取方块主色：优先取背景渐变的首个色标（浅色端，粒子/光晕更亮眼），
     * 单色背景直接返回；取不到时回退暖橙色。
     */
    public static Color tileColor(StackPane tile) {
        Background bg = tile.getBackground();
        if (bg != null) {
            for (BackgroundFill f : bg.getFills()) {
                Paint p = f.getFill();
                if (p instanceof Color c) {
                    return c;
                }
                if (p instanceof LinearGradient g) {
                    for (Stop s : g.getStops()) {
                        if (s.getColor().getOpacity() > 0) {
                            return s.getColor();
                        }
                    }
                }
            }
        }
        return Color.web("#ff9f43");
    }
}