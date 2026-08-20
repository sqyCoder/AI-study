package org.example.gobang.logic.ai;

import org.example.gobang.model.Board;

import java.util.List;
import java.util.Random;

/** 中等 AI：对全部候选点算攻防总分，取最高（同分随机打破平局）。 */
public class MediumAI implements AIStrategy {

    private final Random rnd = new Random();

    @Override
    public Point choose(Board board, int color) {
        List<Point> cands = AICandidate.generate(board);
        if (cands.isEmpty()) return new Point(7, 7);

        Point best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Point p : cands) {
            int s = ScoreEvaluator.combinedScore(board, p.row, p.col, color);
            if (s > bestScore || (s == bestScore && rnd.nextBoolean())) {
                bestScore = s;
                best = p;
            }
        }
        return best;
    }
}