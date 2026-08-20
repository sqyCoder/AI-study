package org.example.gobang.logic;

import org.example.gobang.model.Board;
import org.example.gobang.model.Move;

import java.util.ArrayList;
import java.util.List;

/** 胜负判定：对最后落子点做 4 方向延伸检测，长连也算胜。 */
public final class WinChecker {

    private static final int[][] DIRS = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};

    private WinChecker() {
    }

    /**
     * 检查最近一手是否构成五连（或以上）。
     *
     * @return 该方向上的整条连子（≥5 个坐标），供高亮动画；无胜返回 null。
     */
    public static List<Move> checkWin(Board board, Move last) {
        if (last == null) return null;
        for (int[] d : DIRS) {
            List<Move> line = new ArrayList<>();
            int r = last.row - d[0];
            int c = last.col - d[1];
            while (board.inBounds(r, c) && board.get(r, c) == last.color) {
                line.add(new Move(r, c, last.color, 0));
                r -= d[0];
                c -= d[1];
            }
            line.add(last);
            r = last.row + d[0];
            c = last.col + d[1];
            while (board.inBounds(r, c) && board.get(r, c) == last.color) {
                line.add(new Move(r, c, last.color, 0));
                r += d[0];
                c += d[1];
            }
            if (line.size() >= 5) return line;
        }
        return null;
    }
}
