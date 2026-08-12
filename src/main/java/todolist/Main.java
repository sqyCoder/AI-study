package todolist;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage stage) {
        stage.setTitle("todolist");
        stage.setWidth(500);
        stage.setHeight(500);
        stage.setScene(new Scene(new StackPane()));
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}