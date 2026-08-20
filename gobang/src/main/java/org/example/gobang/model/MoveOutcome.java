package org.example.gobang.model;

import java.util.Collections;
import java.util.List;

/** 一次落子的结果。 */
public final class MoveOutcome {
    public enum Type { NONE, WIN, DRAW }

    public final Type type;
    /** 五连（或以上）的坐标列表，仅 WIN 时非 null */
    public final List<Move> winLine;

    private MoveOutcome(Type type, List<Move> winLine) {
        this.type = type;
        this.winLine = winLine;
    }

    public static MoveOutcome none() {
        return new MoveOutcome(Type.NONE, null);
    }

    public static MoveOutcome win(List<Move> winLine) {
        return new MoveOutcome(Type.WIN, winLine == null ? null : Collections.unmodifiableList(winLine));
    }

    public static MoveOutcome draw() {
        return new MoveOutcome(Type.DRAW, null);
    }
}
