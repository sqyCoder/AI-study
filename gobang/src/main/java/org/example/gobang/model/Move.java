package org.example.gobang.model;

/** 一步落子记录。 */
public final class Move {
    public final int row;
    public final int col;
    public final int color; // Board.BLACK / Board.WHITE
    public final int seq;   // 第几手，从 1 开始

    public Move(int row, int col, int color, int seq) {
        this.row = row;
        this.col = col;
        this.color = color;
        this.seq = seq;
    }

    /** 值相等：四字段全同（联机两侧历史一致性比对依赖此语义）。 */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Move m)) return false;
        return row == m.row && col == m.col && color == m.color && seq == m.seq;
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + row;
        result = 31 * result + col;
        result = 31 * result + color;
        result = 31 * result + seq;
        return result;
    }
}
