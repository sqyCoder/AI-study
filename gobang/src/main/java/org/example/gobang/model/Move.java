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
}
