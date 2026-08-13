package org.example.ui;

import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.logging.Logger;

/**
 * 内置字体工具（spec2 §4.1）：启动时一次性注册 MiSans 中文字体，
 * 全局统一观感；加载失败静默回退系统字体，绝不影响启动。
 * <p>
 * MiSans（小米出品，免费商用）：按官方许可，应用内使用需注明所用字体为 MiSans——
 * 见 resources/fonts/MiSans-LICENSE.txt。
 */
public final class FontKit {

    private static final Logger LOGGER = Logger.getLogger(FontKit.class.getName());
    private static final String FONT_PATH = "/fonts/MiSans.ttf";
    private static final String SYSTEM = "System";

    private static String family = SYSTEM;
    private static boolean loaded;

    private FontKit() {
        // 工具类，禁止实例化
    }

    /** 注册内置字体（幂等）：返回实际生效的字体族名。 */
    public static String load() {
        if (loaded) {
            return family;
        }
        loaded = true;
        try (var in = FontKit.class.getResourceAsStream(FONT_PATH)) {
            Font font = Font.loadFont(in, 16);
            if (font != null) {
                family = font.getFamily();
                LOGGER.info("内置字体加载成功：" + family);
            } else {
                LOGGER.warning("内置字体解析失败，回退系统字体");
            }
        } catch (Exception e) {
            LOGGER.warning("内置字体加载失败（回退系统字体）: " + e);
        }
        return family;
    }

    /** 是否成功加载了内置字体。 */
    public static boolean isCustomLoaded() {
        return !SYSTEM.equals(family);
    }

    /** 当前字体族名（未加载时为 "System"）。 */
    public static String family() {
        return family;
    }

    /** 按当前字体族创建加粗字体（未加载时回退系统字体）。 */
    public static Font bold(double size) {
        return new Font(family, size);
    }

    /** 按当前字体族创建指定字重字体。 */
    public static Font font(FontWeight weight, double size) {
        return Font.font(family, weight, size);
    }
}