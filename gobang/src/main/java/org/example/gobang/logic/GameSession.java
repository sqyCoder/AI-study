package org.example.gobang.logic;

import org.example.gobang.model.Board;
import org.example.gobang.model.GameState;
import org.example.gobang.model.Move;
import org.example.gobang.model.MoveOutcome;

import java.util.ArrayList;
import java.util.List;

/**
 * 对局会话：唯一状态入口，防竞态核心。
 * <p>状态机：GUESS → PLAYING → FINISHED；PLAYING 期间 THINKING 仅由 UI 层维护，
 * session 通过 currentColor + 颜色角色（blackIsAI/whiteIsAI）决定谁该落子。
 */
public class GameSession {

    public enum Mode { PVE, PVP, ONLINE }

    public enum Difficulty { EASY, MEDIUM, HARD }

    public final Board board = new Board();

    private final Mode mode;
    private final Difficulty difficulty;

    private GameState state = GameState.GUESS;
    private int currentColor = Board.BLACK;
    private boolean blackIsAI;
    private boolean whiteIsAI;

    /** 生成号：重开/再来一局时递增，用于废弃过期 AI 回调。 */
    private volatile int generation = 0;

    public GameSession(Mode mode, Difficulty difficulty) {
        this.mode = mode;
        this.difficulty = difficulty;
    }

    public Mode getMode() {
        return mode;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public GameState getState() {
        return state;
    }

    public int getCurrentColor() {
        return currentColor;
    }

    public int getGeneration() {
        return generation;
    }

    public boolean isAI(int color) {
        return color == Board.BLACK ? blackIsAI : whiteIsAI;
    }

    /** 返回 AI 执的颜色；双人模式下返回 0。 */
    public int aiColor() {
        if (blackIsAI) return Board.BLACK;
        if (whiteIsAI) return Board.WHITE;
        return 0;
    }

    /** 猜先判定：持子颗数奇偶与猜单双是否一致。一致 = 猜中。 */
    public static boolean guesserWins(int heldCount, boolean guessOdd) {
        return (heldCount % 2 == 1) == guessOdd;
    }

    /**
     * 猜先结束后应用角色：猜中者执黑，否则持子者执黑。
     *
     * @param holderIsAI  持子方是否为 AI
     * @param guesserIsAI 猜子方是否为 AI
     * @param heldCount   持子方实际握的颗数（1 或 2）
     * @param guessOdd    猜子方猜的是单数
     */
    public void applyGuess(boolean holderIsAI, boolean guesserIsAI, int heldCount, boolean guessOdd) {
        boolean guesserIsBlack = guesserWins(heldCount, guessOdd);
        if (guesserIsBlack) {
            blackIsAI = guesserIsAI;
            whiteIsAI = holderIsAI;
        } else {
            blackIsAI = holderIsAI;
            whiteIsAI = guesserIsAI;
        }
        state = GameState.PLAYING;
        currentColor = Board.BLACK;
        generation++;
    }

    /** 合法落子（状态与回合校验）。被拒绝返回 null。 */
    public MoveOutcome place(int r, int c, int color) {
        if (state != GameState.PLAYING) return null;
        if (color != currentColor) return null;
        return apply(r, c, color);
    }

    /** 无校验落子（供测试构造局面使用）。 */
    public MoveOutcome placeAny(int r, int c, int color) {
        return apply(r, c, color);
    }

    private MoveOutcome apply(int r, int c, int color) {
        if (!board.place(r, c, color)) return null;
        Move last = board.getLastMove();
        List<Move> line = WinChecker.checkWin(board, last);
        if (line != null) {
            state = GameState.FINISHED;
            return MoveOutcome.win(line);
        }
        if (board.isFull()) {
            state = GameState.FINISHED;
            return MoveOutcome.draw();
        }
        currentColor = 3 - color;
        return MoveOutcome.none();
    }

    /**
     * 悔棋：每次撤 2 子（PvE 撤玩家+AI 各 1 子、PvP 撤最近 2 子）。
     * 终局/猜先阶段拒绝；历史不足时只撤现有子数。
     *
     * @return 被移除的落子列表（按移除顺序，最后一手在前）；非法返回 null。
     */
    public List<Move> undo() {
        if (state != GameState.PLAYING) return null;
        List<Move> h = board.getHistory();
        if (h.isEmpty()) return null;
        int n = Math.min(2, h.size());
        List<Move> removed = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            removed.add(board.removeLast());
        }
        h = board.getHistory();
        currentColor = h.isEmpty() ? Board.BLACK : 3 - h.get(h.size() - 1).color;
        return removed;
    }

    /** 再来一局：交换黑白，清盘，直接进入 PLAYING（不再猜先）。 */
    public void nextRound() {
        boolean tmp = blackIsAI;
        blackIsAI = whiteIsAI;
        whiteIsAI = tmp;
        board.clear();
        state = GameState.PLAYING;
        currentColor = Board.BLACK;
        generation++;
    }

    /** 重新开局：清盘回到猜先。 */
    public void restart() {
        board.clear();
        blackIsAI = false;
        whiteIsAI = false;
        state = GameState.GUESS;
        currentColor = Board.BLACK;
        generation++;
    }
}