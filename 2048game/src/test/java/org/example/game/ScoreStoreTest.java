package org.example.game;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ScoreStore 持久化测试：使用独立 Preferences 节点，避免污染真实数据。
 */
class ScoreStoreTest {

    private Preferences prefs;
    private ScoreStore store;

    @BeforeEach
    void setUp() {
        prefs = Preferences.userRoot().node("2048game-test-" + UUID.randomUUID());
        store = new ScoreStore(prefs);
    }

    @AfterEach
    void tearDown() throws Exception {
        prefs.removeNode();
    }

    @Test
    void 初始无最佳与榜单() {
        assertEquals(0, store.loadBestScore());
        assertTrue(store.loadHistory().isEmpty());
    }

    @Test
    void 上报成绩维护排序与截断() throws InterruptedException {
        store.reportGameOver(100, 4);
        store.reportGameOver(50, 3);
        store.reportGameOver(200, 8);
        Thread.sleep(5);
        store.reportGameOver(150, 4);
        store.reportGameOver(80, 4);
        Thread.sleep(5);
        store.reportGameOver(120, 4);

        List<HistoryEntry> history = store.loadHistory();
        assertEquals(5, history.size());
        assertEquals(200, history.get(0).score());
        assertEquals(150, history.get(1).score());
        assertEquals(120, history.get(2).score());
        assertEquals(100, history.get(3).score());
        assertEquals(80, history.get(4).score());
        assertEquals(8, history.get(0).size());
        assertEquals(200, store.loadBestScore());
    }

    @Test
    void 最佳分数只增不减() throws InterruptedException {
        store.reportGameOver(500, 4);
        assertEquals(500, store.loadBestScore());
        Thread.sleep(5);
        store.reportGameOver(300, 8);
        assertEquals(500, store.loadBestScore());
        Thread.sleep(5);
        store.reportGameOver(800, 4);
        assertEquals(800, store.loadBestScore());
    }

    @Test
    void 同分按时间新者优先() throws InterruptedException {
        store.reportGameOver(100, 4);
        Thread.sleep(5);
        store.reportGameOver(100, 8);
        List<HistoryEntry> history = store.loadHistory();
        assertEquals(2, history.size());
        assertEquals(8, history.get(0).size());
        assertEquals(4, history.get(1).size());
    }
}
