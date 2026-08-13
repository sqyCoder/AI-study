package org.example.game;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 撤销栈（spec §4.1.6）：棋盘 + 分数快照，深度上限 20。
 * <p>
 * 每次有效移动前把（棋盘快照, score）压栈；栈满时丢弃最旧快照。
 * 入栈时机由引擎控制：仅在确认移动有效后入栈，避免无效移动污染栈。
 */
public class UndoManager {

    /** 撤销深度上限。 */
    public static final int UNDO_DEPTH = 20;

    private final Deque<UndoEntry> stack = new ArrayDeque<>();

    /**
     * 压入快照（内部再做一次防御拷贝，防外部篡改）。
     *
     * @param gridSnapshot 移动前的棋盘快照
     * @param score        移动前的分数
     */
    public void push(Tile[][] gridSnapshot, int score) {
        Tile[][] copy = new Tile[gridSnapshot.length][gridSnapshot.length];
        for (int r = 0; r < gridSnapshot.length; r++) {
            System.arraycopy(gridSnapshot[r], 0, copy[r], 0, gridSnapshot.length);
        }
        stack.addLast(new UndoEntry(copy, score));
        while (stack.size() > UNDO_DEPTH) {
            stack.removeFirst();
        }
    }

    /** 是否可撤销。 */
    public boolean canUndo() {
        return !stack.isEmpty();
    }

    /** 弹出最近一次快照（后入先出）。 */
    public UndoEntry pop() {
        return stack.removeLast();
    }

    /** 清空撤销栈（新开局 / 切换尺寸时调用）。 */
    public void clear() {
        stack.clear();
    }
}
