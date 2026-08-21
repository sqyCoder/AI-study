package org.example.gobang.fx;

import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import org.example.gobang.audio.SettingsStore;
import org.example.gobang.audio.SoundManager;
import org.example.gobang.audio.SoundType;
import org.example.gobang.net.Link;
import org.example.gobang.net.NetLink;
import org.example.gobang.net.Protocol;
import org.example.gobang.net.supa.RoomCodes;
import org.example.gobang.net.supa.SupaConfig;
import org.example.gobang.net.supa.SupaRestLink;

import java.util.Random;
import java.util.function.Consumer;

/**
 * 联机房间页（spec4 §3 / v4.2 简化）：仅保留房间码联机——
 * 输入 4 位房号可创建同名房间，留空则随机生成；对方输房码加入。
 */
public class NetLobbyView {

    private final StackPane root = new StackPane();
    private final SettingsStore settings;
    private final Consumer<Link> onConnected;                  // 连接建立 → 进入对局
    private final Runnable onExit;

    private final VBox card = Theme.panel(400);
    private final Label nameTip = Theme.label("你的昵称", 14, Theme.TEXT_SUB, false);
    private final TextField nameField = new TextField();
    private final Label codeTip = Theme.label("房间号（4 位字母数字，留空则随机）", 14, Theme.TEXT_SUB, false);
    private final TextField codeField = new TextField();
    private final Button createBtn = Ui.styledButton("创建房间", 20);
    private final Button joinBtn = Ui.styledButton("加入房间", 20);
    private final HBox btnRow = new HBox(14);
    private final Label statusLabel = new Label();

    private final VBox waitBox = new VBox(10);
    private final Label codeLabel = new Label();
    private final Label dotsLabel = new Label();
    private Timeline dotsTl;

    private SupaConfig supaCfg;
    private SupaRestLink supaLink;

    public NetLobbyView(ForestBackground bg, SettingsStore settings,
                        Consumer<Link> onConnected, Runnable onExit) {
        this.settings = settings;
        this.onConnected = onConnected;
        this.onExit = onExit;
        build(bg);
    }

    public StackPane getRoot() {
        return root;
    }

    private void build(ForestBackground bg) {
        Theme.applyCss(root);
        root.getChildren().add(bg.getNode());

        HBox top = new HBox();
        top.setPadding(new Insets(12, 16, 0, 0));
        top.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        SettingsPanel settingsPanel = new SettingsPanel(root, settings);
        Button gear = Ui.smallButton("⚙ 设置");
        gear.setOnAction(e -> settingsPanel.show());
        top.getChildren().add(gear);
        StackPane.setAlignment(top, Pos.TOP_RIGHT);

        VBox center = new VBox(24);
        center.setAlignment(Pos.CENTER);

        Label title = Theme.titleLabel("联机对战", 52, Theme.CREAM);
        title.setStyle(title.getStyle()
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.8), 18, 0, 0, 3);");
        Label sub = Theme.label("凭 4 位房间码 · 与好友异地对弈", 17, "#4a3520", true);
        sub.setStyle(sub.getStyle()
                + "-fx-effect: dropshadow(gaussian, rgba(255,250,230,0.85), 8, 0, 0, 1);");

        buildCard();
        center.getChildren().addAll(title, sub, card, back());

        root.getChildren().addAll(center, top);

        VBox bottom = new VBox();
        bottom.setPadding(new Insets(0, 0, 10, 0));
        bottom.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        bottom.getChildren().add(Ui.makerLabel());
        StackPane.setAlignment(bottom, Pos.BOTTOM_CENTER);
        root.getChildren().add(bottom);
    }

    private Button back() {
        Button back = Theme.darkButton("← 返回菜单", 20);
        back.setOnAction(e -> {
            cancelSupa();
            onExit.run();
        });
        return back;
    }

    // ---------- 卡片 ----------

    private void buildCard() {
        card.getChildren().add(Theme.titleLabel("房间码联机", 26, Theme.GOLD_BRIGHT));
        supaCfg = SupaConfig.load();

        String saved = settings.getNetName();
        nameField.setText(saved == null || saved.isBlank()
                ? "棋客" + (10 + new Random().nextInt(90)) : saved);
        nameField.setPrefHeight(38);
        nameField.setMaxWidth(Double.MAX_VALUE);

        codeField.setPromptText("例如 K7XQ，不含 0/O/1/I/L");
        codeField.setTextFormatter(new javafx.scene.control.TextFormatter<String>(c ->
                c.getControlNewText().length() <= 6 ? c : null));
        codeField.setPrefHeight(38);
        codeField.setMaxWidth(Double.MAX_VALUE);

        createBtn.setMaxWidth(Double.MAX_VALUE);
        createBtn.setOnAction(e -> onCreate());
        joinBtn.setMaxWidth(Double.MAX_VALUE);
        joinBtn.setOnAction(e -> onJoin());
        btnRow.getChildren().addAll(createBtn, joinBtn);
        btnRow.setAlignment(Pos.CENTER);
        HBox.setHgrow(createBtn, javafx.scene.layout.Priority.ALWAYS);
        HBox.setHgrow(joinBtn, javafx.scene.layout.Priority.ALWAYS);

        statusLabel.setWrapText(true);
        statusLabel.setStyle("-fx-font-family: '" + Theme.FONT_BODY + "'; -fx-font-size: 13px;"
                + "-fx-text-fill: #e07a6a;");
        statusLabel.setVisible(false);
        statusLabel.setManaged(false);

        // 等待接入态
        waitBox.setAlignment(Pos.CENTER);
        waitBox.setVisible(false);
        waitBox.setManaged(false);
        Label waitTip = Theme.label("把房号发给好友，等待接入…", 15, Theme.TEXT_MAIN, false);
        codeLabel.setStyle("-fx-font-family: '" + Theme.FONT_BODY + "'; -fx-font-size: 40px;"
                + "-fx-font-weight: bold; -fx-text-fill: " + Theme.GOLD_BRIGHT + ";"
                + "-fx-cursor: hand;");
        codeLabel.setOnMouseClicked(ev -> {
            ClipboardContent cc = new ClipboardContent();
            cc.putString(codeLabel.getText());
            Clipboard.getSystemClipboard().setContent(cc);
            SoundManager.play(SoundType.CLICK);
            dotsLabel.setText("已复制到剪贴板！");
        });
        dotsLabel.setStyle("-fx-font-family: '" + Theme.FONT_BODY + "'; -fx-font-size: 13px;"
                + "-fx-text-fill: " + Theme.TEXT_SUB + ";");
        Button cancelWait = Ui.smallButton("取消");
        cancelWait.setOnAction(e -> {
            cancelSupa();
            resetIdle();
        });
        waitBox.getChildren().addAll(waitTip, codeLabel, dotsLabel, cancelWait);

        if (supaCfg == null) {
            Label hint = Theme.label("未配置 Supabase，联机功能不可用", 13, "#e07a6a", false);
            hint.setWrapText(true);
            createBtn.setDisable(true);
            joinBtn.setDisable(true);
            codeField.setDisable(true);
            card.getChildren().addAll(nameTip, nameField, hint, Ui.makerLabel());
            return;
        }

        card.getChildren().addAll(nameTip, nameField, codeTip, codeField, btnRow,
                statusLabel, waitBox, Ui.makerLabel());
    }

    /** 解析输入房号：合法返回规范化值；留空返回随机码；非法返回 null 并提示。 */
    private String resolveInputCode(boolean forCreate) {
        String raw = codeField.getText().trim();
        if (raw.isEmpty()) {
            if (forCreate) {
                return RoomCodes.generate(); // 留空：随机生成
            }
            showStatus("请输入好友的 4 位房号");
            return null;
        }
        String norm = RoomCodes.normalize(raw);
        if (norm == null) {
            showStatus("房号须为 4 位字母数字，且不含易混的 0/O/1/I/L");
            return null;
        }
        return norm;
    }

    private void onCreate() {
        hideStatus();
        persistName();
        String code = resolveInputCode(true);
        if (code == null) {
            return;
        }
        startLink(code, true);
    }

    private void onJoin() {
        hideStatus();
        persistName();
        String code = resolveInputCode(false);
        if (code == null) {
            return;
        }
        startLink(code, false);
    }

    private void startLink(String code, boolean host) {
        SupaRestLink link = new SupaRestLink(supaCfg, code, host);
        link.start(localName(), lobbyListener());
        supaLink = link;
        createBtn.setDisable(true);
        joinBtn.setDisable(true);
        nameTip.setVisible(false);
        nameTip.setManaged(false);
        nameField.setVisible(false);
        nameField.setManaged(false);
        codeTip.setVisible(false);
        codeTip.setManaged(false);
        codeField.setVisible(false);
        codeField.setManaged(false);
        btnRow.setVisible(false);
        btnRow.setManaged(false);
        codeLabel.setText(code);
        dotsLabel.setText("正在连接服务器…");
        waitBox.setVisible(true);
        waitBox.setManaged(true);
    }

    private void persistName() {
        String n = nameField.getText().trim();
        if (!n.isEmpty()) {
            settings.setNetName(n.length() > Protocol.MAX_NAME
                    ? n.substring(0, Protocol.MAX_NAME) : n);
            settings.persist();
        }
    }

    /** 大厅期监听器：就绪即导航进对局（监听器随后由 GameView 移交接管）。 */
    private NetLink.Listener lobbyListener() {
        return new NetLink.Listener() {
            @Override
            public void onMessage(String line) {
                // 大厅期不处理游戏消息
            }

            @Override
            public void onDisconnected(String reason) {
                javafx.application.Platform.runLater(() -> {
                    supaLink = null;
                    stopDots();
                    resetIdle();
                    showStatus(reason);
                });
            }

            @Override
            public void onPeerReady() {
                javafx.application.Platform.runLater(() -> {
                    SupaRestLink l = supaLink;
                    supaLink = null;
                    stopDots();
                    if (l != null) {
                        onConnected.accept(l);
                    }
                });
            }

            @Override
            public void onLinkReady() {
                javafx.application.Platform.runLater(() -> {
                    if (supaLink != null) {
                        dotsLabel.setText("房间已就绪，等待对方接入…");
                        startDots();
                    }
                });
            }
        };
    }

    private void cancelSupa() {
        if (supaLink != null) {
            supaLink.close("取消");
            supaLink = null;
        }
        stopDots();
        resetIdle();
    }

    private void resetIdle() {
        createBtn.setDisable(supaCfg == null);
        joinBtn.setDisable(supaCfg == null);
        nameTip.setVisible(true);
        nameTip.setManaged(true);
        nameField.setVisible(true);
        nameField.setManaged(true);
        codeTip.setVisible(true);
        codeTip.setManaged(true);
        codeField.setVisible(true);
        codeField.setManaged(true);
        btnRow.setVisible(true);
        btnRow.setManaged(true);
        waitBox.setVisible(false);
        waitBox.setManaged(false);
    }

    private void startDots() {
        stopDots();
        final int[] phase = {0};
        dotsTl = new Timeline(new javafx.animation.KeyFrame(Duration.millis(500), e -> {
            phase[0] = (phase[0] + 1) % 4;
            if (phase[0] > 0) {
                dotsLabel.setText("等待对方接入" + ".".repeat(phase[0]));
            } else {
                dotsLabel.setText("等待对方接入…");
            }
        }));
        dotsTl.setCycleCount(Timeline.INDEFINITE);
        dotsTl.play();
    }

    private void stopDots() {
        if (dotsTl != null) {
            dotsTl.stop();
            dotsTl = null;
        }
    }

    private void showStatus(String msg) {
        statusLabel.setText(msg);
        statusLabel.setVisible(true);
        statusLabel.setManaged(true);
    }

    private void hideStatus() {
        statusLabel.setVisible(false);
        statusLabel.setManaged(false);
    }

    /** 昵称统一来源：设置持久化优先，其次输入框，最后默认值。 */
    private String localName() {
        String saved = settings.getNetName();
        String n = (saved == null || saved.isBlank()) ? nameField.getText().trim() : saved;
        if (n.isEmpty()) {
            n = "棋客" + (10 + new Random().nextInt(90));
        }
        return n.length() > Protocol.MAX_NAME ? n.substring(0, Protocol.MAX_NAME) : n;
    }
}
