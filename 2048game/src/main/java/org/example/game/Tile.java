package org.example.game;

/**
 * 棋盘方块（spec §4.1.2）。
 * <p>
 * value 为 0 表示空格，否则必为 2 的幂；merged 标记本块在本步内是否已参与合并，
 * 用于保证"每块每步最多合并一次"，仅在一次移动过程中有效，移动结束后复位。
 */
public record Tile(int value, boolean merged) {

    /** 空格常量（value=0）。 */
    public static final Tile EMPTY = new Tile(0, false);

    /** 是否为空格。 */
    public boolean isEmpty() {
        return value == 0;
    }
}
