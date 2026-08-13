package org.example.ui;

import javafx.scene.Scene;

import java.util.logging.Logger;
import java.util.prefs.Preferences;

/**
 * 主题切换（spec §4.3.5）：浅色 / 暗黑两套 CSS 互切，即时生效不重建场景。
 * 当前主题存 Preferences（键 theme），下次启动沿用。
 */
public class ThemeManager {

    private static final Logger LOGGER = Logger.getLogger(ThemeManager.class.getName());
    public static final String LIGHT = "light";
    public static final String DARK = "dark";

    private static final String PREFS_KEY = "theme";
    private static final String CSS_LIGHT = "/css/game-light.css";
    private static final String CSS_DARK = "/css/game-dark.css";

    private final Preferences prefs = Preferences.userRoot().node("2048game");
    private String current;

    public ThemeManager(String initial) {
        String saved;
        try {
            saved = prefs.get(PREFS_KEY, initial);
        } catch (Exception e) {
            saved = initial;
        }
        current = DARK.equals(saved) ? DARK : LIGHT;
    }

    public String getCurrent() {
        return current;
    }

    /** 应用当前主题到场景：移除已有 game-*.css 后加入当前主题样式表。 */
    public void apply(Scene scene) {
        scene.getStylesheets().removeIf(s -> s.contains("game-") && s.endsWith(".css"));
        String css = DARK.equals(current) ? CSS_DARK : CSS_LIGHT;
        scene.getStylesheets().add(getClass().getResource(css).toExternalForm());
    }

    /** 切换主题（浅色 ↔ 暗黑），即时生效并持久化。 */
    public void toggle(Scene scene) {
        current = DARK.equals(current) ? LIGHT : DARK;
        try {
            prefs.put(PREFS_KEY, current);
            prefs.flush();
        } catch (Exception e) {
            LOGGER.warning("主题偏好写入失败（静默降级）：" + e);
        }
        apply(scene);
    }
}
