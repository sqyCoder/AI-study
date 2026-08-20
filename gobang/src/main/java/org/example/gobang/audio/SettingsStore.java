package org.example.gobang.audio;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

/**
 * 设置持久化：用户目录/.gobang/config.properties。
 * 键：music.volume / sfx.volume / music.muted / sfx.muted。
 * 启动读、修改即写（写失败静默）。
 */
public class SettingsStore {

    private final File file;
    private final Properties props = new Properties();

    private double musicVolume = 0.7;
    private double sfxVolume = 0.8;
    private boolean musicMuted = false;
    private boolean sfxMuted = false;

    public SettingsStore() {
        file = new File(System.getProperty("user.home") + File.separator + ".gobang"
                + File.separator + "config.properties");
        load();
    }

    private void load() {
        try (InputStream in = new FileInputStream(file)) {
            props.load(in);
            musicVolume = clamp(Double.parseDouble(props.getProperty("music.volume", "0.7")));
            sfxVolume = clamp(Double.parseDouble(props.getProperty("sfx.volume", "0.8")));
            musicMuted = Boolean.parseBoolean(props.getProperty("music.muted", "false"));
            sfxMuted = Boolean.parseBoolean(props.getProperty("sfx.muted", "false"));
        } catch (Exception e) {
            // 首次运行或文件损坏：使用默认值
        }
    }

    private void save() {
        try {
            File dir = file.getParentFile();
            if (dir != null && !dir.exists()) {
                dir.mkdirs();
            }
            props.setProperty("music.volume", String.valueOf(musicVolume));
            props.setProperty("sfx.volume", String.valueOf(sfxVolume));
            props.setProperty("music.muted", String.valueOf(musicMuted));
            props.setProperty("sfx.muted", String.valueOf(sfxMuted));
            try (OutputStream out = new FileOutputStream(file)) {
                props.store(out, "gobang settings");
            }
        } catch (Exception e) {
            // 写失败静默，游戏照常
        }
    }

    public double getMusicVolume() {
        return musicVolume;
    }

    /** 仅改内存；由调用方在合适的时机（滑块松手/开关切换）调用 persist() 落盘，避免拖动时高频写文件。 */
    public void setMusicVolume(double v) {
        musicVolume = clamp(v);
    }

    public double getSfxVolume() {
        return sfxVolume;
    }

    public void setSfxVolume(double v) {
        sfxVolume = clamp(v);
    }

    public boolean isMusicMuted() {
        return musicMuted;
    }

    public void setMusicMuted(boolean b) {
        musicMuted = b;
    }

    public boolean isSfxMuted() {
        return sfxMuted;
    }

    public void setSfxMuted(boolean b) {
        sfxMuted = b;
    }

    /** 显式落盘（写失败静默，游戏照常）。 */
    public void persist() {
        save();
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }
}