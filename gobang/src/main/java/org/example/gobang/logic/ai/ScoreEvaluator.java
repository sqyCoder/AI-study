package org.example.gobang.logic.ai;

import org.example.gobang.model.Board;

/**
 * 棋型评分表：
 * <pre>
 *   连五   1_000_000
 *   活四   100_000    冲四   10_000
 *   活三    8_000     眠三    1_000
 *   活二      500     眠二      100
 * </pre>
 * 每格得分 = max(己方四方向得分)；攻防合成：总 = 己方分 + 对方分 × 0.9。
 */
public final class ScoreEvaluator {

    public static final int FIVE = 1_000_000;
    public static final int LIVE_FOUR = 100_000;
    public static final int RUSH_FOUR = 10_000;
    public static final int LIVE_THREE = 8_000;
    public static final int SLEEP_THREE = 1_000;
    public static final int LIVE_TWO = 500;
    public static final int SLEEP_TWO = 100;

    private static final int[][] DIRS = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};

    private ScoreEvaluator() {
    }

    /** 在空位 (r,c) 模拟落 color，取 4 方向中最高棋型分。 */
    public static int scoreMove(Board board, int r, int c, int color) {
        int best = 0;
        for (int[] d : DIRS) {
            best = Math.max(best, lineScore(board, r, c, color, d[0], d[1]));
        }
        return best;
    }

    /** 攻防合成分：己方攻分 + 对方守分×0.9（防比攻略轻）。 */
    public static int combinedScore(Board board, int r, int c, int myColor) {
        int self = scoreMove(board, r, c, myColor);
        int enemy = scoreMove(board, r, c, 3 - myColor);
        return self + enemy * 9 / 10;
    }

    private static int lineScore(Board board, int r, int c, int color, int dr, int dc) {
        int count = 1;
        int r1 = r - dr;
        int c1 = c - dc;
        while (board.inBounds(r1, c1) && board.get(r1, c1) == color) {
            count++;
            r1 -= dr;
            c1 -= dc;
        }
        int r2 = r + dr;
        int c2 = c + dc;
        while (board.inBounds(r2, c2) && board.get(r2, c2) == color) {
            count++;
            r2 += dr;
            c2 += dc;
        }
        if (count >= 5) return FIVE;
        boolean open1 = board.inBounds(r1, c1) && board.get(r1, c1) == Board.EMPTY;
        boolean open2 = board.inBounds(r2, c2) && board.get(r2, c2) == Board.EMPTY;
        int open = (open1 ? 1 : 0) + (open2 ? 1 : 0);
        if (count == 4) return open == 2 ? LIVE_FOUR : open == 1 ? RUSH_FOUR : 0;
        if (count == 3) return open == 2 ? LIVE_THREE : open == 1 ? SLEEP_THREE : 0;
        if (count == 2) return open == 2 ? LIVE_TWO : open == 1 ? SLEEP_TWO : 0;
        return 0;
    }
}