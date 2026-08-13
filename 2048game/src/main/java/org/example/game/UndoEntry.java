package org.example.game;

/**
 * 撤销栈中的一条快照：棋盘 + 分数。
 */
public record UndoEntry(Tile[][] grid, int score) {
}