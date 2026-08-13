package org.example;

import org.example.ui.GameController;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * 2048 小游戏主入口（spec §4.2）。
 * 本类只负责装配：加载 FXML、创建 Scene、交给控制器完成主题/文案初始化，
 * 不包含任何业务逻辑。
 */
public class App extends Application {

    /** 默认窗口宽 */
    private static final double WIDTH = 600;
    /** 默认窗口高 */
    private static final double HEIGHT = 760;

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/game.fxml"));
        Parent root = loader.load();
        Scene scene = new Scene(root, WIDTH, HEIGHT);
        GameController controller = loader.getController();

        stage.setTitle("2048");
        stage.setMinWidth(480);
        stage.setMinHeight(620);
        stage.setResizable(true);
        stage.setScene(scene);
        stage.show();

        // 场景就绪后装配：应用偏好主题、刷新文案、窗口标题（spec §4.2）
        controller.attach(stage);

        // 保证按键事件可直达根节点（spec §5 焦点对策）
        root.requestFocus();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
