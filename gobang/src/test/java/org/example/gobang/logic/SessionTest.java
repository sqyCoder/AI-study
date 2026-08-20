package org.example.gobang.logic;

import org.example.gobang.model.Board;
import org.example.gobang.model.GameState;
import org.example.gobang.model.Move;
import org.example.gobang.model.MoveOutcome;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SessionTest {

    private GameSession pvp() {
        GameSession s = new GameSession(GameSession.Mode.PVP, GameSession.Difficulty.MEDIUM);
        s.applyGuess(false, false, 1, true);
        return s;
    }

    /** 用例 20：悔棋后棋盘与回合一致（PvP）。 */
    @Test
    void undoKeepsBoardAndTurnConsistent() {
        GameSession s = pvp();
        assertNotNull(s.place(0, 0, Board.BLACK)); // 黑
        assertNotNull(s.place(0, 1, Board.WHITE));
        assertNotNull(s.place(1, 0, Board.BLACK));
        assertNotNull(s.place(1, 1, Board.WHITE));

        List<Move> removed = s.undo();
        assertNotNull(removed);
        assertEquals(2, removed.size());
        assertEquals(Board.WHITE, removed.get(0).color); // 先撤最后一手
        assertEquals(Board.BLACK, removed.get(1).color);

        assertEquals(Board.BLACK, s.getCurrentColor());
        assertTrue(s.board.isEmpty(1, 1));
        assertTrue(s.board.isEmpty(1, 0));
        assertFalse(s.board.isEmpty(0, 0));
        assertFalse(s.board.isEmpty(0, 1));
        assertEquals(2, s.board.getHistory().size());

        // 轮次校验：白方再落会被拒绝，黑方可落
        assertNull(s.place(2, 2, Board.WHITE));
        assertNotNull(s.place(2, 2, Board.BLACK));
    }

    /** 用例 20b：悔棋至开局时回退到黑方回合。 */
    @Test
    void undoToEmptyBoardReturnsToBlack() {
        GameSession s = pvp();
        s.place(0, 0, Board.BLACK);
        s.undo();
        assertEquals(Board.BLACK, s.getCurrentColor());
        assertTrue(s.board.getHistory().isEmpty());
    }

    /** 用例 21：PvE 悔棋撤 2 子（AI + 玩家各 1）。 */
    @Test
    void pveUndoRemovesTwo() {
        // 人执黑、AI 执白：applyGuess(false, true, 1, false) => 猜错 → 持子方(人)执黑
        GameSession s = new GameSession(GameSession.Mode.PVE, GameSession.Difficulty.MEDIUM);
        s.applyGuess(false, true, 1, false);
        assertEquals(Board.BLACK, s.getCurrentColor());
        assertFalse(s.isAI(Board.BLACK));
        assertTrue(s.isAI(Board.WHITE));

        s.place(7, 7, Board.BLACK);   // 人
        s.place(7, 8, Board.WHITE);   // AI
        s.place(7, 6, Board.BLACK);   // 人
        s.place(7, 5, Board.WHITE);   // AI，轮到人

        List<Move> removed = s.undo();
        assertNotNull(removed);
        assertEquals(2, removed.size());
        assertEquals(Board.WHITE, removed.get(0).color);
        assertEquals(Board.BLACK, removed.get(1).color);
        assertEquals(2, s.board.getHistory().size());
        assertEquals(Board.BLACK, s.getCurrentColor()); // 轮到人
        assertTrue(s.board.isEmpty(7, 5));
        assertTrue(s.board.isEmpty(7, 6));
    }

    /** 用例 22：再来一局交换黑白。 */
    @Test
    void nextRoundSwapsColors() {
        GameSession s = new GameSession(GameSession.Mode.PVE, GameSession.Difficulty.MEDIUM);
        s.applyGuess(false, true, 1, false); // 人执黑
        assertFalse(s.isAI(Board.BLACK));
        s.place(7, 7, Board.BLACK);
        s.nextRound();
        assertTrue(s.isAI(Board.BLACK)); // AI 变为黑
        assertFalse(s.isAI(Board.WHITE));
        assertEquals(GameState.PLAYING, s.getState());
        assertEquals(Board.BLACK, s.getCurrentColor());
        assertTrue(s.board.getHistory().isEmpty());
        assertNotEquals(s.getGeneration(), 0);
    }

    /** 用例 23：猜先四组合全部正确。 */
    @Test
    void guessFourCombinations() {
        // 组合 1：人持 1（单），AI 猜单 → 猜中 → AI 执黑
        GameSession s1 = new GameSession(GameSession.Mode.PVE, GameSession.Difficulty.EASY);
        s1.applyGuess(false, true, 1, true);
        assertTrue(s1.isAI(Board.BLACK));

        // 组合 2：人持 2（双），AI 猜单 → 猜错 → 人执黑
        GameSession s2 = new GameSession(GameSession.Mode.PVE, GameSession.Difficulty.EASY);
        s2.applyGuess(false, true, 2, true);
        assertFalse(s2.isAI(Board.BLACK));
        assertTrue(s2.isAI(Board.WHITE));

        // 组合 3：AI 持 1（单），人猜双 → 猜错 → AI 执黑
        GameSession s3 = new GameSession(GameSession.Mode.PVE, GameSession.Difficulty.EASY);
        s3.applyGuess(true, false, 1, false);
        assertTrue(s3.isAI(Board.BLACK));

        // 组合 4：AI 持 2（双），人猜双 → 猜中 → 人执黑
        GameSession s4 = new GameSession(GameSession.Mode.PVE, GameSession.Difficulty.EASY);
        s4.applyGuess(true, false, 2, false);
        assertFalse(s4.isAI(Board.BLACK));

        // 双人：无论持/猜角色，双方都不是 AI
        GameSession s5 = new GameSession(GameSession.Mode.PVP, GameSession.Difficulty.MEDIUM);
        s5.applyGuess(false, false, 1, true);
        assertEquals(0, s5.aiColor());
        assertEquals(Board.BLACK, s5.getCurrentColor());

        // 判定函数本身
        assertTrue(GameSession.guesserWins(1, true));
        assertFalse(GameSession.guesserWins(2, true));
        assertFalse(GameSession.guesserWins(1, false));
        assertTrue(GameSession.guesserWins(2, false));
    }

    /** 用例 24：满盘平局 → FINISHED。 */
    @Test
    void fullBoardDraw() {
        GameSession s = pvp();
        int lastR = -1, lastC = -1, lastColor = Board.BLACK;
        for (int r = 0; r < Board.SIZE; r++) {
            for (int c = 0; c < Board.SIZE; c++) {
                if (r == Board.SIZE - 1 && c == Board.SIZE - 1) continue; // 预留最后一格
                int color = ((r * 2 + c) % 4 < 2) ? Board.BLACK : Board.WHITE;
                lastColor = color;
                lastR = r;
                lastC = c;
                s.placeAny(r, c, color);
            }
        }
        assertFalse(s.board.isFull());
        MoveOutcome oc = s.placeAny(Board.SIZE - 1, Board.SIZE - 1, lastColor); // 第 225 手
        assertNotNull(oc);
        assertEquals(MoveOutcome.Type.DRAW, oc.type);
        assertEquals(GameState.FINISHED, s.getState());
        assertTrue(s.board.isFull());
    }

    /** 悔棋在终局/猜先阶段被拒绝。 */
    @Test
    void undoRejectedWhenNotPlaying() {
        GameSession s = new GameSession(GameSession.Mode.PVP, GameSession.Difficulty.MEDIUM);
        assertNull(s.undo()); // GUESS
        s.applyGuess(false, false, 1, true);
        assertNull(s.undo()); // 空盘
        s.place(0, 0, Board.BLACK);
        s.place(1, 0, Board.WHITE);
        s.place(0, 1, Board.BLACK);
        s.place(1, 1, Board.WHITE);
        s.place(0, 2, Board.BLACK);
        s.place(1, 2, Board.WHITE);
        s.place(0, 3, Board.BLACK);
        s.place(1, 3, Board.WHITE);
        s.place(0, 4, Board.BLACK); // 黑横向五连
        assertEquals(GameState.FINISHED, s.getState());
        assertNull(s.undo());
    }

    /** 状态机：错误回合落子被拒绝；非法坐标落子被拒绝。 */
    @Test
    void turnAndPlaceValidation() {
        GameSession s = pvp();
        assertNull(s.place(7, 7, Board.WHITE)); // 该黑走
        assertNotNull(s.place(7, 7, Board.BLACK));
        assertNull(s.place(7, 7, Board.WHITE)); // 已占
        assertNull(s.place(20, 20, Board.WHITE)); // 越界
    }
}