package org.example.gobang;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import org.example.gobang.audio.MusicManager;
import org.example.gobang.audio.SettingsStore;
import org.example.gobang.audio.SoundManager;
import org.example.gobang.fx.ForestBackground;
import org.example.gobang.fx.GameView;
import org.example.gobang.fx.MenuView;
import org.example.gobang.logic.GameSession;

/**
 * 五子棋入口：单一 800×900 固定窗口，菜单页与对局页共享同一个森林背景实例；
 * 窗口失焦时落叶暂停；关闭时停止 BGM/落叶。
 */
public class Main extends Application {

    private Stage stage;
    private Scene scene;
    private ForestBackground background;
    private MenuView menu;
    private GameView game;
    private SettingsStore settings;

    @Override
    public void start(Stage stage) {
        this.stage = stage;

        settings = new SettingsStore();
        SoundManager.init(settings);
        MusicManager.init(settings);

        background = new ForestBackground();
        background.start();
        MusicManager.start();

        menu = new MenuView(background, settings, this::openGame);

        scene = new Scene(menu.getRoot(), 800, 900);
        stage.setScene(scene);
        stage.setTitle("五子棋");
        stage.setResizable(false);
        stage.focusedProperty().addListener((ob, o, n) -> background.getLeaves().setPaused(!n));
        stage.setOnCloseRequest(e -> 
            background.stop();
            MusicManager.shutdown();
        });
        stage.show();
    }

    private void openGame(GameSession.Mode mode, GameSession.Difficulty difficulty) {
        game = new GameView(background, mode, difficulty, settings, this::openMenu);
        scene.setRoot(game.getRoot());
    }

    private void openMenu() {
        game = null;
        // GameView 曾把共享背景节点从菜单根中移走（节点只能有一个父节点），返回菜单时放回底部
        javafx.scene.layout.StackPane menuRoot = menu.getRoot();
        if (!menuRoot.getChildren().contains(background.getNode())) {
            menuRoot.getChildren().add(0, background.getNode());
        }
        scene.setRoot(menuRoot);
    }

    public static void main(String[] args) {
        launch(args);
    }
}