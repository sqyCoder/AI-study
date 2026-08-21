package org.example.gobang.fx;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.util.Duration;

/**
 * 棋盘/场景震动（spec2 扩展）：
 * shake —— 固定关键帧强震（终局 8px/600ms、根节点摆动）；
 * shakeDamped —— 指数衰减阻尼微震（落子接触 1.5px/110ms）。
 */
public class ShakeEffect {

    private final Node node;
    private Timeline timeline;

    public ShakeEffect(Node node) {
        this.node = node;
    }

    /** 强震：4 折线关键帧。 */
    public void shake(double amp, double durMs) {
        stop();
        Timeline tl = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(node.translateXProperty(), 0),
                        new KeyValue(node.translateYProperty(), 0)),
                new KeyFrame(Duration.millis(durMs * 0.25),
                        new KeyValue(node.translateXProperty(), amp),
                        new KeyValue(node.translateYProperty(), -amp * 0.6)),
                new KeyFrame(Duration.millis(durMs * 0.5),
                        new KeyValue(node.translateXProperty(), -amp * 0.8),
                        new KeyValue(node.translateYProperty(), amp * 0.5)),
                new KeyFrame(Duration.millis(durMs * 0.75),
                        new KeyValue(node.translateXProperty(), amp * 0.6),
                        new KeyValue(node.translateYProperty(), -amp * 0.35)),
                new KeyFrame(Duration.millis(durMs),
                        new KeyValue(node.translateXProperty(), 0),
                        new KeyValue(node.translateYProperty(), 0))
        );
        timeline = tl;
        tl.play();
    }

    /** 阻尼微震：正弦振荡 × 指数衰减，结束精确归零。 */
    public void shakeDamped(double amp, double durMs) {
        stop();
        int steps = 8;
        KeyFrame[] frames = new KeyFrame[steps + 1];
        frames[0] = new KeyFrame(Duration.ZERO,
                new KeyValue(node.translateXProperty(), 0),
                new KeyValue(node.translateYProperty(), 0));
        for (int i = 1; i <= steps; i++) {
            double k = (double) i / steps;
            double decay = Math.exp(-4.5 * k);
            double ang = k * durMs / 1000.0 * 2 * Math.PI * 9; // ~9Hz 抖动
            double x = Math.cos(ang) * amp * decay;
            double y = Math.sin(ang * 0.9) * amp * 0.6 * decay;
            frames[i] = new KeyFrame(Duration.millis(durMs * k),
                    new KeyValue(node.translateXProperty(), i == steps ? 0 : x),
                    new KeyValue(node.translateYProperty(), i == steps ? 0 : y));
        }
        Timeline tl = new Timeline(frames);
        timeline = tl;
        tl.play();
    }

    public void stop() {
        if (timeline != null) {
            timeline.stop();
            timeline = null;
        }
        node.setTranslateX(0);
        node.setTranslateY(0);
    }
}
