package org.example.game;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

/**
 * 最高分与历史 Top5 榜单持久化（spec §4.1.8），基于 Preferences。
 * <p>
 * 根节点 {@code Preferences.userRoot().node("2048game")}；写入失败（受限环境）
 * 静默降级并记日志，不影响游戏运行（本局成绩仍可在内存中使用）。
 */
public class ScoreStore {

    private static final Logger LOGGER = Logger.getLogger(ScoreStore.class.getName());

    /** Preferences 根节点名。 */
    private static final String ROOT_NODE = "2048game";
    /** 存储结构版本号键。 */
    private static final String KEY_VERSION = "version";
    /** 历史最高分键。 */
    private static final String KEY_BEST = "best-score";
    /** 榜单键前缀。 */
    private static final String PREFIX_HISTORY = "history.";
    /** 榜单条目上限。 */
    private static final int HISTORY_SIZE = 5;

    private final Preferences prefs;

    /** 默认使用 {@code userRoot/2048game} 节点。 */
    public ScoreStore() {
        this(Preferences.userRoot().node(ROOT_NODE));
    }

    /** 指定 Preferences 节点（测试可注入独立节点，避免污染真实数据）。 */
    public ScoreStore(Preferences prefs) {
        this.prefs = prefs;
    }

    /** 读取历史最高分（无记录返回 0）。 */
    public int loadBestScore() {
        return prefs.getInt(KEY_BEST, 0);
    }

    /** 读取历史 Top5 榜单（不存在返回空列表）。 */
    public List<HistoryEntry> loadHistory() {
        List<HistoryEntry> list = new ArrayList<>();
        for (int i = 1; i <= HISTORY_SIZE; i++) {
            int score = prefs.getInt(PREFIX_HISTORY + i + ".score", 0);
            if (score > 0) {
                list.add(new HistoryEntry(
                        score,
                        prefs.getInt(PREFIX_HISTORY + i + ".size", 0),
                        prefs.getLong(PREFIX_HISTORY + i + ".date", 0L)));
            }
        }
        return list;
    }

    /**
     * 上报一局结束成绩：写入榜单并维护排序淘汰（按分数降序，最多 5 条，
     * 同分按时间新者优先）；若超过历史最佳则更新 best-score。
     *
     * @param finalScore 本局最终分数
     * @param size       棋盘尺寸
     */
    public void reportGameOver(int finalScore, int size) {
        List<HistoryEntry> history = new ArrayList<>(loadHistory());
        history.add(new HistoryEntry(finalScore, size, System.currentTimeMillis()));
        history.sort((a, b) -> a.score() != b.score()
                ? Integer.compare(b.score(), a.score())
                : Long.compare(b.date(), a.date()));
        if (history.size() > HISTORY_SIZE) {
            history = new ArrayList<>(history.subList(0, HISTORY_SIZE));
        }
        try {
            prefs.putInt(KEY_VERSION, 1);
            for (int i = 1; i <= HISTORY_SIZE; i++) {
                prefs.remove(PREFIX_HISTORY + i + ".score");
                prefs.remove(PREFIX_HISTORY + i + ".size");
                prefs.remove(PREFIX_HISTORY + i + ".date");
            }
            for (int i = 0; i < history.size(); i++) {
                HistoryEntry e = history.get(i);
                prefs.putInt(PREFIX_HISTORY + (i + 1) + ".score", e.score());
                prefs.putInt(PREFIX_HISTORY + (i + 1) + ".size", e.size());
                prefs.putLong(PREFIX_HISTORY + (i + 1) + ".date", e.date());
            }
            if (finalScore > loadBestScore()) {
                prefs.putInt(KEY_BEST, finalScore);
            }
            prefs.flush();
        } catch (Exception ex) {
            LOGGER.warning("最高分/榜单写入失败，已静默降级：" + ex);
        }
    }
}
