package org.example.ui;

/**
 * 棋盘布局常量与计算工具（spec §4.5）。
 * 棋盘保持正方形：boardSize = min(可用宽, 可用高) - 2×PADDING；
 * 格宽 = (boardSize - (N+1)×GAP) / N。
 */
public final class BoardLayout {

    /** 棋盘外边距 */
    public static final double PADDING = 10;
    /** 格子间距 */
    public static final double GAP = 10;

    private BoardLayout() {
        // 工具类，禁止实例化
    }

    /** 由容器可用宽高计算棋盘边长：min(宽,高) - 2×PADDING */
    public static double boardSize(double areaWidth, double areaHeight) {
        return Math.min(areaWidth, areaHeight) - 2 * PADDING;
    }

    /** 由容器可用宽高与规模 N 计算格宽：(boardSize - (N+1)×GAP) / N */
    public static double cellSize(double areaWidth, double areaHeight, int n) {
        double board = boardSize(areaWidth, areaHeight);
        return (board - (n + 1) * GAP) / n;
    }
}