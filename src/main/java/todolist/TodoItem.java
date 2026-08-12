package todolist;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class TodoItem {

    private final StringProperty text = new SimpleStringProperty();
    private final BooleanProperty done = new SimpleBooleanProperty();
    private final ObjectProperty<Category> category = new SimpleObjectProperty<>();

    public TodoItem(String text, boolean done, Category category) {
        this.text.set(text);
        this.done.set(done);
        this.category.set(category);
    }

    public StringProperty textProperty() {
        return text;
    }

    public BooleanProperty doneProperty() {
        return done;
    }

    public ObjectProperty<Category> categoryProperty() {
        return category;
    }

    public String getText() {
        return text.get();
    }

    public boolean isDone() {
        return done.get();
    }

    public Category getCategory() {
        return category.get();
    }

    public void setText(String text) {
        this.text.set(text);
    }

    public void setDone(boolean done) {
        this.done.set(done);
    }

    public void setCategory(Category category) {
        this.category.set(category);
    }
}