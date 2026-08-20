package org.example.gobang.logic;

import org.example.gobang.model.Board;
import org.example.gobang.model.Move;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WinCheckerTest {

    private void place(Board b, int r, int c, int color) {
        assertTrue(b.place(r, c, color), "place(" + r + "," + c + ") failed");
    }

    private void line(Board b, int r0, int c0, int dr, int dc, int n, int color) {
        for (int i = 0; i < n; i++) {
            place(b, r0 + i * dr, c0 + i * dc, color);
        }
    }

    private List<Move> win(Board b) {
        return WinChecker.checkWin(b, b.getLastMove());
    }

    private void assertWin(Board b) {
        List<Move> line = win(b);
        assertNotNull(line);
        assertTrue(line.size() >= 5);
        Move last = b.getLastMove();
        assertTrue(line.stream().anyMatch(m -> m.row == last.row && m.col == last.col));
    }

    private void assertNoWin(Board b) {
        assertNull(win(b));
    }

    /** 用例 1：横向五连。 */
    @Test
    void horizontalWin() {
        Board b = new Board();
        line(b, 7, 3, 0, 1, 5, Board.BLACK);
        assertWin(b);
    }

    /** 用例 2：纵向五连。 */
    @Test
    void verticalWin() {
        Board b = new Board();
        line(b, 3, 7, 1, 0, 5, Board.BLACK);
        assertWin(b);
    }

    /** 用例 3：斜向（↖↘）五连。 */
    @Test
    void diagonalWin() {
        Board b = new Board();
        line(b, 3, 3, 1, 1, 5, Board.BLACK);
        assertWin(b);
    }

    /** 用例 4：五连位于行中间（最后一手补齐中间点）。 */
    @Test
    void winInMiddleOfRow() {
        Board b = new Board();
        place(b, 7, 3, Board.BLACK);
        place(b, 7, 4, Board.BLACK);
        place(b, 7, 6, Board.BLACK);
        place(b, 7, 7, Board.BLACK);
        place(b, 7, 5, Board.BLACK); // 最后一手在中间
        assertWin(b);
    }

    /** 用例 5：六连也算胜（长连胜）。 */
    @Test
    void longLineWins() {
        Board b = new Board();
        line(b, 10, 0, 0, 1, 6, Board.WHITE);
        assertWin(b);
    }

    /** 用例 6：四连不算胜。 */
    @Test
    void fourIsNotWin() {
        Board b = new Board();
        line(b, 7, 3, 0, 1, 4, Board.BLACK);
        assertNoWin(b);
    }

    /** 用例 7：斜向（↙↗）五连。 */
    @Test
    void antiDiagonalWin() {
        Board b = new Board();
        line(b, 7, 3, -1, 1, 5, Board.BLACK);
        assertWin(b);
    }

    /** 用例 8：棋盘边界起始的五连。 */
    @Test
    void winStartingAtBoundary() {
        Board b = new Board();
        line(b, 0, 0, 0, 1, 5, Board.BLACK);
        assertWin(b);
        Board b2 = new Board();
        line(b2, 0, 0, 1, 0, 5, Board.BLACK);
        assertWin(b2);
    }

    /** 用例 10：空盘不判胜。 */
    @Test
    void emptyBoardNoWin() {
        Board b = new Board();
        assertNull(WinChecker.checkWin(b, null));
        assertNull(win(b));
    }

    /** 用例 11：悔棋回滚后重判（去掉最后一手即不再成五连）。 */
    @Test
    void undoRollbackRecheck() {
        Board b = new Board();
        line(b, 7, 3, 0, 1, 4, Board.BLACK);
        assertNoWin(b);
        place(b, 7, 7, Board.BLACK);
        assertWin(b);
        b.removeLast();
        assertNoWin(b);
        assertNull(win(b));
    }

    /** 用例 12：双五同时成立（最后一手同时补齐横竖两条五连）。 */
    @Test
    void doubleFiveSimultaneously() {
        Board b = new Board();
        place(b, 7, 3, Board.BLACK);
        place(b, 7, 4, Board.BLACK);
        place(b, 7, 6, Board.BLACK);
        place(b, 7, 7, Board.BLACK);
        place(b, 5, 5, Board.BLACK);
        place(b, 6, 5, Board.BLACK);
        place(b, 8, 5, Board.BLACK);
        place(b, 9, 5, Board.BLACK);
        place(b, 7, 5, Board.BLACK); // 同时补横(3..7)与竖(5..9)
        List<Move> line = win(b);
        assertNotNull(line);
        assertEquals(5, line.size());
        assertTrue(line.stream().anyMatch(m -> m.row == 7 && m.col == 5));
    }
}