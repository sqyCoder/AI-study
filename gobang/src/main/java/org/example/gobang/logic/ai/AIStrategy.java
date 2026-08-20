package org.example.gobang.logic.ai;

import org.example.gobang.model.Board;

/** AI 策略接口。choose 返回一个合法的空位。 */
public interface AIStrategy {
    Point choose(Board board, int color);
}