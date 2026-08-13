package org.example.game;

/**
 * 一局统计（spec §4.1.7）：分数与步数，随引擎推进。
 * <p>
 * 计分约定：合并值累加（如 2+2 得 4 分）；步数每次有效移动 +1；
 * 撤销回退分数但不回退步数，且撤销操作本身使步数 +1（视为一次操作，见 spec §4.7）。
 */
public class GameStats {

    private int score;
    private int steps;

    /** 空统计（分数、步数均为 0）。 */
    public GameStats() {
    }

    /** 指定初值构造，用于防御性拷贝。 */
    public GameStats(int score, int steps) {
        this.score = score;
        this.steps = steps;
    }

    public int getScore() {
        return score;
    }

    public int getSteps() {
        return steps;
    }

    /** 累加得分（合并加分）。 */
    public void addScore(int delta) {
        score += delta;
    }

    /** 步数 +1（仅有效移动触发）。 */
    public void incrementSteps() {
        steps++;
    }

    /** 直接设置分数（撤销恢复时使用）。 */
    public void setScore(int score) {
        this.score = score;
    }

    /** 归零（新开局）。 */
    public void reset() {
        score = 0;
        steps = 0;
    }
}
