package org.example.game;

/**
 * 移动方向枚举（spec §4.1.1）。
 * <p>
 * 提供坐标变换辅助：把"任意方向的滑动"统一等价为"向左滑动"。
 * 约定：transform 得到的变换棋盘 T 中第 r 行，即原棋盘沿该方向的一条"列/行"序列，
 * 且行内从左到右的顺序与原方向上的滑动顺序一致；inverseTransform 将其还原。
 */
public enum Direction {
    UP, DOWN, LEFT, RIGHT;

    /**
     * 变换棋盘：T[r][c] = grid[originRow(r,c)][originCol(r,c)]。
     * 变换后对每行执行向左滑动，等价于原棋盘向本方向滑动。
     */
    public Tile[][] transform(Tile[][] grid) {
        int n = grid.length;
        Tile[][] t = new Tile[n][n];
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                t[r][c] = grid[originRow(r, c, n)][originCol(r, c, n)];
            }
        }
        return t;
    }

    /** 逆变换：把滑动后的变换棋盘还原回原棋盘。 */
    public Tile[][] inverseTransform(Tile[][] t) {
        int n = t.length;
        Tile[][] grid = new Tile[n][n];
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                grid[originRow(r, c, n)][originCol(r, c, n)] = t[r][c];
            }
        }
        return grid;
    }

    /** 变换棋盘坐标 (r, c) 在原棋盘中的行号；n 为棋盘边长。 */
    public int originRow(int r, int c, int n) {
        return switch (this) {
            case UP -> c;
            case DOWN -> n - 1 - c;
            case LEFT, RIGHT -> r;
        };
    }

    /** 变换棋盘坐标 (r, c) 在原棋盘中的列号；n 为棋盘边长。 */
    public int originCol(int r, int c, int n) {
        return switch (this) {
            case UP, DOWN -> r;
            case LEFT -> c;
            case RIGHT -> n - 1 - c;
        };
    }
}
