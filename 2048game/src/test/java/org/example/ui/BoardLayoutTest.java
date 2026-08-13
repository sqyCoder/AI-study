package org.example.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 布局公式测试（spec §4.5 / spec2 D7）：棋盘正方形、格宽公式、格子坐标一致性。
 * 锁定"底板 GridPane 与方块层绝对定位完全重合"这一关键约束。
 * spec2 起 PADDING=14、GAP=12，断言数值已同步重算。
 */
class BoardLayoutTest {

    @Test
    void 棋盘边长取宽高较小者减两倍边距() {
        assertEquals(372, BoardLayout.boardSize(500, 400), 1e-9);
        assertEquals(472, BoardLayout.boardSize(500, 500), 1e-9);
        assertEquals(272, BoardLayout.boardSize(300, 400), 1e-9);
    }

    @Test
    void 格宽公式与规格一致() {
        // boardSize(500,400)=372；N=4：cell=(372-5×12)/4=78
        assertEquals(78.0, BoardLayout.cellSize(500, 400, 4), 1e-9);
        // N=8：cell=(372-9×12)/8=33
        assertEquals(33.0, BoardLayout.cellSize(500, 400, 8), 1e-9);
        // N=3：cell=(372-4×12)/3=108
        assertEquals(108.0, BoardLayout.cellSize(500, 400, 3), 1e-9);
    }

    @Test
    void 格子坐标间距等于格宽加间距() {
        double cell = 78.0;
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
        // 与 cellSize 公式互逆：N=4、棋盘 372 → 格宽 78
        assertEquals(372, BoardLayout.boardSide(4, 78), 1e-9);
        assertEquals(390, BoardLayout.boardSide(4, 82.5), 1e-9);
        assertEquals(372, BoardLayout.boardSize(500, 400), 1e-9);
    }

    @Test
    void 小窗口下格宽仍为正() {
        // min 窗口 480×620 → 棋盘区 ~456×~500 → 8×8 格宽应 > 0
        double cell = BoardLayout.cellSize(456, 500, 8);
        assertTrue(cell > 0);
        assertTrue(cell > 30, "格宽不应过小，实际 " + cell);
    }
}