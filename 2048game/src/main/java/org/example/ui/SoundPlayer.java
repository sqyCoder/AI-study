package org.example.ui;

import javafx.scene.media.AudioClip;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 音效播放（spec §4.3.7）：四类提示音 + 全局开关。
 * <p>
 * 音频文件（resources/audio/*.wav，M7 生成）缺失或加载失败一律静默降级，
 * 音频属体验增强，绝不影响玩法。
 */
public class SoundPlayer {

    private static final Logger LOGGER = Logger.getLogger(SoundPlayer.class.getName());

    private static final String[] FILES = {"merge.wav", "win.wav", "gameover.wav", "click.wav"};

    private final Map<String, AudioClip> clips = new HashMap<>();
    private boolean enabled;

    public SoundPlayer(boolean enabled) {
        this.enabled = enabled;
        for (String file : FILES) {
            try {
                URL url = getClass().getResource("/audio/" + file);
                if (url != null) {
                    clips.put(file, new AudioClip(url.toExternalForm()));
                }
            } catch (Exception e) {
                LOGGER.warning("音频加载失败（静默降级）: " + file + " -> " + e);
            }
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void toggle() {
        enabled = !enabled;
    }

    public void playMerge() {
        play("merge.wav");
    }

    public void playWin() {
        play("win.wav");
    }

    public void playGameOver() {
        play("gameover.wav");
    }

    public void playClick() {
        play("click.wav");
    }

    private void play(String file) {
        if (!enabled) {
            return;
        }
        AudioClip clip = clips.get(file);
        if (clip != null) {
            clip.play();
        }
    }
}
