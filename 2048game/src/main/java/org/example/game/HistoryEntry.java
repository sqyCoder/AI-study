package org.example.game;

/**
 * 历史榜单条目：分数 + 棋盘尺寸 + 达成时间（epochMillis）。
 */
public record HistoryEntry(int score, int size, long date) {
}