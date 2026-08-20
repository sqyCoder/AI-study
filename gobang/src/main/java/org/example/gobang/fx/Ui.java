package org.example.gobang.fx;

import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.event.ActionEvent;
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

import org.example.gobang.audio.SoundManager;
import org.example.gobang.audio.SoundType;

/** 统一 UI 样式助手：按钮 / 标注 / 徽标 / 布局工具。 */
public final class Ui {

    public static final String MAKER = "制作：林森lsjs";
    public static final String FONT = "Microsoft YaHei";

    private static final String BTN_BASE =
            "-fx-background-color: linear-gradient(to bottom, #f2dcae, #d9b378);"
            + "-fx-background-radius: 16;"
            + "-fx-border-color: #8a6a3a;"
            + "-fx-border-radius: 16;"
            + "-fx-border-width: 1.5;"
            + "-fx-font-family: '" + FONT + "';"
            + "-fx-font-weight: bold;"
            + "-fx-text-fill: #4a3010;"
            + "-fx-cursor: hand;"
            + "-fx-focus-traversable: false;"
            + "-fx-padding: 10 24 10 24;";
    private static final String BTN_HOVER =
            "-fx-background-color: linear-gradient(to bottom, #ffe8bc, #e6c384);"
            + "-fx-background-radius: 16;"
            + "-fx-border-color: #8a6a3a;"
            + "-fx-border-radius: 16;"
            + "-fx-border-width: 1.5;"
            + "-fx-font-family: '" + FONT + "';"
            + "-fx-font-weight: bold;"
            + "-fx-text-fill: #4a3010;"
            + "-fx-cursor: hand;"
            + "-fx-focus-traversable: false;"
            + "-fx-padding: 10 24 10 24;";
    private static final String BTN_PRESSED =
            "-fx-background-color: linear-gradient(to bottom, #c99d5e, #b0813f);"
            + "-fx-background-radius: 16;"
            + "-fx-border-color: #6e4f28;"
            + "-fx-border-radius: 16;"
            + "-fx-border-width: 1.5;"
            + "-fx-font-family: '" + FONT + "';"
            + "-fx-font-weight: bold;"
            + "-fx-text-fill: #3c2608;"
            + "-fx-cursor: hand;"
            + "-fx-focus-traversable: false;"
            + "-fx-padding: 10 24 10 24;";
    private static final String TOGGLE_SELECTED = BTN_BASE.replace("#f2dcae, #d9b378", "#8fbf4f, #6f9e3a")
            .replace("#4a3010", "#ffffff")
            .replace("#8a6a3a", "#4c6b26");

    private Ui() {
    }

    public static Button styledButton(String text, double fontSize) {
        Button b = new Button(text);
        String style = BTN_BASE + "-fx-font-size: " + fontSize + "px;";
        String hover = BTN_HOVER + "-fx-font-size: " + fontSize + "px;";
        String pressed = BTN_PRESSED + "-fx-font-size: " + fontSize + "px;";
        b.setStyle(style);
        b.setOnMouseEntered(e -> b.setStyle(hover));
        b.setOnMouseExited(e -> b.setStyle(style));
        b.setOnMousePressed(e -> b.setStyle(pressed));
        b.setOnMouseReleased(e -> b.setStyle(b.isHover() ? hover : style));
        // 用事件过滤器而非 setOnAction：调用方后续 setOnAction 不会覆盖点击音效
        b.addEventHandler(ActionEvent.ACTION, e -> SoundManager.play(SoundType.CLICK));
        b.setCursor(Cursor.HAND);
        return b;
    }

    public static Button smallButton(String text) {
        return styledButton(text, 16);
    }

    public static ToggleButton toggleButton(String text, ToggleGroup group) {
        ToggleButton b = new ToggleButton(text);
        b.setToggleGroup(group);
        b.setFocusTraversable(false);
        b.setCursor(Cursor.HAND);
        b.setStyle(BTN_BASE + "-fx-font-size: 16px;");
        b.selectedProperty().addListener((ob, o, n) ->
                b.setStyle((n ? TOGGLE_SELECTED : BTN_BASE) + "-fx-font-size: 16px;"));
        b.setOnMouseEntered(e -> {
            if (!b.isSelected()) b.setStyle(BTN_HOVER + "-fx-font-size: 16px;");
        });
        b.setOnMouseExited(e -> {
            if (!b.isSelected()) b.setStyle(BTN_BASE + "-fx-font-size: 16px;");
        });
        b.addEventHandler(ActionEvent.ACTION, e -> SoundManager.play(SoundType.CLICK));
        return b;
    }

    public static Label makerLabel() {
        Label l = new Label(MAKER);
        l.setStyle("-fx-font-family: '" + FONT + "'; -fx-font-size: 12px; -fx-text-fill: rgba(255,255,255,0.6);");
        return l;
    }

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

    /** 头像徽标：圆 + 名字。 */
    public static VBox badge(String name, String fill) {
        javafx.scene.shape.Circle circle = new javafx.scene.shape.Circle(34);
        circle.setFill(javafx.scene.paint.Color.web(fill));
        circle.setStroke(javafx.scene.paint.Color.web("#ffffff", 0.85));
        circle.setStrokeWidth(2.5);
        Label label = new Label(name);
        label.setStyle("-fx-font-family: '" + FONT + "'; -fx-font-size: 16px; -fx-font-weight: bold;"
                + "-fx-text-fill: #f5e9cf;");
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

    /** 头像金圈绽放动画。 */
    public static void goldRing(javafx.scene.layout.StackPane avatarNode) {
        javafx.scene.shape.Circle ring = new javafx.scene.shape.Circle(46);
        ring.setFill(null);
        ring.setStroke(javafx.scene.paint.Color.web("#ffd54a"));
        ring.setStrokeWidth(4);
        ring.setOpacity(0);
        avatarNode.getChildren().add(ring);
        TranslateTransition t = new TranslateTransition(Duration.millis(400), ring);
        t.setFromY(0);
        t.setToY(0);
        ScaleTransition st = new ScaleTransition(Duration.millis(400), ring);
        st.setFromX(0.6);
        st.setFromY(0.6);
        st.setToX(1.25);
        st.setToY(1.25);
        st.setOnFinished(e -> {
            avatarNode.getChildren().remove(ring);
        });
        ring.setOpacity(1);
        st.play();
    }
}