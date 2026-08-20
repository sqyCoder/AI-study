package org.example.gobang.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BoardTest {

    @Test
    void placeAndGet() {
        Board b = new Board();
        assertTrue(b.place(0, 0, Board.BLACK));
        assertEquals(Board.BLACK, b.get(0, 0));
        assertEquals(1, b.getHistory().size());
        assertEquals(1, b.getLastMove().seq);
    }

    @Test
    void placeRejected() {
        Board b = new Board();
        assertTrue(b.place(7, 7, Board.BLACK));
        assertFalse(b.place(7, 7, Board.WHITE));   // 已占
        assertFalse(b.place(-1, 0, Board.BLACK));  // 越界
        assertFalse(b.place(15, 15, Board.BLACK)); // 越界
        assertFalse(b.place(0, 0, 9));             // 非法颜色
    }

    @Test
    void removeLast() {
        Board b = new Board();
        assertNull(b.removeLast());
        b.place(0, 0, Board.BLACK);
        b.place(0, 1, Board.WHITE);
        Move m = b.removeLast();
        assertEquals(0, m.row);
        assertEquals(1, m.col);
        assertEquals(Board.WHITE, m.color);
        assertTrue(b.isEmpty(0, 1));
        assertFalse(b.isEmpty(0, 0));
        assertEquals(1, b.getHistory().size());
    }

    /** 用例 9：满盘判定。用无五连模式的 4 色循环填满 225 格。 */
    @Test
    void isFull() {
        Board b = new Board();
        assertFalse(b.isFull());
        for (int r = 0; r < Board.SIZE; r++) {
            for (int c = 0; c < Board.SIZE; c++) {
                int color = ((r * 2 + c) % 4 < 2) ? Board.BLACK : Board.WHITE;
                assertTrue(b.place(r, c, color));
            }
        }
        assertTrue(b.isFull());
    }
}