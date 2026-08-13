package org.example.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 布局公式测试（spec §4.5）：棋盘正方形、格宽公式、格子坐标一致性。
 * 锁定"底板 GridPane 与方块层绝对定位完全重合"这一关键约束。
 */
class BoardLayoutTest {

    @Test
    void 棋盘边长取宽高较小者减两倍边距() {
        assertEquals(380, BoardLayout.boardSize(500, 400), 1e-9);
        assertEquals(480, BoardLayout.boardSize(500, 500), 1e-9);
        assertEquals(280, BoardLayout.boardSize(300, 400), 1e-9);
    }

    @Test
    void 格宽公式与规格一致() {
        // boardSize(500,400)=380；N=4：cell=(380-5×10)/4=82.5
        assertEquals(82.5, BoardLayout.cellSize(500, 400, 4), 1e-9);
        // N=8：cell=(380-9×10)/8=36.25
        assertEquals(36.25, BoardLayout.cellSize(500, 400, 8), 1e-9);
        // N=3：cell=(380-4×10)/3≈113.33
        assertEquals(340.0 / 3, BoardLayout.cellSize(500, 400, 3), 1e-9);
    }

    @Test
    void 格子坐标间距等于格宽加间距() {
        double cell = 82.5;
        assertEquals(BoardLayout.GAP, BoardLayout.cellX(0, cell), 1e-9);
        for (int c = 0; c < 7; c++) {
            assertEquals(cell + BoardLayout.GAP,
                    BoardLayout.cellX(c + 1, cell) - BoardLayout.cellX(c, cell), 1e-9);
            assertEquals(cell + BoardLayout.GAP,
                    BoardLayout.cellY(c + 1, cell) - BoardLayout.cellY(c, cell), 1e-9);
        }
    }

    @Test
    void 棋盘边长与坐标范围一致() {
        for (int n : new int[]{3, 4, 8}) {
            double cell = 40;
            double board = BoardLayout.boardSide(n, cell);
            double last = BoardLayout.cellX(n - 1, cell) + cell;
            assertEquals(board - BoardLayout.GAP, last, 1e-9, "最后格子右缘应留 1 个 GAP（N=" + n + "）");
        }
        // 与 cellSize 公式互逆：N=4、棋盘 380 → 格宽 82.5
        assertEquals(380, BoardLayout.boardSide(4, 82.5), 1e-9);
        assertEquals(380, BoardLayout.boardSize(500, 400), 1e-9);
    }

    @Test
    void 小窗口下格宽仍为正() {
        // min 窗口 480×620 → 棋盘区 ~456×~500 → 8×8 格宽应 > 0
        double cell = BoardLayout.cellSize(456, 500, 8);
        assertTrue(cell > 0);
        assertTrue(cell > 30, "格宽不应过小，实际 " + cell);
    }
}
