package org.example.gobang.logic.ai;

import org.example.gobang.model.Board;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 候选点生成：遍历所有已落子，取其切比雪夫距离 ≤2 的空格，去重。
 * 超过 30 个时按「离中心近优先」截断，但保证不丢关键点：
 * 任何「下子即成五连」或「对手下子即成五连」的点（必胜/必堵点）永远保留。
 */
public final class AICandidate {

    private static final int MAX_CANDIDATES = 30;
    private static final int[][] DIRS = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};

    private AICandidate() {
    }

    public static List<Point> generate(Board board) {
        Set<Long> seen = new HashSet<>();
        List<Point> out = new ArrayList<>();
        for (int r = 0; r < Board.SIZE; r++) {
            for (int c = 0; c < Board.SIZE; c++) {
                if (board.get(r, c) == Board.EMPTY) continue;
                for (int dr = -2; dr <= 2; dr++) {
                    for (int dc = -2; dc <= 2; dc++) {
                        if (dr == 0 && dc == 0) continue;
                        int nr = r + dr;
                        int nc = c + dc;
                        if (!board.inBounds(nr, nc)) continue;
                        if (board.get(nr, nc) != Board.EMPTY) continue;
                        long key = (long) nr * 100 + nc;
                        if (seen.add(key)) {
                            out.add(new Point(nr, nc));
                        }
                    }
                }
            }
        }
        if (out.isEmpty()) {
            // 空盘：AI 首手落天元
            return List.of(new Point(7, 7));
        }
        if (out.size() > MAX_CANDIDATES) {
            return truncate(board, out);
        }
        return out;
    }

    private static List<Point> truncate(Board board, List<Point> all) {
        List<Point> forced = new ArrayList<>();
        List<Point> rest = new ArrayList<>();
        for (Point p : all) {
            boolean f = wouldWin(board, p.row, p.col, Board.BLACK) || wouldWin(board, p.row, p.col, Board.WHITE);
            (f ? forced : rest).add(p);
        }
        // 离中心近优先
        rest.sort((a, b) -> Integer.compare(centerDist(a), centerDist(b)));
        // 必胜/必堵点全部保留（极端局面下可能超过 30，此时宁多勿漏）
        List<Point> result = new ArrayList<>(forced);
        for (Point p : rest) {
            if (result.size() >= MAX_CANDIDATES) break;
            result.add(p);
        }
        return result;
    }

    private static int centerDist(Point p) {
        int dr = p.row - 7;
        int dc = p.col - 7;
        return dr * dr + dc * dc;
    }

    /** (r,c) 处落 color 能否立刻成五连（不修改棋盘）。 */
    public static boolean wouldWin(Board board, int r, int c, int color) {
        for (int[] d : DIRS) {
            int count = 1;
            int rr = r - d[0];
            int cc = c - d[1];
            while (board.inBounds(rr, cc) && board.get(rr, cc) == color) {
                count++;
                rr -= d[0];
                cc -= d[1];
            }
            rr = r + d[0];
            cc = c + d[1];
            while (board.inBounds(rr, cc) && board.get(rr, cc) == color) {
                count++;
                rr += d[0];
                cc += d[1];
            }
            if (count >= 5) return true;
        }
        return false;
    }
}