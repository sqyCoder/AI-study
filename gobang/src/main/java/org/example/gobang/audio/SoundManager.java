package org.example.gobang.audio;

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

import java.net.URL;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 音效管理：启动时加载全部到 Map&lt;SoundType, MediaPlayer[]&gt;；
 * 每类型固定播放池轮换复用（防 GC 堆积）；
 * 黑/白子变调 setRate(0.92 + rand×0.16)；
 * 悬停音=总音量×0.15、落子=×1.0、终局=×1.0；
 * 所有播放 try/catch，素材缺失静默跳过。
 */
public final class SoundManager {

    private static SettingsStore settings;
    private static final Map<SoundType, List<MediaPlayer>> POOLS = new EnumMap<>(SoundType.class);
    private static final Random RND = new Random();
    private static boolean initialized = false;

    private SoundManager() {
    }

    /** 必须在 FX 线程调用（Main.start）。 */
    public static void init(SettingsStore s) {
        settings = s;
        for (SoundType t : SoundType.values()) {
            List<MediaPlayer> list = new ArrayList<>();
            for (int i = 0; i < t.variants; i++) {
                Media m = loadMedia(t.fileName(i + 1));
                if (m != null) {
                    list.add(new MediaPlayer(m));
                }
            }
            if (!list.isEmpty()) {
                POOLS.put(t, list);
            }
        }
        initialized = true;
    }

    private static Media loadMedia(String fileName) {
        try {
            URL url = SoundManager.class.getResource("/sfx/" + fileName);
            if (url != null) {
                return new Media(url.toExternalForm());
            }
            String synth = SynthWav.urlFor(fileName);
            if (synth != null) {
                return new Media(synth);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static void play(SoundType t) {
        play(t, 1.0);
    }

    /** volumeScale：悬停音=0.15，其余=1.0。 */
    public static void play(SoundType t, double volumeScale) {
        if (!initialized || settings == null || settings.isSfxMuted()) return;
        try {
            List<MediaPlayer> list = POOLS.get(t);
            if (list == null || list.isEmpty()) return;
            MediaPlayer p = list.get(RND.nextInt(list.size()));
            p.stop();
            if (t == SoundType.STONE_BLACK || t == SoundType.STONE_WHITE) {
                p.setRate(0.92 + RND.nextDouble() * 0.16);
            } else {
                p.setRate(1.0);
            }
            double v = Math.max(0, Math.min(1, settings.getSfxVolume() * volumeScale));
            p.setVolume(v);
            p.play();
        } catch (Throwable ignored) {
        }
    }
}