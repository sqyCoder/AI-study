package org.example.gobang.fx;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.util.Duration;

/**
 * 棋盘/场景震动：
 * 落子 —— 幅度 3px / 150ms；终局 —— 8px / 600ms + 整体 Scene 根节点 5px 摆动。
 */
public class ShakeEffect {

    private final Node node;
    private Timeline timeline;

    public ShakeEffect(Node node) {
        this.node = node;
    }

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

    public void stop() {
        if (timeline != null) {
            timeline.stop();
            timeline = null;
        }
        node.setTranslateX(0);
        node.setTranslateY(0);
    }
}