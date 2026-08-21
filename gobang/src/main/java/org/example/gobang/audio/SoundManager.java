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
 * 音效管理（spec2 §5.5）：
 * 每类型 12 个 MediaPlayer 轮换复用（变体文件循环分配）；
 * 黑/白子随机变调 setRate(0.92~1.08)；支持显式 rate 重载；
 * 所有播放 try/catch，素材缺失静默跳过。
 */
public final class SoundManager {

    private static final int POOL_SIZE = 12;

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
            for (int i = 0; i < POOL_SIZE; i++) {
                // 池内轮询分配变体文件，保证变体均匀出现
                String file = t.fileName(i % t.variants + 1);
                Media m = loadMedia(file);
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
        play(t, 1.0, 1.0);
    }

    /** volumeScale：悬停音=0.15 等；rate=1 时黑白子自动随机变调。 */
    public static void play(SoundType t, double volumeScale) {
        double rate = 1.0;
        if (t == SoundType.STONE_BLACK || t == SoundType.STONE_WHITE) {
            rate = 0.92 + RND.nextDouble() * 0.16;
        }
        play(t, volumeScale, rate);
    }

    /** 显式速率播放。 */
    public static void play(SoundType t, double volumeScale, double rate) {
        if (!initialized || settings == null || settings.isSfxMuted()) {
            return;
        }
        try {
            List<MediaPlayer> list = POOLS.get(t);
            if (list == null || list.isEmpty()) {
                return;
            }
            MediaPlayer p = list.get(RND.nextInt(list.size()));
            p.stop();
            p.setRate(Math.max(0.25, Math.min(4, rate)));
            double v = Math.max(0, Math.min(1, settings.getSfxVolume() * volumeScale));
            p.setVolume(v);
            p.play();
        } catch (Throwable ignored) {
        }
    }
}
