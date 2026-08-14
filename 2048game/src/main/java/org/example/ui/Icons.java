package org.example.ui;

import javafx.scene.shape.SVGPath;

/**
 * 图标工具（spec2 §4.3）：Material Design 风格 SVG 路径图标，
 * 统一观感并规避 emoji 字符跨平台渲染不一致的问题。
 * 颜色由 CSS 的 .svg-icon（-fx-fill）控制，随主题变化。
 */
public final class Icons {

    private Icons() {
        // 工具类，禁止实例化
    }

    /** 主题图标：浅色主题返回太阳，暗黑主题返回月亮。 */
    public static SVGPath theme(boolean dark) {
        return of(dark ? DARK_MODE : LIGHT_MODE);
    }

    /** 音效开图标。 */
    public static SVGPath soundOn() {
        return of(VOLUME_UP);
    }

    /** 音效关图标。 */
    public static SVGPath soundOff() {
        return of(VOLUME_OFF);
    }

    /** 统计图标。 */
    public static SVGPath stats() {
        return of(CHART);
    }

    /** 撤销图标。 */
    public static SVGPath undo() {
        return of(UNDO);
    }

    /** 返回主菜单图标（spec3 §二 游戏内返回按钮）。 */
    public static SVGPath back() {
        return of(ARROW_BACK);
    }

    private static SVGPath of(String d) {
        SVGPath p = new SVGPath();
        p.setContent(d);
        p.getStyleClass().add("svg-icon");
        return p;
    }

    /** 太阳（light_mode，Material Icons 路径数据） */
    private static final String LIGHT_MODE =
            "M12,7c-2.76,0 -5,2.24 -5,5s2.24,5 5,5 5,-2.24 5,-5 -2.24,-5 -5,-5zM2,13h2c0.55,0 1,-0.45 1,-1s-0.45,-1 -1,-1H2c-0.55,0 -1,0.45 -1,1s0.45,1 1,1zM20,13h2c0.55,0 1,-0.45 1,-1s-0.45,-1 -1,-1h-2c-0.55,0 -1,0.45 -1,1s0.45,1 1,1zM11,2v2c0,0.55 0.45,1 1,1s1,-0.45 1,-1V2c0,-0.55 -0.45,-1 -1,-1s-1,0.45 -1,1zM11,20v2c0,0.55 0.45,1 1,1s1,-0.45 1,-1v-2c0,-0.55 -0.45,-1 -1,-1s-1,0.45 -1,1zM5.99,4.58c-0.39,-0.39 -1.03,-0.39 -1.41,0 -0.39,0.39 -0.39,1.03 0,1.41l1.06,1.06c0.39,0.39 1.03,0.39 1.41,0s0.39,-1.03 0,-1.41L5.99,4.58zM18.36,16.95c-0.39,-0.39 -1.03,-0.39 -1.41,0 -0.39,0.39 -0.39,1.03 0,1.41l1.06,1.06c0.39,0.39 1.03,0.39 1.41,0 0.39,-0.39 0.39,-1.03 0,-1.41l-1.06,-1.06zM19.42,5.99c0.39,-0.39 0.39,-1.03 0,-1.41 -0.39,-0.39 -1.03,-0.39 -1.41,0l-1.06,1.06c-0.39,0.39 -0.39,1.03 0,1.41s1.03,0.39 1.41,0l1.06,-1.06zM7.05,18.36c0.39,-0.39 0.39,-1.03 0,-1.41 -0.39,-0.39 -1.03,-0.39 -1.41,0l-1.06,1.06c-0.39,0.39 -0.39,1.03 0,1.41s1.03,0.39 1.41,0l1.06,-1.06z";

    /** 月亮（dark_mode） */
    private static final String DARK_MODE =
            "M12,3c-4.97,0 -9,4.03 -9,9s4.03,9 9,9s9,-4.03 9,-9c0,-0.46 -0.04,-0.92 -0.1,-1.36c-0.98,1.37 -2.58,2.26 -4.4,2.26c-2.98,0 -5.4,-2.42 -5.4,-5.4c0,-1.81 0.89,-3.42 2.26,-4.4C12.92,3.04 12.46,3 12,3z";

    /** 音量（volume_up） */
    private static final String VOLUME_UP =
            "M3,9v6h4l5,5V4L7,9H3zM16.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v8.05c1.48,-0.73 2.5,-2.25 2.5,-4.02zM14,3.23v2.06c2.89,0.86 5,3.54 5,6.71s-2.11,5.85 -5,6.71v2.06c4.01,-0.91 7,-4.49 7,-8.77s-2.99,-7.86 -7,-8.77z";

    /** 静音（volume_off） */
    private static final String VOLUME_OFF =
            "M16.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v2.21l2.45,2.45c0.03,-0.2 0.05,-0.41 0.05,-0.63zM19,12c0,0.94 -0.2,1.82 -0.54,2.64l1.51,1.51C20.63,14.91 21,13.5 21,12c0,-4.28 -2.99,-7.86 -7,-8.77v2.06c2.89,0.86 5,3.54 5,6.71zM4.27,3L3,4.27L7.73,9H3v6h4l5,5v-6.73l4.25,4.25c-0.67,0.52 -1.42,0.93 -2.25,1.18v2.06c1.38,-0.31 2.63,-0.95 3.69,-1.81L19.73,21L21,19.73l-9,-9L4.27,3zM12,4L9.91,6.09L12,8.18V4z";

    /** 统计柱状图（insert_chart） */
    private static final String CHART =
            "M19,3L5,3c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2L21,5c0,-1.1 -0.9,-2 -2,-2zM9,17L7,17v-5h2v5zM13,17h-2L11,7h2v10zM17,17h-2v-7h2v7z";

    /** 撤销（undo） */
    private static final String UNDO =
            "M12.5,8c-2.65,0 -5.05,0.99 -6.9,2.6L2,7v9h9l-3.62,-3.62c1.39,-1.16 3.16,-1.88 5.12,-1.88c3.54,0 6.55,2.31 7.6,5.5l2.37,-0.78C21.08,11.03 17.15,8 12.5,8z";

    /** 返回箭头（arrow_back） */
    private static final String ARROW_BACK =
            "M20,11H7.83l5.59,-5.59L12,4l-8,8l8,8l1.41,-1.41L7.83,13H20V11z";
}