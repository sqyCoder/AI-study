package org.example.ui;

/**
 * 棋盘布局常量与计算工具（spec §4.5）。
 * 棋盘保持正方形：boardSize = min(可用宽, 可用高) - 2×PADDING；
 * 格宽 = (boardSize - (N+1)×GAP) / N。
 */
public final class BoardLayout {

    /** 棋盘外边距（spec2：由 10 增大到 14，配合卡片留白） */
    public static final double PADDING = 14;
    /** 格子间距（spec2：由 10 增大到 12，更透气） */
    public static final double GAP = 12;

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

    /**
     * 第 col 列格子的左边缘 x 坐标（方块层绝对定位用）。
     * 棋盘内布局：左缘留 1 个 GAP，格子间距 GAP。
     * 底格层与方块层共用同一公式，两图层必然重合。
     */
    public static double cellX(int col, double cellSize) {
        return GAP + col * (cellSize + GAP);
    }

    /** 第 row 行格子的上边缘 y 坐标。 */
    public static double cellY(int row, double cellSize) {
        return GAP + row * (cellSize + GAP);
    }

    /**
     * 由规模与格宽反推棋盘边长：(N+1)×GAP + N×cellSize，
     * 与 cellSize 公式互逆（boardSize = min(宽,高) - 2×PADDING）。
     * 用于设置方块层/底格层尺寸，保证两图层完全重合。
     */
    public static double boardSide(int n, double cellSize) {
        return (n + 1) * GAP + n * cellSize;
    }

    /**
     * 棋盘内容在（被 StackPane 拉伸的）容器层内的水平偏移，用于居中：
     * (层所在区域宽 − 棋盘边长) / 2，最小为 0（棋盘大于层时贴边不越界）。
     */
    public static double boardX(double layerWidth, double boardSide) {
        return Math.max(0, (layerWidth - boardSide) / 2);
    }

    /** 垂直方向的居中偏移，同 {@link #boardX}。 */
    public static double boardY(double layerHeight, double boardSide) {
        return Math.max(0, (layerHeight - boardSide) / 2);
    }
}