package org.example.gobang.fx;

import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * 统一 UI 助手：样式实现已迁入 Theme（spec2 §1），本类保留旧签名转发，
 * 并持有头像徽标 / 脉冲 / 金圈等小部件。
 */
public final class Ui {

    public static final String MAKER = "制作：林森lsjs";

    private Ui() {
    }

    /** 旧签名：主金按钮（保持既有调用点与冒烟测试像素预期）。 */
    public static Button styledButton(String text, double fontSize) {
        return Theme.primaryButton(text, fontSize);
    }

    public static Button smallButton(String text) {
        return styledButton(text, 16);
    }

    public static ToggleButton toggleButton(String text, ToggleGroup group) {
        return Theme.toggleButton(text, group);
    }

    public static Label makerLabel() {
        return Theme.makerLabel();
    }

    public static Region spacer() {
        return Theme.spacer();
    }

    public static Region vSpacer() {
        return Theme.vSpacer();
    }

    /** 头像徽标：圆 + 名字。 */
    public static VBox badge(String name, String fill) {
        javafx.scene.shape.Circle circle = new javafx.scene.shape.Circle(34);
        circle.setFill(javafx.scene.paint.Color.web(fill));
        circle.setStroke(javafx.scene.paint.Color.web("#ffffff", 0.85));
        circle.setStrokeWidth(2.5);
        Label label = new Label(name);
        label.setStyle("-fx-font-family: '" + Theme.FONT_BODY + "'; -fx-font-size: 16px; -fx-font-weight: bold;"
                + "-fx-text-fill: " + Theme.CREAM + ";");
        VBox box = new VBox(6, circle, label);
        box.setAlignment(javafx.geometry.Pos.CENTER);
        return box;
    }

    /** 头像高亮脉冲动画。 */
    public static void pulse(Node node) {
        ScaleTransition st = new ScaleTransition(Duration.millis(260), node);
        st.setFromX(1);
        st.setFromY(1);
        st.setToX(1.16);
        st.setToY(1.16);
        st.setAutoReverse(true);
        st.setCycleCount(4);
        st.setOnFinished(e -> {
            node.setScaleX(1);
            node.setScaleY(1);
        });
        st.play();
    }

    /** 金圈绽放（可叠双环，spec2 §4.5）。 */
    public static void goldRing(javafx.scene.layout.StackPane avatarNode) {
        goldRings(avatarNode, 1);
    }

    public static void goldRings(javafx.scene.layout.StackPane avatarNode, int count) {
        for (int i = 0; i < count; i++) {
            javafx.scene.shape.Circle ring = new javafx.scene.shape.Circle(46);
            ring.setFill(null);
            ring.setStroke(javafx.scene.paint.Color.web(i == 0 ? "#ffd54a" : "#fff2cc"));
            ring.setStrokeWidth(i == 0 ? 4 : 2);
            ring.setOpacity(0);
            avatarNode.getChildren().add(ring);
            ScaleTransition st = new ScaleTransition(Duration.millis(400 + i * 120), ring);
            st.setDelay(Duration.millis(i * 110));
            st.setFromX(0.6);
            st.setFromY(0.6);
            st.setToX(1.25 + i * 0.25);
            st.setToY(1.25 + i * 0.25);
            st.setOnFinished(e -> avatarNode.getChildren().remove(ring));
            ring.setOpacity(1);
            st.play();
        }
    }
}
