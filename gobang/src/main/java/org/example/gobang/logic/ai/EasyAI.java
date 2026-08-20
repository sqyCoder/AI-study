package org.example.gobang.logic.ai;

import org.example.gobang.model.Board;

import java.util.List;
import java.util.Random;

/**
 * 简单 AI：检测对手「四且一端空」必堵；否则 60% 随机合法点、40% 取己方单点最高分。
 * 自身可成五连时优先取胜。
 */
public class EasyAI implements AIStrategy {

    private final Random rnd = new Random();

    @Override
    public Point choose(Board board, int color) {
        List<Point> cands = AICandidate.generate(board);
        if (cands.isEmpty()) return new Point(7, 7);

        // 1. 己方可成五连 → 直接取胜
        Point win = bestByScore(board, cands, color, ScoreEvaluator.FIVE);
        if (win != null) return win;

        // 2. 对手「四」（冲四/活四）→ 必堵
        Point block = bestByScore(board, cands, 3 - color, ScoreEvaluator.RUSH_FOUR);
        if (block != null) return block;

        // 3. 60% 随机合法点，40% 己方单点最高分
        if (rnd.nextDouble() < 0.6) {
            return cands.get(rnd.nextInt(cands.size()));
        }
        return bestOwn(board, cands, color);
    }

    private Point bestByScore(Board board, List<Point> cands, int color, int threshold) {
        Point best = null;
        int bestScore = -1;
        for (Point p : cands) {
            int s = ScoreEvaluator.scoreMove(board, p.row, p.col, color);
            if (s >= threshold && s > bestScore) {
                bestScore = s;
                best = p;
            }
        }
        return best;
    }

    private Point bestOwn(Board board, List<Point> cands, int color) {
        Point best = null;
        int bestScore = -1;
        for (Point p : cands) {
            int s = ScoreEvaluator.scoreMove(board, p.row, p.col, color);
            if (s > bestScore || (s == bestScore && rnd.nextBoolean())) {
                bestScore = s;
                best = p;
            }
        }
        return best;
    }
}