package org.example.gobang.audio;

/** 音效类型清单。base 为资源文件名前缀（resources/sfx/ 下，wav）。 */
public enum SoundType {
    STONE_BLACK("stone_black", 4),
    STONE_WHITE("stone_white", 4),
    WIN("win", 1),
    LOSE("lose", 1),
    DRAW("draw", 1),
    CLICK("click", 1),
    HOVER("hover", 1),
    UNDO("undo", 1),
    INVALID("invalid", 1),
    GUESS_HOLD("guess_hold", 1),
    GUESS_PICK("guess_pick", 1),
    GUESS_REVEAL("guess_reveal", 1),
    GUESS_RESULT_WIN("guess_result_win", 1),
    GUESS_RESULT_LOSE("guess_result_lose", 1),
    LEAF_RUSTLE("leaf_rustle", 1);

    public final String base;
    public final int variants;

    SoundType(String base, int variants) {
        this.base = base;
        this.variants = variants;
    }

    /** 资源文件名，如 stone_black_1.wav / win.wav。 */
    public String fileName(int index) {
        return variants > 1 ? base + "_" + index + ".wav" : base + ".wav";
    }
}