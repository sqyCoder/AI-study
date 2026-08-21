package org.example.gobang.net.supa;

import java.io.InputStream;
import java.util.Properties;

/**
 * Supabase 配置加载（spec4 §3）：resources/supabase.properties。
 * 缺失或损坏返回 null → UI 置灰「房间码联机」入口。
 */
public final class SupaConfig {

    private final String url;
    private final String anonKey;

    private SupaConfig(String url, String anonKey) {
        this.url = url;
        this.anonKey = anonKey;
    }

    /** 加载配置；不可用返回 null。 */
    public static SupaConfig load() {
        try (InputStream in = SupaConfig.class.getResourceAsStream("/supabase.properties")) {
            if (in == null) {
                return null;
            }
            Properties p = new Properties();
            p.load(in);
            String url = p.getProperty("supabase.url", "").trim();
            String key = p.getProperty("supabase.anonKey", "").trim();
            if (url.isEmpty() || key.isEmpty()) {
                return null;
            }
            return new SupaConfig(url, key);
        } catch (Exception e) {
            return null;
        }
    }

    /** PostgREST 基址（表轮询传输唯一依赖的通道，实测国内稳定可用）。 */
    public String restBase() {
        return url.replaceFirst("/+$", "") + "/rest/v1";
    }

    public String url() {
        return url;
    }

    public String anonKey() {
        return anonKey;
    }
}
