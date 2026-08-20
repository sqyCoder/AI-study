package org.example.gobang.logic.ai;

import org.example.gobang.model.Board;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 困难 AI：α-β 剪枝极小极大，深度 2 层。
 * 叶子估值 = 己方最高型分 − 对方最高型分；走法按启发式分降序排序（剪枝效率关键）；
 * long 超时标记 2 秒，到时截断返回当前最优；每次评估前校验候选点非空，杜绝越界。
 */
public class HardAI implements AIStrategy {

    private static final int MAX_DEPTH = 2;
    private static final long TIME_LIMIT_MS = 2000;

    private final Random rnd = new Random();

    @Override
    public Point choose(Board board, int color) {
        List<Point> cands = AICandidate.generate(board);
        if (cands.isEmpty()) return new Point(7, 7);

        // 一步杀：己方可成五连，直接取胜
        Point win = findWin(board, cands, color);
        if (win != null) return win;

        long deadline = System.currentTimeMillis() + TIME_LIMIT_MS;
        List<Point> ordered = orderCandidates(board, cands, color);

        Point bestMove = null;
        int bestValue = Integer.MIN_VALUE;
        for (Point m : ordered) {
            if (System.currentTimeMillis() > deadline) break;
            board.place(m.row, m.col, color);
            int v = -negamax(board, 3 - color, MAX_DEPTH - 1,
                    Integer.MIN_VALUE + 1000, Integer.MAX_VALUE - 1000, deadline);
            board.removeLast();
            if (System.currentTimeMillis() > deadline) break; // 截断：丢弃半途而废的估值
            if (v > bestValue) {
                bestValue = v;
                bestMove = m;
            }
        }
        if (bestMove == null) {
            bestMove = ordered.isEmpty() ? new Point(7, 7) : ordered.get(0);
        }
        return bestMove;
    }

    /** 负极大搜索：返回当前局面下轮到 turn 方的最佳分值（已按负号折算）。 */
    private int negamax(Board board, int turn, int depth, int alpha, int beta, long deadline) {
        if (System.currentTimeMillis() > deadline) return 0;
        if (depth <= 0) return evaluate(board, turn);

        List<Point> cands = AICandidate.generate(board);
        if (cands.isEmpty()) return 0;

        List<Point> ordered = orderCandidates(board, cands, turn);
        int best = Integer.MIN_VALUE + 1000;
        for (Point m : ordered) {
            if (System.currentTimeMillis() > deadline) break;
            board.place(m.row, m.col, turn);
            int v = -negamax(board, 3 - turn, depth - 1, -beta, -alpha, deadline);
            board.removeLast();
            if (v > best) best = v;
            if (best > alpha) alpha = best;
            if (alpha >= beta) break; // β 剪枝
        }
        return best;
    }

    /** 静态估值：轮到 turn 方，其最佳进攻分 − 对方最佳进攻分。 */
    private int evaluate(Board board, int turn) {
        List<Point> cands = AICandidate.generate(board);
        if (cands.isEmpty()) return 0;
        int self = 0;
        int opp = 0;
        for (Point p : cands) {
            self = Math.max(self, ScoreEvaluator.scoreMove(board, p.row, p.col, turn));
            opp = Math.max(opp, ScoreEvaluator.scoreMove(board, p.row, p.col, 3 - turn));
        }
        return self - opp;
    }

    private Point findWin(Board board, List<Point> cands, int color) {
        for (Point p : cands) {
            if (ScoreEvaluator.scoreMove(board, p.row, p.col, color) >= ScoreEvaluator.FIVE) {
                return p;
            }
        }
        return null;
    }

    /** 按启发式分（攻防合成）降序排序；同分按预生成随机扰动打破平局（比较器必须确定性且可传递，否则 TimSort 抛异常）。 */
    private List<Point> orderCandidates(Board board, List<Point> cands, int color) {
        List<Point> list = new ArrayList<>(cands);
        java.util.Map<Point, Double> jitter = new java.util.HashMap<>();
        for (Point p : list) {
            jitter.put(p, rnd.nextDouble());
        }
        list.sort((a, b) -> {
            int sa = ScoreEvaluator.combinedScore(board, a.row, a.col, color);
            int sb = ScoreEvaluator.combinedScore(board, b.row, b.col, color);
            if (sa != sb) return Integer.compare(sb, sa);
            int byJitter = Double.compare(jitter.get(a), jitter.get(b));
            if (byJitter != 0) return byJitter;
            return Integer.compare(a.row * 100 + a.col, b.row * 100 + b.col);
        });
        return list;
    }
}