package org.example;

import org.example.ui.GameController;
import org.example.ui.MainMenuController;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * 2048 小游戏主入口（spec §4.2 / spec3 §二）。
 * 启动先进主菜单（标题/棋盘选择/设置/最高分/退出），"开始游戏"按所选尺寸切到
 * 游戏场景；游戏内可返回主菜单（每次切换都新建场景，保证偏好与榜单即时生效）。
 */
public class App extends Application {

    /** 默认窗口宽 */
    private static final double WIDTH = 600;
    /** 默认窗口高 */
    private static final double HEIGHT = 760;

    private Stage stage;

    @Override
    public void start(Stage stage) throws Exception {
        this.stage = stage;
        stage.setTitle("2048");
        stage.setMinWidth(480);
        stage.setMinHeight(620);
        stage.setResizable(true);
        stage.show();
        showMainMenu();
    }

    /** 切换到主菜单场景（spec3 §二）。 */
    private void showMainMenu() throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/mainMenu.fxml"));
        Parent root = loader.load();
        Scene scene = new Scene(root, WIDTH, HEIGHT);
        stage.setScene(scene);
        MainMenuController controller = loader.getController();
        controller.setOnStartGame(size -> {
            try {
                showGame(size);
            } catch (Exception ex) {
                throw new RuntimeException("切换游戏场景失败", ex);
            }
        });
        controller.attach(stage);
        root.requestFocus();
    }

    /** 切换到游戏场景，按主菜单选定的棋盘尺寸开局（spec3 §二）。 */
    private void showGame(int size) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/game.fxml"));
        Parent root = loader.load();
        Scene scene = new Scene(root, WIDTH, HEIGHT);
        stage.setScene(scene);
        GameController controller = loader.getController();
        controller.setInitialSize(size);
        controller.setOnBackToMenu(() -> {
            try {
                showMainMenu();
            } catch (Exception ex) {
                throw new RuntimeException("返回主菜单失败", ex);
            }
        });
        controller.attach(stage);
        root.requestFocus();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
