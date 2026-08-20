package org.example.gobang.audio;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

import java.net.URL;

/**
 * 音乐管理：中国风 + 森林自然音双轨无限循环；
 * 按 musicVolume 混音同时播放；开关做 500ms 淡入淡出。
 */
public final class MusicManager {

    private static SettingsStore settings;
    private static MediaPlayer chinese;
    private static MediaPlayer forest;
    private static Timeline fade;
    private static boolean started = false;

    private MusicManager() {
    }

    public static void init(SettingsStore s) {
        settings = s;
        chinese = createTrack("/audio/bgm_chinese.mp3", "bgm_chinese.wav");
        forest = createTrack("/audio/bgm_forest.mp3", "bgm_forest.wav");
    }

    private static MediaPlayer createTrack(String resource, String synthName) {
        try {
            URL url = MusicManager.class.getResource(resource);
            Media m = url != null ? new Media(url.toExternalForm()) : new Media(SynthWav.urlFor(synthName));
            MediaPlayer p = new MediaPlayer(m);
            p.setCycleCount(MediaPlayer.INDEFINITE);
            return p;
        } catch (Throwable e) {
            return null;
        }
    }

    public static void start() {
        if (started) return;
        started = true;
        double v = currentTarget();
        if (chinese != null) {
            chinese.setVolume(v);
            chinese.play();
        }
        if (forest != null) {
            forest.setVolume(v);
            forest.play();
        }
    }

    private static double currentTarget() {
        return settings == null || settings.isMusicMuted() ? 0
                : Math.max(0, Math.min(1, settings.getMusicVolume()));
    }

    /** fade 为 true 时 500ms 淡入淡出，否则即时生效（拖滑条时用即时，防频繁动画）。 */
    public static void applySettings(boolean withFade) {
        if (!started) return;
        double target = currentTarget();
        if (withFade) {
            if (fade != null && fade.getStatus() == javafx.animation.Animation.Status.RUNNING) {
                fade.stop();
            }
            Timeline tl = new Timeline();
            if (chinese != null) {
                tl.getKeyFrames().add(new KeyFrame(Duration.millis(500),
                        new KeyValue(chinese.volumeProperty(), target)));
            }
            if (forest != null) {
                tl.getKeyFrames().add(new KeyFrame(Duration.millis(500),
                        new KeyValue(forest.volumeProperty(), target)));
            }
            if (!tl.getKeyFrames().isEmpty()) {
                fade = tl;
                tl.play();
            }
        } else {
            // 即时应用：先停掉残留的淡入淡出，防止动画回写旧目标值覆盖新音量
            if (fade != null && fade.getStatus() == javafx.animation.Animation.Status.RUNNING) {
                fade.stop();
                fade = null;
            }
            if (chinese != null) chinese.setVolume(target);
            if (forest != null) forest.setVolume(target);
        }
    }

    /** 音量滑条拖动时的即时应用（防止 500ms 动画频繁叠加卡顿）。 */
    public static void applyInstant() {
        applySettings(false);
    }

    public static void shutdown() {
        started = false;
        if (chinese != null) {
            chinese.stop();
            chinese.dispose();
            chinese = null;
        }
        if (forest != null) {
            forest.stop();
            forest.dispose();
            forest = null;
        }
    }
}