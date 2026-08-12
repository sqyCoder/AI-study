package todolist;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageTest {

    @Test
    void saveAndLoadRoundTrip() throws Exception {
        Path file = Files.createTempFile("todolist", ".txt");
        try {
            List<TodoItem> source = List.of(
                    new TodoItem("买牛奶并顺便取快递", false, Category.URGENT_IMPORTANT),
                    new TodoItem("写周报", true, Category.IMPORTANT_NOT_URGENT),
                    new TodoItem("回复加急邮件", false, Category.URGENT_NOT_IMPORTANT)
            );
            Storage.save(file, source);

            List<TodoItem> loaded = Storage.load(file);
            assertEquals(3, loaded.size());
            assertEquals("买牛奶并顺便取快递", loaded.get(0).getText());
            assertFalse(loaded.get(0).isDone());
            assertEquals(Category.URGENT_IMPORTANT, loaded.get(0).getCategory());
            assertTrue(loaded.get(1).isDone());
            assertEquals(Category.IMPORTANT_NOT_URGENT, loaded.get(1).getCategory());
            assertEquals(Category.URGENT_NOT_IMPORTANT, loaded.get(2).getCategory());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void loadMissingFileReturnsEmpty() throws Exception {
        Path file = Files.createTempFile("todolist-nonexistent", ".txt");
        try {
            Files.deleteIfExists(file);
            assertTrue(Storage.load(file).isEmpty());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void loadIgnoresMalformedLines() throws Exception {
        Path file = Files.createTempFile("todolist-malformed", ".txt");
        try {
            Files.writeString(file, "垃圾行\n[ ] 紧急重要\n[x] 未知分类 有效任务\n");
            List<TodoItem> loaded = Storage.load(file);
            assertEquals(1, loaded.size());
            assertEquals("有效任务", loaded.get(0).getText());
            assertEquals(Category.URGENT_IMPORTANT, loaded.get(0).getCategory());
            assertTrue(loaded.get(0).isDone());
        } finally {
            Files.deleteIfExists(file);
        }
    }
}