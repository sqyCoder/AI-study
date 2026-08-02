import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage stage) {
        stage.setTitle("counter");
        stage.setWidth(500);
        stage.setHeight(500);

        Label label = new Label("0");
        label.setStyle("-fx-font-size: 48px;");
        Button button1 = new Button("+");
        Button button2 = new Button("-");
        button1.setStyle("-fx-font-size: 20px; -fx-padding: 10 20;");
        button2.setStyle("-fx-font-size: 20px; -fx-padding: 10 20;");

        button1.setOnAction(e -> label.setText(Integer.parseInt(label.getText()) + 1 + ""));
        button2.setOnAction(e -> label.setText(Integer.parseInt(label.getText()) - 1 + ""));

        HBox buttonBox = new HBox(10, button1, button2);
        buttonBox.setAlignment(Pos.CENTER);

        VBox root = new VBox(20, label, buttonBox);
        root.setAlignment(Pos.CENTER);

        stage.setScene(new Scene(root));
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
