package org.example.gobang.logic.ai;

import org.example.gobang.logic.GameSession;
import org.example.gobang.model.Board;
import org.example.gobang.model.MoveOutcome;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class AITest {

    private final Random rnd = new Random(42);

    private void place(Board b, int r, int c, int color) {
        assertTrue(b.place(r, c, color));
    }

    private boolean legal(Board b, Point p) {
        return p != null && b.inBounds(p.row, p.col) && b.isEmpty(p.row, p.col);
    }

    /** 用例 13：候选点包含必胜点（对手四连的开端）。 */
    @Test
    void candidatesContainWinningPoint() {
        Board b = new Board();
        place(b, 7, 3, Board.WHITE);
        place(b, 7, 4, Board.WHITE);
        place(b, 7, 5, Board.WHITE);
        place(b, 7, 6, Board.WHITE); // 白四连，两端 (7,2)(7,7) 可成五
        List<Point> cands = AICandidate.generate(b);
        assertTrue(cands.stream().anyMatch(p -> p.row == 7 && p.col == 2));
        assertTrue(cands.stream().anyMatch(p -> p.row == 7 && p.col == 7));
        // 不包含已占格
        assertTrue(cands.stream().noneMatch(p -> b.isOccupied(p.row, p.col)));
    }

    /** 用例 14：EasyAI 必堵冲四/活四。 */
    @Test
    void easyAIBlocksFour() {
        Board b = new Board();
        place(b, 7, 3, Board.WHITE);
        place(b, 7, 4, Board.WHITE);
        place(b, 7, 5, Board.WHITE);
        place(b, 7, 6, Board.WHITE);
        EasyAI ai = new EasyAI();
        Point p = ai.choose(b, Board.BLACK);
        assertTrue(legal(b, p));
        boolean isEnd = (p.row == 7 && (p.col == 2 || p.col == 7));
        // 4 次尝试应至少有 1 次堵住（防随机 60% 干扰，但堵点优先级最高应全中）
        assertTrue(isEnd, "EasyAI 未堵四连，落子=" + p);
    }

    /** 用例 14b：EasyAI 自己可成五连时先取胜。 */
    @Test
    void easyAIWinsWhenPossible() {
        Board b = new Board();
        place(b, 7, 3, Board.BLACK);
        place(b, 7, 4, Board.BLACK);
        place(b, 7, 5, Board.BLACK);
        place(b, 7, 6, Board.BLACK);
        EasyAI ai = new EasyAI();
        Point p = ai.choose(b, Board.BLACK);
        assertTrue(p.row == 7 && (p.col == 2 || p.col == 7));
    }

    /** 用例 15：EasyAI 永不越界/永不返回已占格。 */
    @Test
    void easyAINeverIllegal() {
        EasyAI ai = new EasyAI();
        for (int i = 0; i < 50; i++) {
            Board b = randomBoard(10 + rnd.nextInt(30));
            Point p = ai.choose(b, Board.BLACK);
            assertTrue(legal(b, p), "非法落子=" + p);
            Point p2 = ai.choose(b, Board.WHITE);
            assertTrue(legal(b, p2), "非法落子=" + p2);
        }
    }

    /** 用例 16：MediumAI 选必胜点（一步成五连）。 */
    @Test
    void mediumAIPicksWinningMove() {
        Board b = new Board();
        place(b, 3, 3, Board.WHITE);
        place(b, 3, 4, Board.WHITE);
        place(b, 3, 5, Board.WHITE);
        place(b, 3, 6, Board.WHITE);
        MediumAI ai = new MediumAI();
        Point p = ai.choose(b, Board.WHITE);
        assertTrue(p.row == 3 && (p.col == 2 || p.col == 7), "未选必胜点=" + p);
    }

    /** 用例 17：HardAI 一步杀。 */
    @Test
    void hardAIOneMoveKill() {
        Board b = new Board();
        place(b, 3, 3, Board.BLACK);
        place(b, 3, 4, Board.BLACK);
        place(b, 3, 5, Board.BLACK);
        place(b, 3, 6, Board.BLACK);
        HardAI ai = new HardAI();
        Point p = ai.choose(b, Board.BLACK);
        assertTrue(p.row == 3 && (p.col == 2 || p.col == 7), "未实现一步杀=" + p);
    }

    /** 用例 17b：HardAI 必堵对手冲四（轮到自己防守）。 */
    @Test
    void hardAIBlocksOpponentFour() {
        Board b = new Board();
        place(b, 7, 3, Board.WHITE);
        place(b, 7, 4, Board.WHITE);
        place(b, 7, 5, Board.WHITE);
        place(b, 7, 6, Board.WHITE);
        HardAI ai = new HardAI();
        Point p = ai.choose(b, Board.BLACK);
        assertTrue(p.row == 7 && (p.col == 2 || p.col == 7), "未堵四=" + p);
    }

    /** 用例 18：HardAI 时限内返回合法点。 */
    @Test
    void hardAIWithinTimeLimit() {
        HardAI ai = new HardAI();
        for (int i = 0; i < 5; i++) {
            Board b = randomBoard(10 + rnd.nextInt(30));
            long t0 = System.nanoTime();
            Point p = ai.choose(b, Board.BLACK);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            assertTrue(ms < 2500, "超时 " + ms + "ms");
            assertTrue(legal(b, p), "非法落子=" + p);
        }
    }

    /** 用例 19：三档 AI 永不返回已占格/越界。 */
    @Test
    void allAIsNeverIllegal() {
        AIStrategy[] ais = {new EasyAI(), new MediumAI(), new HardAI()};
        for (AIStrategy ai : ais) {
            for (int i = 0; i < 20; i++) {
                Board b = randomBoard(10 + rnd.nextInt(35));
                for (int color : new int[]{Board.BLACK, Board.WHITE}) {
                    Point p = ai.choose(b, color);
                    assertTrue(legal(b, p), ai.getClass().getSimpleName() + " 非法落子=" + p);
                }
            }
        }
    }

    /** 完整对局（AI 双方）必然在有限步内收敛到 FINISHED，不卡死。 */
    @Test
    void fullGameTerminates() {
        GameSession s = new GameSession(GameSession.Mode.PVE, GameSession.Difficulty.MEDIUM);
        s.applyGuess(false, true, 1, false);
        AIStrategy ai = new MediumAI();
        int guard = 0;
        while (s.getState() == org.example.gobang.model.GameState.PLAYING && guard < 300) {
            int color = s.getCurrentColor();
            Point p = ai.choose(s.board, color);
            assertTrue(legal(s.board, p), "非法落子=" + p);
            MoveOutcome oc = s.place(p.row, p.col, color);
            assertNotNull(oc);
            guard++;
        }
        assertTrue(guard < 300, "对局未在有限步内收敛");
        assertEquals(org.example.gobang.model.GameState.FINISHED, s.getState());
    }

    /** 稠密棋盘（>30 候选）下截断时不会丢弃必胜点（用例 13 的强化版）。 */
    @Test
    void truncationKeepsForcedMoves() {
        Board b = new Board();
        // 黑四连，两端 (7,2)(7,7) 可成五
        place(b, 7, 3, Board.BLACK);
        place(b, 7, 4, Board.BLACK);
        place(b, 7, 5, Board.BLACK);
        place(b, 7, 6, Board.BLACK);
        // 远离胜利线区域撒 70 颗随机子，确保候选数 > 30
        int placed = 0;
        while (placed < 70) {
            int r = rnd.nextInt(Board.SIZE);
            int c = rnd.nextInt(Board.SIZE);
            if (b.isEmpty(r, c) && Math.abs(r - 7) + Math.abs(c - 5) > 4) {
                b.place(r, c, rnd.nextBoolean() ? Board.BLACK : Board.WHITE);
                placed++;
            }
        }
        List<Point> cands = AICandidate.generate(b);
        assertTrue(cands.size() <= 30);
        assertTrue(cands.stream().anyMatch(p -> p.row == 7 && (p.col == 2 || p.col == 7)),
                "截断后丢失必胜点: " + cands);
        // 且无重复点
        assertEquals(cands.size(), cands.stream().distinct().count());
    }

    private Board randomBoard(int stones) {
        Board b = new Board();
        int placed = 0;
        while (placed < stones) {
            int r = rnd.nextInt(Board.SIZE);
            int c = rnd.nextInt(Board.SIZE);
            if (b.isEmpty(r, c)) {
                b.place(r, c, rnd.nextBoolean() ? Board.BLACK : Board.WHITE);
                placed++;
            }
        }
        return b;
    }
}