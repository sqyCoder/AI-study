package org.example.gobang.fx;

import javafx.animation.Interpolator;
import javafx.animation.PauseTransition;
import javafx.animation.RotateTransition;
import javafx.animation.ScaleTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import org.example.gobang.audio.SoundManager;
import org.example.gobang.audio.SoundType;
import org.example.gobang.logic.GameSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 猜先仪式（spec §3.5）：
 * 随机分配持子/猜子角色 → 播报+头像高亮 → 持子方操作（人/两按钮、AI/转圈）
 * → 猜子方操作 → 揭晓（鼓点+数字翻转）→ 判定（欢庆/低沉）→ 黑方先行。
 */
public class GuessDialog {

    private final GameSession session;
    private final GameSession.Mode mode;
    private final Pane parent;
    private final Runnable onDone;
    private final Random rnd = new Random();
    private final List<PauseTransition> timers = new ArrayList<>();

    private final StackPane overlay = new StackPane();
    private final VBox panel = new VBox(14);
    private final Label announce = new Label();
    private final Label hint = new Label();
    private final Label number = new Label("？");
    private final Label result = new Label();
    private final HBox avatarRow = new HBox(56);
    private final StackPane actionArea = new StackPane();
    private StackPane holderAvatar;
    private StackPane guesserAvatar;

    private boolean holderIsAI;
    private boolean guesserIsAI;
    private String holderName;
    private String guesserName;
    private int held = -1;
    private boolean guessOdd;
    private boolean closed = false;
    private RotateTransition spinRt;

    public GuessDialog(GameSession session, Pane parent, Runnable onDone) {
        this.session = session;
        this.mode = session.getMode();
        this.parent = parent;
        this.onDone = onDone;
    }

    public void show() {
        assignRoles();
        build();
        parent.getChildren().add(overlay);
        startFlow();
    }

    private void assignRoles() {
        if (mode == GameSession.Mode.PVE) {
            holderIsAI = rnd.nextBoolean();
            guesserIsAI = !holderIsAI;
            holderName = holderIsAI ? "AI" : "你";
            guesserName = guesserIsAI ? "AI" : "你";
        } else {
            boolean p1Holds = rnd.nextBoolean();
            holderIsAI = false;
            guesserIsAI = false;
            holderName = p1Holds ? "玩家1" : "玩家2";
            guesserName = p1Holds ? "玩家2" : "玩家1";
        }
    }

    private void build() {
        Rectangle dark = new Rectangle(800, 900, Color.rgb(0, 0, 0, 0.55));
        dark.setOnMouseClicked(e -> e.consume());
        overlay.getChildren().add(dark);

        panel.setAlignment(Pos.CENTER);
        panel.setPadding(new Insets(26, 34, 20, 34));
        panel.setMaxWidth(560);
        panel.setStyle("-fx-background-color: rgba(56, 35, 15, 0.95); -fx-background-radius: 22;"
                + "-fx-border-color: #8a6a3a; -fx-border-radius: 22; -fx-border-width: 2;");

        Label title = new Label("猜先");
        title.setStyle("-fx-font-family: '" + Ui.FONT + "'; -fx-font-size: 34px; -fx-font-weight: bold;"
                + "-fx-text-fill: #ffd54a;");

        announce.setStyle("-fx-font-family: '" + Ui.FONT + "'; -fx-font-size: 22px; -fx-font-weight: bold;"
                + "-fx-text-fill: #f5e9cf;");

        holderAvatar = wrap(Ui.badge(holderName, "#3fae4f"));
        guesserAvatar = wrap(Ui.badge(guesserName, "#3f8fae"));
        avatarRow.setAlignment(Pos.CENTER);
        avatarRow.getChildren().addAll(holderAvatar, guesserAvatar);

        actionArea.setPrefHeight(120);
        actionArea.setAlignment(Pos.CENTER);

        number.setStyle("-fx-font-family: '" + Ui.FONT + "'; -fx-font-size: 56px; -fx-font-weight: bold;"
                + "-fx-text-fill: #ffd54a;");
        hint.setStyle("-fx-font-family: '" + Ui.FONT + "'; -fx-font-size: 18px; -fx-text-fill: #f0e0bd;");
        result.setStyle("-fx-font-family: '" + Ui.FONT + "'; -fx-font-size: 24px; -fx-font-weight: bold;");

        panel.getChildren().addAll(title, announce, avatarRow, actionArea, number, hint, result, Ui.makerLabel());
        overlay.getChildren().add(panel);
    }

    private StackPane wrap(javafx.scene.Node badge) {
        StackPane sp = new StackPane(badge);
        sp.setPrefSize(110, 90);
        return sp;
    }

    // ---------- 流程 ----------

    private void startFlow() {
        announce.setText("随机决定：" + holderName + " 持子");
        SoundManager.play(SoundType.GUESS_HOLD);
        Ui.pulse(holderAvatar);
        after(1000, this::stepHolder);
    }

    private void stepHolder() {
        if (closed) return;
        if (holderIsAI) {
            showSpin(holderName + " 握子中…");
            after(1000 + rnd.nextInt(500), () -> {
                held = 1 + rnd.nextInt(2);
                stepGuesser();
            });
        } else {
            showHolderButtons();
        }
    }

    private void showHolderButtons() {
        clearActionArea();
        HBox row = new HBox(20);
        row.setAlignment(Pos.CENTER);
        Button b1 = Ui.styledButton("握 1 颗", 22);
        Button b2 = Ui.styledButton("握 2 颗", 22);
        b1.setOnAction(e -> pickHeld(1, b1, b2));
        b2.setOnAction(e -> pickHeld(2, b1, b2));
        row.getChildren().addAll(b1, b2);
        actionArea.getChildren().add(row);
        hint.setText("");
    }

    private void pickHeld(int n, Button b1, Button b2) {
        held = n;
        SoundManager.play(SoundType.GUESS_PICK);
        b1.setDisable(true);
        b2.setDisable(true);
        Button chosen = n == 1 ? b1 : b2;
        chosen.setStyle(chosen.getStyle() + "-fx-background-color: #8fbf4f; -fx-text-fill: white;");
        hint.setText(holderName + " 已握好（结果保密）");
        after(500, this::stepGuesser);
    }

    private void stepGuesser() {
        if (closed) return;
        if (guesserIsAI) {
            showSpin(guesserName + " 猜子中…");
            after(1000 + rnd.nextInt(500), () -> {
                guessOdd = rnd.nextBoolean();
                reveal();
            });
        } else {
            clearActionArea();
            HBox row = new HBox(20);
            row.setAlignment(Pos.CENTER);
            Button odd = Ui.styledButton("单数", 22);
            Button even = Ui.styledButton("双数", 22);
            odd.setOnAction(e -> pickGuess(true, odd, even));
            even.setOnAction(e -> pickGuess(false, odd, even));
            row.getChildren().addAll(odd, even);
            actionArea.getChildren().add(row);
        }
    }

    private void pickGuess(boolean odd, Button b1, Button b2) {
        guessOdd = odd;
        SoundManager.play(SoundType.GUESS_PICK);
        b1.setDisable(true);
        b2.setDisable(true);
        Button chosen = odd ? b1 : b2;
        chosen.setStyle(chosen.getStyle() + "-fx-background-color: #8fbf4f; -fx-text-fill: white;");
        after(500, this::reveal);
    }

    private void reveal() {
        if (closed) return;
        clearActionArea();
        // 鼓点 + 数字翻转
        SoundManager.play(SoundType.GUESS_REVEAL);
        number.setText(String.valueOf(held));
        ScaleTransition st = new ScaleTransition(Duration.millis(450), number);
        st.setFromX(0.1);
        st.setFromY(0.1);
        st.setToX(1);
        st.setToY(1);
        st.setInterpolator(Interpolator.EASE_OUT);
        st.play();
        hint.setText(holderName + " 握了 " + held + " 颗");
        after(1150, this::showVerdict);
    }

    private void showVerdict() {
        if (closed) return;
        boolean guesserWins = GameSession.guesserWins(held, guessOdd);
        String winnerName = guesserWins ? guesserName : holderName;
        result.setText((guesserWins ? "猜对！" : "猜错！") + winnerName + " 执黑先行");
        if (guesserWins) {
            result.setStyle("-fx-font-family: '" + Ui.FONT + "'; -fx-font-size: 24px; -fx-font-weight: bold;"
                    + "-fx-text-fill: #ffd54a;");
            SoundManager.play(SoundType.GUESS_RESULT_WIN);
            Ui.goldRing(guesserAvatar);
        } else {
            result.setStyle("-fx-font-family: '" + Ui.FONT + "'; -fx-font-size: 24px; -fx-font-weight: bold;"
                    + "-fx-text-fill: #c9b98f;");
            SoundManager.play(SoundType.GUESS_RESULT_LOSE);
            Ui.goldRing(holderAvatar);
        }
        Ui.pulse(guesserWins ? guesserAvatar : holderAvatar);
        session.applyGuess(holderIsAI, guesserIsAI, held, guessOdd);
        after(1700, this::close);
    }

    private void close() {
        if (closed) return;
        closed = true;
        if (spinRt != null) {
            spinRt.stop();
            spinRt = null;
        }
        for (PauseTransition t : timers) {
            t.stop();
        }
        parent.getChildren().remove(overlay);
        onDone.run();
    }

    // ---------- 工具 ----------

    private void after(long ms, Runnable r) {
        PauseTransition p = new PauseTransition(Duration.millis(ms));
        p.setOnFinished(e -> {
            if (!closed) r.run();
        });
        timers.add(p);
        p.play();
    }

    private void showSpin(String text) {
        clearActionArea();
        VBox box = new VBox(10);
        box.setAlignment(Pos.CENTER);
        Label l = new Label(text);
        l.setStyle("-fx-font-family: '" + Ui.FONT + "'; -fx-font-size: 20px; -fx-text-fill: #f0e0bd;");
        Circle c = new Circle(14);
        c.setFill(null);
        c.setStroke(Color.web("#f5e9cf"));
        c.setStrokeWidth(3);
        c.getStrokeDashArray().addAll(16.0, 12.0);
        RotateTransition rt = new RotateTransition(Duration.seconds(1), c);
        rt.setFromAngle(0);
        rt.setToAngle(360);
        rt.setCycleCount(RotateTransition.INDEFINITE);
        rt.setInterpolator(Interpolator.LINEAR);
        spinRt = rt;
        rt.play();
        box.getChildren().addAll(l, c);
        actionArea.getChildren().add(box);
    }

    /** 清空操作区并停掉残留的转圈动画，避免动画对象泄漏。 */
    private void clearActionArea() {
        if (spinRt != null) {
            spinRt.stop();
            spinRt = null;
        }
        actionArea.getChildren().clear();
    }
}