package org.example.ui;

import javafx.scene.control.Labeled;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * 双语切换（spec §4.3.6）。
 * <p>
 * 资源：i18n/messages_zh.properties、i18n/messages_en.properties（UTF-8）。
 * 所有用户可见文案一律走 key（NFR-6）；切换语言时通过"绑定注册表 + 回调"
 * 全量刷新，不得遗漏。缺失 key 记录 warn 并回退中文。
 */
public class I18n {

    private static final Logger LOGGER = Logger.getLogger(I18n.class.getName());
    private static final String RESOURCE_BASE = "/i18n/messages";
    public static final String LANG_ZH = "zh";
    public static final String LANG_EN = "en";

    private final Map<String, String> zhBundle;
    private final Map<String, String> enBundle;
    private String lang;
    private final List<Binding> bindings = new ArrayList<>();
    private final List<Runnable> refreshCallbacks = new ArrayList<>();

    /**
     * @param lang 初始语言（"zh" / "en"，其他值回退中文）
     */
    public I18n(String lang) {
        zhBundle = load("_zh");
        enBundle = load("_en");
        setLang(lang);
    }

    public String getLang() {
        return lang;
    }

    /** 切换语言并全量刷新：已绑定控件 setText + 注册的回调（按钮文本/统计面板/标题等）。 */
    public void setLang(String lang) {
        this.lang = LANG_EN.equals(lang) ? LANG_EN : LANG_ZH;
        Map<String, String> bundle = currentBundle();
        for (Binding b : bindings) {
            b.node.setText(bundle.getOrDefault(b.key, b.node.getText()));
        }
        for (Runnable r : refreshCallbacks) {
            r.run();
        }
    }

    /** 取文案（缺失时 warn 并回退中文，再缺失返回 key 本身）。 */
    public String t(String key) {
        String v = currentBundle().get(key);
        if (v == null) {
            LOGGER.warning("缺少文案 key: " + key + "，回退中文");
            v = zhBundle.get(key);
            if (v == null) {
                return key;
            }
        }
        return v;
    }

    /** 格式化文案：把文案中的 {0}、{1}… 替换为参数（无 MessageFormat 单引号坑）。 */
    public String format(String key, Object... args) {
        String s = t(key);
        for (int i = 0; i < args.length; i++) {
            s = s.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return s;
    }

    /** 绑定控件与文案 key：立即生效，语言切换时自动刷新。 */
    public void bind(Labeled node, String key) {
        bindings.add(new Binding(node, key));
        node.setText(t(key));
    }

    /** 注册语言切换时的自定义刷新回调（按钮文本、数值、统计面板等）。 */
    public void addRefreshCallback(Runnable callback) {
        refreshCallbacks.add(callback);
    }

    private Map<String, String> currentBundle() {
        return LANG_EN.equals(lang) ? enBundle : zhBundle;
    }

    /** 加载 properties（UTF-8，JDK9+ Properties.load(Reader) 默认按 UTF-8 解码）。 */
    private static Map<String, String> load(String suffix) {
        Map<String, String> map = new HashMap<>();
        try (InputStream is = I18n.class.getResourceAsStream(RESOURCE_BASE + suffix + ".properties");
             Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            Properties p = new Properties();
            p.load(reader);
            for (String key : p.stringPropertyNames()) {
                map.put(key, p.getProperty(key));
            }
        } catch (IOException | NullPointerException e) {
            LOGGER.severe("加载文案资源失败: " + RESOURCE_BASE + suffix + " -> " + e);
        }
        return map;
    }

    private record Binding(Labeled node, String key) {
    }
}
