package org.example.game;

/**
 * 单块移动记录：from 坐标 → to 坐标；isMerge=true 表示该记录是合并源块
 * （同一目标可能对应两条记录，两个源块同时飞向目标格；value 为合并后的新值）。
 */
public record TileMove(int fromRow, int fromCol, int toRow, int toCol, int value, boolean isMerge) {
}