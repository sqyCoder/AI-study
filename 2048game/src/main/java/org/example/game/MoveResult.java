package org.example.game;

import java.util.List;

/**
 * 一次移动的结果（spec §4.1.5），供动画层消费的数据契约。
 * <p>
 * 设计意图：UI 只消费 MoveResult 的数据（moves/spawned），不自己推导动画，
 * 彻底杜绝"动画与逻辑不一致"。
 */
public record MoveResult(
        boolean moved,          // 本次是否有效移动
        int scoreDelta,         // 本次加分
        List<TileMove> moves,   // 每块 from(r,c) → to(r,c) 及值/合并标记
        TileSpawn spawned,      // 生成块的位置与值（无效移动为 null）
        boolean gameOver,       // 移动后是否游戏结束
        boolean winReached      // 本次是否新达成 2048 胜利
) {

    /** 无效移动的空结果（不生成块、不移动）。 */
    public static MoveResult noMove(boolean gameOver) {
        return new MoveResult(false, 0, List.of(), null, gameOver, false);
    }
}
