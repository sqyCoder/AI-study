package org.example.gobang.fx;

import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.RotateTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
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
import javafx.util.Duration;

import org.example.gobang.audio.SoundManager;
import org.example.gobang.audio.SoundType;
import org.example.gobang.logic.GameSession;
import org.example.gobang.model.Board;
import org.example.gobang.net.GuessCrypto;
import org.example.gobang.net.Link;
import org.example.gobang.net.Protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.IntConsumer;

/**
 * 远程猜先（spec3 §4）：密码学承诺-揭示复刻猜单双仪式。
 * 房主=持子方：打开即发 GUESS_COMMIT；客人=猜子方：COMMIT 到达前按钮禁用。
 * 揭示哈希校验失败 → 红字警告 + BYE 断开（防作弊红线）。
 * 视觉沿用 GuessDialog 体系（spring 弹入 / 数字滚动定格 / 双环金圈）。
 */
public class RemoteGuessDialog {

    private final GameSession session;
    private final Pane parent;
    private final Link link;
    private final boolean isHost;
    private final IntConsumer onDone;
    private final Runnable onAbort;
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
    private final StackPane holderAvatar;
    private final StackPane guesserAvatar;

    private GuessCrypto.Commit commit;      // 房主持有
    private String expectedHash;            // 客人持有
    private boolean myGuessOdd;
    private boolean choiceSent;
    private boolean closed;
    private RotateTransition spinRt;

    public RemoteGuessDialog(GameSession session, Pane parent, Link link,
                             boolean isHost, IntConsumer onDone, Runnable onAbort) {
        this.session = session;
        this.parent = parent;
        this.link = link;
        this.isHost = isHost;
        this.onDone = onDone;
        this.onAbort = onAbort;
        this.holderAvatar = wrap(Ui.badge(isHost ? "你 · 持子" : "对方 · 持子", "#3fae4f"));
        this.guesserAvatar = wrap(Ui.badge(isHost ? "对方 · 猜子" : "你 · 猜子", "#3f8fae"));
    }

    public void show() {
        build();
        parent.getChildren().add(overlay);
        Theme.springIn(panel);
        if (isHost) {
            // 承诺先行：颗数仅存内存，哈希随消息外发
            commit = GuessCrypto.createCommit();
            link.send(Protocol.guessCommit(commit.hashHex()));
            SoundManager.play(SoundType.GUESS_HOLD);
            Ui.pulse(holderAvatar);
            announce.setText("你已暗中握子（结果保密）");
            showSpin("等待对方猜单双…");
        } else {
            SoundManager.play(SoundType.GUESS_HOLD);
            Ui.pulse(holderAvatar);
            announce.setText("对方正在暗中握子…");
            showSpin("等待承诺到达…");
        }
    }

    /** GameView 把 GUESS_* 消息路由到这里（FX 线程）。 */
    public void onMessage(Protocol.Message m) {
        if (closed) {
            return;
        }
        switch (m.type()) {
            case GUESS_COMMIT -> {
                if (isHost || expectedHash != null) {
                    abortViolation();
                    return;
                }
                expectedHash = m.get("hash");
                showGuessButtons();
            }
            case GUESS_CHOICE -> {
                if (!isHost || commit == null || choiceSent) {
                    abortViolation();
                    return;
                }
                choiceSent = true;
                boolean odd = "1".equals(m.get("odd"));
                hostReveal(odd);
            }
            case GUESS_REVEAL -> {
                if (isHost || expectedHash == null || !choiceSent) {
                    abortViolation();
                    return;
                }
                guestVerify(Integer.parseInt(m.get("count")), m.get("salt"));
            }
            default -> abortViolation();
        }
    }

    /** 断线等外部原因：静默销毁（不回调 onDone）。 */
    public void destroy() {
        closed = true;
        stopTimers();
        parent.getChildren().remove(overlay);
    }

    // ---------- 房主：揭示 ----------

    private void hostReveal(boolean odd) {
        clearActionArea();
        SoundManager.play(SoundType.GUESS_REVEAL);
        rollTo(commit.count());
        hint.setText("对方已猜" + (odd ? "单数" : "双数"));
        link.send(Protocol.guessReveal(commit.count(), commit.salt()));
        // 本地自证（理论必过，防御内存损坏）
        if (!GuessCrypto.verify(
                new GuessCrypto.Reveal(commit.count(), commit.salt()), commit.hashHex())) {
            finishVerdictText("本地承诺校验异常", false);
            return;
        }
        after(1150, () -> showVerdict(commit.count(), odd));
    }

    // ---------- 客人：校验揭示 ----------

    private void guestVerify(int count, String saltHex) {
        byte[] salt = new byte[16];
        for (int i = 0; i < 16; i++) {
            salt[i] = (byte) Integer.parseInt(saltHex.substring(i * 2, i * 2 + 2), 16);
        }
        if (!GuessCrypto.verify(new GuessCrypto.Reveal(count, salt), expectedHash)) {
            // 防作弊红线：红字警告 + BYE 断开
            clearActionArea();
            SoundManager.play(SoundType.INVALID);
            finishVerdictText("承诺校验失败，对方数据不一致", false);
            link.close("承诺校验失败");
            after(1400, onAbort);
            return;
        }
        SoundManager.play(SoundType.GUESS_REVEAL);
        rollTo(count);
        hint.setText("对方握了 " + count + " 颗");
        after(1150, () -> showVerdict(count, myGuessOdd));
    }

    // ---------- 判定与收尾 ----------

    private void showVerdict(int held, boolean guessOdd) {
        if (closed) {
            return;
        }
        boolean guesserWins = GameSession.guesserWins(held, guessOdd);
        String winnerName = guesserWins
                ? (isHost ? "对方" : "你")
                : (isHost ? "你" : "对方");
        boolean iAmBlack = guesserWins != isHost; // 猜中=客人执黑
        finishVerdictText((guesserWins ? "猜对！" : "猜错！") + winnerName + " 执黑先行",
                (isHost ? !guesserWins : guesserWins));

        StackPane winAvatar = guesserWins ? guesserAvatar : holderAvatar;
        Ui.goldRings(winAvatar, 2);
        var pt = winAvatar.localToScene(winAvatar.getWidth() / 2, winAvatar.getHeight() / 2 - 12);
        if (pt != null) {
            Particles.get().sparks(pt.getX(), pt.getY(), 6);
        }
        Ui.pulse(winAvatar);

        session.applyGuess(false, false, held, guessOdd);
        int myColor = iAmBlack ? Board.BLACK : Board.WHITE;
        after(1700, () -> {
            closed = true;
            stopTimers();
            parent.getChildren().remove(overlay);
            onDone.accept(myColor);
        });
    }

    private void finishVerdictText(String text, boolean good) {
        result.setText(text);
        result.setStyle("-fx-font-family: '" + Theme.FONT_BODY + "'; -fx-font-size: 24px;"
                + "-fx-font-weight: bold; -fx-text-fill: "
                + (good ? Theme.GOLD_BRIGHT : "#c9b98f") + ";");
        SoundManager.play(good ? SoundType.GUESS_RESULT_WIN : SoundType.GUESS_RESULT_LOSE);
        result.setScaleX(0.9);
        result.setScaleY(0.9);
        ScaleTransition pop = new ScaleTransition(Duration.millis(200), result);
        pop.setToX(1);
        pop.setToY(1);
        pop.setInterpolator(Theme.EASE_OUT_BACK);
        pop.play();
    }

    /** 协议越序：fail-fast 断开（spec3 §0.1）。 */
    private void abortViolation() {
        if (closed) {
            return;
        }
        clearActionArea();
        SoundManager.play(SoundType.INVALID);
        finishVerdictText("协议违规，连接终止", false);
        link.close("协议违规");
        after(1200, onAbort);
    }

    // ---------- UI 构建 ----------

    private void build() {
        if (parent instanceof StackPane sp) {
            Theme.applyCss(sp);
        }
        overlay.getChildren().add(Theme.radialMask());
        panel.setAlignment(Pos.CENTER);
        panel.setPadding(new Insets(26, 34, 20, 34));
        panel.setMaxWidth(560);

        Label title = Theme.titleLabel("猜先", 34, Theme.GOLD_BRIGHT);
        announce.setStyle("-fx-font-family: '" + Theme.FONT_BODY + "'; -fx-font-size: 22px;"
                + "-fx-font-weight: bold; -fx-text-fill: " + Theme.CREAM + ";");
        avatarRow.setAlignment(Pos.CENTER);
        avatarRow.getChildren().addAll(holderAvatar, guesserAvatar);
        actionArea.setPrefHeight(120);
        actionArea.setAlignment(Pos.CENTER);
        number.setStyle("-fx-font-family: '" + Theme.FONT_BODY + "'; -fx-font-size: 56px;"
                + "-fx-font-weight: bold; -fx-text-fill: " + Theme.GOLD_BRIGHT + ";");
        hint.setStyle("-fx-font-family: '" + Theme.FONT_BODY + "'; -fx-font-size: 18px;"
                + "-fx-text-fill: " + Theme.TEXT_MAIN + ";");

        panel.getChildren().addAll(title, Theme.divider(340), announce, avatarRow,
                actionArea, number, hint, result, Ui.makerLabel());
        overlay.getChildren().add(panel);
    }

    private StackPane wrap(javafx.scene.Node badge) {
        StackPane sp = new StackPane(badge);
        sp.setPrefSize(110, 90);
        return sp;
    }

    private void showGuessButtons() {
        clearActionArea();
        HBox row = new HBox(20);
        row.setAlignment(Pos.CENTER);
        Button odd = Ui.styledButton("单数", 22);
        Button even = Ui.styledButton("双数", 22);
        announce.setText("对方已握子，请猜单双");
        odd.setOnAction(e -> pickGuess(true, odd, even));
        even.setOnAction(e -> pickGuess(false, odd, even));
        row.getChildren().addAll(odd, even);
        actionArea.getChildren().add(row);
    }

    private void pickGuess(boolean odd, Button b1, Button b2) {
        if (choiceSent || closed) {
            return;
        }
        myGuessOdd = odd;
        choiceSent = true;
        SoundManager.play(SoundType.GUESS_PICK);
        b1.setDisable(true);
        b2.setDisable(true);
        link.send(Protocol.guessChoice(odd));
        hint.setText("已提交，等待对方揭晓…");
        showSpin("");
    }

    // ---------- 动效工具（沿用 GuessDialog 模式） ----------

    /** 数字滚动定格（spec2 §4.5）。 */
    private void rollTo(int real) {
        Timeline roll = new Timeline(new KeyFrame(Duration.millis(45), e ->
                number.setText(String.valueOf(1 + rnd.nextInt(2)))));
        roll.setCycleCount(10);
        roll.setOnFinished(e -> {
            number.setText(String.valueOf(real));
            ScaleTransition st = new ScaleTransition(Duration.millis(220), number);
            st.setFromX(1.28);
            st.setFromY(1.28);
            st.setToX(1);
            st.setToY(1);
            st.setInterpolator(Theme.EASE_OUT_BACK);
            st.play();
        });
        roll.play();
    }

    private void showSpin(String text) {
        clearActionArea();
        VBox box = new VBox(10);
        box.setAlignment(Pos.CENTER);
        Label l = Theme.label(text, 20, Theme.TEXT_MAIN, false);
        Circle c = new Circle(14);
        c.setFill(null);
        c.setStroke(Color.web(Theme.GOLD_BRIGHT));
        c.setStrokeWidth(3);
        c.getStrokeDashArray().addAll(16.0, 12.0);
        RotateTransition rt = new RotateTransition(Duration.seconds(1), c);
        rt.setFromAngle(0);
        rt.setToAngle(360);
        rt.setCycleCount(RotateTransition.INDEFINITE);
        rt.setInterpolator(javafx.animation.Interpolator.LINEAR);
        spinRt = rt;
        rt.play();
        box.getChildren().addAll(l, c);
        actionArea.getChildren().add(box);
    }

    private void clearActionArea() {
        if (spinRt != null) {
            spinRt.stop();
            spinRt = null;
        }
        actionArea.getChildren().clear();
    }

    private void after(long ms, Runnable r) {
        PauseTransition p = new PauseTransition(Duration.millis(ms));
        p.setOnFinished(e -> {
            if (!closed) {
                r.run();
            }
        });
        timers.add(p);
        p.play();
    }

    private void stopTimers() {
        if (spinRt != null) {
            spinRt.stop();
            spinRt = null;
        }
        for (PauseTransition t : timers) {
            t.stop();
        }
        timers.clear();
    }
}
