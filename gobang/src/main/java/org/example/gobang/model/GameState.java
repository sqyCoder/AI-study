package org.example.gobang.model;

/** 对局状态机。 */
public enum GameState {
    /** 猜先仪式中 */
    GUESS,
    /** 对局进行中（轮到某方落子） */
    PLAYING,
    /** AI 思考中（仅 UI 层维护，session 不感知） */
    THINKING,
    /** 已分胜负或满盘平局 */
    FINISHED
}
