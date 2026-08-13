package org.example;

import java.net.URL;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * 2048 小游戏主入口（spec §4.2）。
 * 本类只负责装配：加载 FXML、创建 Scene、挂载样式表，不包含任何业务逻辑。
 */
public class App extends Application {

    /** 默认窗口宽 */
    private static final double WIDTH = 600;
    /** 默认窗口高 */
    private static final double HEIGHT = 760;

    @Override
    public void start(Stage stage) throws Exception {
        URL fxml = getClass().getResource("/fxml/game.fxml");
        FXMLLoader loader = new FXMLLoader(fxml);
        Scene scene = new Scene(loader.load(), WIDTH, HEIGHT);

        // 默认加载浅色主题样式（M3 起提供双主题切换）
        URL lightCss = getClass().getResource("/css/game-light.css");
        scene.getStylesheets().add(lightCss.toExternalForm());

        stage.setTitle("2048");
        stage.setMinWidth(480);
        stage.setMinHeight(620);
        stage.setResizable(true);
        stage.setScene(scene);
        stage.show();

        // 保证按键事件可直达根节点（spec §5 焦点对策）
        scene.getRoot().requestFocus();
    }

    public static void main(String[] args) {
        launch(args);
    }
}