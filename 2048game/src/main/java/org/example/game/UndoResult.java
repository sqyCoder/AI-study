package org.example.game;

/**
 * 一次撤销的结果：恢复后的棋盘与分数，以及是否"复活"（撤销前处于 gameOver/won 状态）。
 */
public record UndoResult(Tile[][] grid, int score, boolean revived) {
}