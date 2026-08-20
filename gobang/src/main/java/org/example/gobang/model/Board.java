package org.example.gobang.model;

import java.util.ArrayList;
import java.util.List;

/** 15×15 棋盘。纯逻辑，不依赖 JavaFX。 */
public class Board {
    public static final int SIZE = 15;
    public static final int EMPTY = 0;
    public static final int BLACK = 1;
    public static final int WHITE = 2;

    private final int[][] cells = new int[SIZE][SIZE];
    private final List<Move> history = new ArrayList<>();

    public int get(int r, int c) {
        return cells[r][c];
    }

    public boolean inBounds(int r, int c) {
        return r >= 0 && r < SIZE && c >= 0 && c < SIZE;
    }

    public boolean isEmpty(int r, int c) {
        return inBounds(r, c) && cells[r][c] == EMPTY;
    }

    public boolean isOccupied(int r, int c) {
        return inBounds(r, c) && cells[r][c] != EMPTY;
    }

    /** 落子。非法（越界/已占/颜色非法）返回 false。 */
    public boolean place(int r, int c, int color) {
        if (!inBounds(r, c) || cells[r][c] != EMPTY) return false;
        if (color != BLACK && color != WHITE) return false;
        cells[r][c] = color;
        history.add(new Move(r, c, color, history.size() + 1));
        return true;
    }

    /** 悔棋：弹出最后一手并清空该格。无历史时返回 null。 */
    public Move removeLast() {
        if (history.isEmpty()) return null;
        Move m = history.remove(history.size() - 1);
        cells[m.row][m.col] = EMPTY;
        return m;
    }

    public boolean isFull() {
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (cells[r][c] == EMPTY) return false;
            }
        }
        return true;
    }

    public List<Move> getHistory() {
        return history;
    }

    /** 深拷贝：供 AI 后台线程在快照上搜索，杜绝与 FX 线程的并发修改。 */
    public Board copy() {
        Board b = new Board();
        for (int r = 0; r < SIZE; r++) {
            System.arraycopy(cells[r], 0, b.cells[r], 0, SIZE);
        }
        b.history.addAll(history);
        return b;
    }

    public Move getLastMove() {
        return history.isEmpty() ? null : history.get(history.size() - 1);
    }

    public void clear() {
        for (int r = 0; r < SIZE; r++) {
            java.util.Arrays.fill(cells[r], EMPTY);
        }
        history.clear();
    }
}
