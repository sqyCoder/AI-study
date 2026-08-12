package todolist;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Main extends Application {

    private static final int MAX_TEXT_LENGTH = 100;

    private final javafx.collections.ObservableList<TodoItem> items =
            javafx.collections.FXCollections.observableArrayList();
    private final Map<Category, VBox> cardBoxes = new EnumMap<>(Category.class);
    private final Map<Category, Label> countLabels = new EnumMap<>(Category.class);

    private TextField input;
    private ComboBox<Category> createCategory;
    private ScrollPane scroll;
    private VBox content;
    private Label statsLabel;
    private ProgressBar progressBar;

    @Override
    public void start(Stage stage) {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");

        root.setTop(buildHeader());
        root.setCenter(buildBody());
        root.setBottom(buildFooter());

        Scene scene = new Scene(root, 500, 640);
        scene.getStylesheets().add(getClass().getResource("/todolist/app.css").toExternalForm());

        stage.setTitle("TodoList");
        stage.setResizable(false);
        stage.setScene(scene);
        stage.show();

        items.setAll(Storage.load());
        refreshAll();
        input.requestFocus();
    }

    private VBox buildHeader() {
        Label title = new Label("TodoList");
        title.getStyleClass().add("app-title");

        Label date = new Label(LocalDate.now()
                .format(DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINA)));
        date.getStyleClass().add("app-date");
        HBox.setHgrow(date, Priority.ALWAYS);

        HBox head = new HBox(12, title, date);
        head.setAlignment(Pos.CENTER_LEFT);

        input = new TextField();
        input.setPromptText("添加新任务，按回车快速添加");
        input.getStyleClass().add("task-input");
        input.setOnAction(e -> addTask());
        input.addEventHandler(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                input.clear();
            }
        });
        HBox.setHgrow(input, Priority.ALWAYS);

        createCategory = new ComboBox<>();
        createCategory.getItems().addAll(Category.values());
        createCategory.getSelectionModel().selectFirst();
        createCategory.setPrefWidth(110);
        createCategory.getStyleClass().add("cat-combo");

        Button addBtn = new Button("添加");
        addBtn.getStyleClass().add("add-btn");
        addBtn.setOnAction(e -> addTask());

        HBox inputRow = new HBox(10, input, createCategory, addBtn);
        inputRow.setAlignment(Pos.CENTER);

        VBox header = new VBox(14, head, inputRow);
        header.setPadding(new Insets(24, 24, 16, 24));
        return header;
    }

    private ScrollPane buildBody() {
        content = new VBox(18);
        content.setPadding(new Insets(4, 24, 8, 24));
        for (Category category : Category.values()) {
            content.getChildren().add(buildSection(category));
        }

        scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("body-scroll");
        return scroll;
    }

    private VBox buildSection(Category category) {
        Circle dot = new Circle(5);
        dot.setFill(Color.web(category.getColor()));

        Label name = new Label(category.getLabel());
        name.getStyleClass().add("section-name");

        Label count = new Label("0");
        count.getStyleClass().add("count-pill");
        countLabels.put(category, count);

        HBox head = new HBox(8, dot, name, count);
        head.setAlignment(Pos.CENTER_LEFT);

        VBox cards = new VBox(10);
        cardBoxes.put(category, cards);

        return new VBox(10, head, cards);
    }

    private HBox buildCard(TodoItem item) {
        CheckBox check = new CheckBox();
        check.getStyleClass().add("task-check");
        check.setSelected(item.isDone());
        check.setOnAction(e -> {
            item.setDone(check.isSelected());
            saveAndRefresh();
        });

        Label label = new Label(item.getText());
        label.getStyleClass().add("task-text");
        if (item.isDone()) {
            label.getStyleClass().add("done");
        }
        label.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(label, Priority.ALWAYS);
        label.setTooltip(new Tooltip("双击编辑任务"));

        ComboBox<Category> categoryBox = new ComboBox<>();
        categoryBox.getItems().addAll(Category.values());
        categoryBox.getSelectionModel().select(item.getCategory());
        categoryBox.setPrefWidth(110);
        categoryBox.getStyleClass().add("cat-combo");
        categoryBox.setOnAction(e -> {
            if (categoryBox.getValue() != item.getCategory()) {
                item.setCategory(categoryBox.getValue());
                saveAndRefresh();
            }
        });

        Button del = new Button("✕");
        del.getStyleClass().add("del-btn");
        del.setTooltip(new Tooltip("删除任务"));
        del.setOnAction(e -> {
            items.remove(item);
            saveAndRefresh();
        });

        HBox card = new HBox(10, check, label, categoryBox, del);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("task-card");

        label.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                startEdit(card, item, label);
            }
        });
        return card;
    }

    private void startEdit(HBox card, TodoItem item, Label label) {
        TextField editor = new TextField(item.getText());
        editor.getStyleClass().add("edit-field");
        card.getChildren().set(card.getChildren().indexOf(label), editor);
        editor.selectAll();
        editor.requestFocus();

        editor.setOnAction(e -> commitEdit(item, editor));
        editor.addEventHandler(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                saveAndRefresh();
            }
        });
        editor.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                commitEdit(item, editor);
            }
        });
    }

    private void commitEdit(TodoItem item, TextField editor) {
        String text = editor.getText().trim();
        if (!text.isEmpty() && !text.equals(item.getText())) {
            item.setText(text.length() > MAX_TEXT_LENGTH ? text.substring(0, MAX_TEXT_LENGTH) : text);
        }
        saveAndRefresh();
    }

    private VBox buildFooter() {
        statsLabel = new Label();
        statsLabel.getStyleClass().add("progress-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button clear = new Button("清除已完成");
        clear.getStyleClass().add("ghost-btn");
        clear.setOnAction(e -> {
            items.removeIf(TodoItem::isDone);
            saveAndRefresh();
        });

        HBox meta = new HBox(statsLabel, spacer, clear);
        meta.setAlignment(Pos.CENTER_LEFT);

        progressBar = new ProgressBar(0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.getStyleClass().add("progress-bar");

        VBox footer = new VBox(10, meta, progressBar);
        footer.setPadding(new Insets(10, 24, 20, 24));
        return footer;
    }

    private void addTask() {
        String text = input.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            text = text.substring(0, MAX_TEXT_LENGTH);
        }
        TodoItem item = new TodoItem(text, false, createCategory.getValue());
        items.add(item);
        input.clear();
        input.requestFocus();
        saveAndRefresh();
        scrollTo(item);
    }

    private void scrollTo(TodoItem item) {
        VBox section = cardBoxes.get(item.getCategory());
        if (section == null) {
            return;
        }
        Platform.runLater(() -> {
            double y = section.getBoundsInParent().getMinY();
            double viewportHeight = scroll.getViewportBounds().getHeight();
            double contentHeight = content.getBoundsInParent().getHeight();
            double maxScroll = Math.max(0, contentHeight - viewportHeight);
            if (maxScroll > 0) {
                scroll.setVvalue(clamp01(y / maxScroll));
            }
        });
    }

    private static double clamp01(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private void saveAndRefresh() {
        Storage.save(items);
        refreshAll();
    }

    private void refreshAll() {
        for (Category category : Category.values()) {
            refreshCategory(category);
        }
        refreshFooter();
    }

    private void refreshCategory(Category category) {
        List<TodoItem> list = items.stream()
                .filter(item -> item.getCategory() == category)
                .toList();

        countLabels.get(category).setText(String.valueOf(list.size()));

        VBox cards = cardBoxes.get(category);
        cards.getChildren().clear();
        if (list.isEmpty()) {
            Label hint = new Label("此分类暂无任务");
            hint.getStyleClass().add("empty-hint");
            cards.getChildren().add(hint);
        } else {
            for (TodoItem item : list) {
                cards.getChildren().add(buildCard(item));
            }
        }
    }

    private void refreshFooter() {
        int total = items.size();
        int done = (int) items.stream().filter(TodoItem::isDone).count();
        progressBar.setProgress(total == 0 ? 0 : (double) done / total);
        if (total == 0) {
            statsLabel.setText("还没有任务，添加一个吧");
        } else if (done == total) {
            statsLabel.setText("全部完成，太棒了！已完成 " + done + " / " + total);
        } else {
            statsLabel.setText("已完成 " + done + " / " + total
                    + "（" + Math.round(done * 100.0 / total) + "%）");
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}