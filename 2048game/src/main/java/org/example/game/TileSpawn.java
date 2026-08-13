package org.example.game;

/**
 * 新生成方块的位置与值（90% 为 2，10% 为 4）。
 */
public record TileSpawn(int row, int col, int value) {
}