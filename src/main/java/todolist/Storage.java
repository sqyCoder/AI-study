package todolist;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 任务数据持久化，纯文本格式。
 * 每行: "[x] 分类名 任务内容" 或 "[ ] 分类名 任务内容"。
 */
public final class Storage {

    private static final Path DEFAULT_FILE = Path.of(
            System.getProperty("user.home"), ".todolist-data.txt");

    private Storage() {
    }

    public static List<TodoItem> load() {
        return load(DEFAULT_FILE);
    }

    public static void save(List<TodoItem> items) {
        save(DEFAULT_FILE, items);
    }

    public static List<TodoItem> load(Path file) {
        List<TodoItem> items = new ArrayList<>();
        if (!Files.exists(file)) {
            return items;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                boolean done = line.startsWith("[x] ");
                if (!done && !line.startsWith("[ ] ")) {
                    continue;
                }
                String rest = line.substring(4);
                int separator = rest.indexOf(' ');
                if (separator < 0) {
                    continue;
                }
                Category category = Category.fromLabel(rest.substring(0, separator));
                String text = rest.substring(separator + 1);
                items.add(new TodoItem(text, done, category));
            }
        } catch (IOException e) {
            System.err.println("[todolist] 读取任务数据失败: " + e.getMessage());
        }
        return items;
    }

    public static void save(Path file, List<TodoItem> items) {
        List<String> lines = new ArrayList<>();
        for (TodoItem item : items) {
            lines.add((item.isDone() ? "[x] " : "[ ] ") + item.getCategory().getLabel() + " " + item.getText());
        }
        try {
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[todolist] 保存任务数据失败: " + e.getMessage());
        }
    }
}