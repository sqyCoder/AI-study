package calculator;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class Main extends Application {

    private final CalculatorModel model = new CalculatorModel();
    private TextField display;

    @Override
    public void start(Stage stage) {
        display = new TextField("0");
        display.setEditable(false);
        display.setAlignment(Pos.CENTER_RIGHT);
        display.setPrefHeight(60);
        display.setStyle("-fx-font-size: 24px;");

        String[][] rows = {
                {"C", "⌫", "+/-", "/"},
                {"7", "8", "9", "*"},
                {"4", "5", "6", "-"},
                {"1", "2", "3", "+"},
                {"0", ".", "="}
        };

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));

        for (int c = 0; c < 4; c++) {
            ColumnConstraints col = new ColumnConstraints();
            col.setHgrow(Priority.ALWAYS);
            col.setPercentWidth(25);
            grid.getColumnConstraints().add(col);
        }
        for (int r = 0; r < 5; r++) {
            RowConstraints row = new RowConstraints();
            row.setVgrow(Priority.ALWAYS);
            row.setPercentHeight(20);
            grid.getRowConstraints().add(row);
        }

        for (int r = 0; r < rows.length; r++) {
            for (int c = 0; c < rows[r].length; c++) {
                String label = rows[r][c];
                Button button = new Button(label);
                button.setMaxWidth(Double.MAX_VALUE);
                button.setMaxHeight(Double.MAX_VALUE);
                button.setStyle("-fx-font-size: 20px;");
                if (r == 4 && c == 0) {
                    grid.add(button, 0, 4, 2, 1);
                } else {
                    int col = c;
                    if (r == 4) col = c + 1;
                    grid.add(button, col, r);
                }
                button.setOnAction(e -> onButtonClick(label));
            }
        }

        VBox root = new VBox(10, display, grid);
        root.setPadding(new Insets(10));
        VBox.setVgrow(grid, Priority.ALWAYS);

        Scene scene = new Scene(root, 400, 560);
        scene.setOnKeyPressed(this::handleKey);
        stage.setScene(scene);
        stage.setTitle("计算器");
        stage.show();
    }

    private void onButtonClick(String label) {
        switch (label) {
            case "C": model.pressClear(); break;
            case "⌫": model.pressBackspace(); break;
            case "+/-": model.pressNegate(); break;
            case ".": model.pressDot(); break;
            case "=": model.pressEquals(); break;
            case "+": case "-": case "*": case "/": model.pressOperator(label.charAt(0)); break;
            default: model.pressDigit(label.charAt(0)); break;
        }
        updateDisplay();
    }

    private void handleKey(KeyEvent e) {
        KeyCode code = e.getCode();
        if (e.isShortcutDown() && code == KeyCode.C) return;
        switch (code) {
            case DIGIT0: case NUMPAD0: model.pressDigit('0'); break;
            case DIGIT1: case NUMPAD1: model.pressDigit('1'); break;
            case DIGIT2: case NUMPAD2: model.pressDigit('2'); break;
            case DIGIT3: case NUMPAD3: model.pressDigit('3'); break;
            case DIGIT4: case NUMPAD4: model.pressDigit('4'); break;
            case DIGIT5: case NUMPAD5: model.pressDigit('5'); break;
            case DIGIT6: case NUMPAD6: model.pressDigit('6'); break;
            case DIGIT7: case NUMPAD7: model.pressDigit('7'); break;
            case DIGIT8: case NUMPAD8: model.pressDigit('8'); break;
            case DIGIT9: case NUMPAD9: model.pressDigit('9'); break;
            case ADD: case PLUS: model.pressOperator('+'); break;
            case SUBTRACT: case MINUS: model.pressOperator('-'); break;
            case MULTIPLY: model.pressOperator('*'); break;
            case SLASH: case DIVIDE: model.pressOperator('/'); break;
            case ENTER: case EQUALS: model.pressEquals(); break;
            case ESCAPE: model.pressClear(); break;
            case C: model.pressClear(); break;
            case BACK_SPACE: case DELETE: model.pressBackspace(); break;
            case PERIOD: case DECIMAL: model.pressDot(); break;
            default: return;
        }
        e.consume();
        updateDisplay();
    }

    private void updateDisplay() {
        display.setText(model.getDisplay());
    }

    public static void main(String[] args) {
        launch(args);
    }
}
