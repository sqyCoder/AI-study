package org.example.gobang;

import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.application.Application;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;

import org.example.gobang.audio.MusicManager;
import org.example.gobang.audio.SettingsStore;
import org.example.gobang.audio.SoundManager;
import org.example.gobang.audio.SoundType;
import org.example.gobang.audio.SynthWav;
import org.example.gobang.fx.ForestBackground;
import org.example.gobang.fx.GameView;
import org.example.gobang.fx.MenuView;
import org.example.gobang.fx.NetLobbyView;
import org.example.gobang.fx.Particles;
import org.example.gobang.fx.Theme;
import org.example.gobang.logic.GameSession;
import org.example.gobang.net.Link;
import org.example.gobang.net.NetLink;

/**
 * 五子棋入口（spec2 §4.6）：
 * 单一 800×900 固定窗口；菜单页与对局页共享森林棋室背景与全局粒子层；
 * 页面切换淡出/淡入 + 微缩放过渡；启动后台预热合成音缓存；
 * 窗口失焦暂停粒子；关闭时停止 BGM/粒子。
 */
public class Main extends Application {

    private Stage stage;
    private Scene scene;
    private ForestBackground background;
    private MenuView menu;
    private GameView game;
    private NetLobbyView lobby;
    private SettingsStore settings;
    private boolean switching = false;

    @Override
    public void start(Stage stage) {
        this.stage = stage;

        Theme.loadFonts();
        settings = new SettingsStore();
        SoundManager.init(settings);
        MusicManager.init(settings);

        background = new ForestBackground();
        background.start();
        MusicManager.start();

        // 后台预热合成音（v4 缓存），首局开始前生成完毕，首次播放零卡顿
        Thread preload = new Thread(SynthWav::preloadAll, "sfx-preload");
        preload.setDaemon(true);
        preload.start();

        menu = new MenuView(background, settings, this::openGame, this::openLobby);

        scene = new Scene(menu.getRoot(), 800, 900);
        stage.setScene(scene);
        stage.setTitle("五子棋");
        stage.setResizable(false);
        stage.focusedProperty().addListener((ob, o, n) -> background.setPaused(!n));
        stage.setOnCloseRequest(e -> {
            if (game != null) {
                game.shutdownForWindowClose();
            }
            background.stop();
            MusicManager.shutdown();
        });
        stage.show();
    }

    private void openGame(GameSession.Mode mode, GameSession.Difficulty difficulty) {
        game = new GameView(background, mode, difficulty, settings, this::openMenu);
        switchRoot(game.getRoot());
    }

    /** 进入联机房间页（spec4 §3）。 */
    private void openLobby() {
        if (lobby == null) {
            lobby = new NetLobbyView(background, settings,
                    this::openOnlineGame,
                    this::openMenu);
        }
        switchRoot(lobby.getRoot());
    }

    /** 连接建立（房主/客人通用）→ 进入联机对局。 */
    private void openOnlineGame(Link link) {
        game = new GameView(background, GameSession.Mode.ONLINE, null, settings,
                this::openMenu, link);
        switchRoot(game.getRoot());
    }

    private void openMenu() {
        game = null;
        switchRoot(menu.getRoot());
    }

    /** 切页过渡：旧根淡出 140ms → 换根 → 新根淡入 200ms + scale 0.985→1。 */
    private void switchRoot(StackPane next) {
        if (switching) {
            return;
        }
        switching = true;
        SoundManager.play(SoundType.PAGE_SWITCH, 0.5);
        Node oldRoot = scene.getRoot();
        attachShared(next);
        FadeTransition out = new FadeTransition(Duration.millis(140), oldRoot);
        out.setToValue(0);
        out.setOnFinished(e -> {
            scene.setRoot(next);
            next.setOpacity(0);
            next.setScaleX(0.985);
            next.setScaleY(0.985);
            FadeTransition in = new FadeTransition(Duration.millis(200), next);
            in.setToValue(1);
            in.setOnFinished(e2 -> switching = false);
            ScaleTransition sc = new ScaleTransition(Duration.millis(200), next);
            sc.setToX(1);
            sc.setToY(1);
            in.play();
            sc.play();
        });
        out.play();
    }

    /** 共享节点重挂载：背景垫底、粒子层置顶（JavaFX 节点单父，需手动搬移）。 */
    private void attachShared(StackPane root) {
        Node bg = background.getNode();
        if (!root.getChildren().contains(bg)) {
            root.getChildren().add(0, bg);
        }
        Node dust = Particles.get().getCanvas();
        if (!root.getChildren().contains(dust)) {
            root.getChildren().add(dust);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
