package org.example.gobang.audio;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 音频合成引擎 2.0（spec2 §5）：
 * 立体声 WAV（44100/16bit/双声道）+ Schroeder 混响（干湿比按音效配置）
 * + Karplus-Strong 拨弦 + tanh 软限幅；缓存于临时目录 gobang_synth_v5
 * （v4.3：落子音重做——中频敲击主体提升全喇叭可闻度，混响减半防稀释）。
 * 素材缺失时的回退方案：生成失败返回 null，游戏静默跳过照常可玩。
 */
public final class SynthWav {

    public static final int RATE = Dsp.RATE;
    private static final Path DIR = Paths.get(System.getProperty("java.io.tmpdir"), "gobang_synth_v5");

    private SynthWav() {
    }

    /** 返回可交给 javafx.scene.media.Media 的 URL；生成失败返回 null。 */
    public static String urlFor(String fileName) {
        try {
            Files.createDirectories(DIR);
            Path f = DIR.resolve(fileName);
            if (!Files.exists(f)) {
                double[][] st = synthesize(fileName);
                if (st == null) {
                    return null;
                }
                Files.write(f, wavBytesStereo(st[0], st[1]));
            }
            return f.toUri().toString();
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** 后台预热：把全部音效/BGM 一次性生成进缓存（Main 启动线程调用）。 */
    public static void preloadAll() {
        for (org.example.gobang.audio.SoundType t : org.example.gobang.audio.SoundType.values()) {
            for (int i = 1; i <= t.variants; i++) {
                urlFor(t.fileName(i));
            }
        }
        urlFor("bgm_chinese.wav");
        urlFor("bgm_forest.wav");
    }

    // ---------- 配方分发 ----------

    private static double[][] synthesize(String name) {
        if (name.startsWith("stone_black_")) {
            return finish(stoneBlack(), 0.08, -0.08);
        }
        if (name.startsWith("stone_white_")) {
            return finish(stoneWhite(), 0.06, 0.08);
        }
        switch (name) {
            case "win.wav": return finish(win(), 0.34, 0);
            case "lose.wav": return finish(lose(), 0.30, 0);
            case "draw.wav": return finish(draw(), 0.20, 0);
            case "click.wav": return finish(click(), 0.06, 0);
            case "hover.wav": return finish(hover(), 0, 0);
            case "undo.wav": return finish(undo(), 0.12, 0);
            case "invalid.wav": return finish(invalid(), 0.10, 0);
            case "guess_hold.wav": return finish(guessHold(), 0.18, 0);
            case "guess_pick.wav": return finish(guessPick(), 0.10, 0);
            case "guess_reveal.wav": return finish(guessReveal(), 0.22, 0);
            case "guess_result_win.wav": return finish(guessResultWin(), 0.25, 0);
            case "guess_result_lose.wav": return finish(guessResultLose(), 0.25, 0);
            case "page_switch.wav": return finish(pageSwitch(), 0.12, 0);
            case "leaf_rustle.wav": return finish(leafRustle(), 0.30, 0.3);
            case "bgm_chinese.wav": return finish(bgmChinese(), 0.10, 0);
            case "bgm_forest.wav": return finish(bgmForest(), 0.06, 0);
            default: return null;
        }
    }

    /** 单声道 → 立体声：混响（右声道 +13 样本去相关）+ 声像 + 全局归一。 */
    private static double[][] finish(double[] mono, double wet, double pan) {
        double gl = Math.cos((pan + 1) * Math.PI / 4);
        double gr = Math.sin((pan + 1) * Math.PI / 4);
        double[] l = Dsp.reverb(mono, wet, 0);
        double[] r = Dsp.reverb(mono, wet, 13);
        double max = 0;
        for (int i = 0; i < l.length; i++) {
            l[i] *= gl;
            r[i] *= gr;
            max = Math.max(max, Math.max(Math.abs(l[i]), Math.abs(r[i])));
        }
        if (max > 0) {
            double k = 0.9 / max;
            for (int i = 0; i < l.length; i++) {
                l[i] *= k;
                r[i] *= k;
            }
        }
        return new double[][]{l, r};
    }

    // ---------- 音效配方（spec2 §5.3） ----------

    /** 黑子：亮木敲击（中频主体，全喇叭可闻）+ 木腔共鸣 + 厚 thump。 */
    private static double[] stoneBlack() {
        double[] b = Dsp.buffer(0.45);
        // 攻击瞬态：更亮更长（2.2kHz / 18ms）
        Dsp.addBandBurst(b, 0, 2200 + (DspSeed.next() - 0.5) * 700, 0.018, 1.0, 0.004);
        // 中频敲击主体（~950Hz，小喇叭的可闻核心），衰减 110ms
        double k = 950 * (1 + (DspSeed.next() - 0.5) * 0.06);
        Dsp.addHarmonicTone(b, 0, k, 0.30, 0.55, 0.11, 0.001, new double[]{0, 0.5, 0.25});
        // 木腔共鸣（175Hz）加长增强
        double f0 = 175 * (1 + (DspSeed.next() - 0.5) * 0.04);
        Dsp.addHarmonicTone(b, 0, f0, 0.22, 0.5, 0.09, 0.001, new double[]{0, 0.4});
        // 低频 thump
        Dsp.addTone(b, 0, 82, 0.12, 0.55, 0.04, 0.001);
        return b;
    }

    /** 白子：更亮更脆（1.25kHz 敲击主体 + 高频 tick）。 */
    private static double[] stoneWhite() {
        double[] b = Dsp.buffer(0.35);
        Dsp.addBandBurst(b, 0, 3200 + (DspSeed.next() - 0.5) * 900, 0.016, 1.0, 0.003);
        double k = 1250 * (1 + (DspSeed.next() - 0.5) * 0.06);
        Dsp.addHarmonicTone(b, 0, k, 0.24, 0.5, 0.085, 0.001, new double[]{0, 0.45, 0.2});
        Dsp.addTone(b, 0, 235, 0.16, 0.45, 0.055, 0.001);
        Dsp.addTone(b, 0, 95, 0.09, 0.3, 0.03, 0.001);
        Dsp.addTone(b, 0, 4200, 0.006, 0.35, 0.0015, 0.0005);
        return b;
    }

    /** 胜利：五声上行琶音 + D 大三和弦 KS 长音 + 高频 shimmer。 */
    private static double[] win() {
        double[] b = Dsp.buffer(2.6);
        double[] arp = {587.33, 739.99, 880.0, 987.77, 1174.66}; // D5 F#5 A5 B5 D6
        for (int i = 0; i < arp.length; i++) {
            Dsp.mix(b, Dsp.ksPluck(arp[i], 1.2, 0.9975, 0.6), (int) (RATE * (0.05 + i * 0.11)), 0.55);
        }
        double[] chord = {587.33, 739.99, 880.0};
        for (int i = 0; i < chord.length; i++) {
            Dsp.mix(b, Dsp.ksPluck(chord[i], 1.8, 0.9975, 0.6),
                    (int) (RATE * (0.62 + i * 0.015)), 0.38);
        }
        double[] shim = {2349.3, 2637.0, 3136.0};
        for (int i = 0; i < shim.length; i++) {
            Dsp.mix(b, Dsp.ksPluck(shim[i], 1.2, 0.998, 0.7),
                    (int) (RATE * (0.70 + i * 0.03)), 0.08);
        }
        return Dsp.normalize(b, 0.95);
    }

    /** 失败：古琴低音下行 A2 F2 D2。 */
    private static double[] lose() {
        double[] b = Dsp.buffer(1.8);
        double[] notes = {110.0, 87.31, 73.42};
        for (int i = 0; i < notes.length; i++) {
            Dsp.mix(b, Dsp.ksPluck(notes[i], 1.4, 0.992, 0.35),
                    (int) (RATE * (0.05 + i * 0.26)), 0.75);
        }
        return Dsp.normalize(b, 0.9);
    }

    /** 平局：中性 KS 双音。 */
    private static double[] draw() {
        double[] b = Dsp.buffer(1.2);
        Dsp.mix(b, Dsp.ksPluck(440.0, 1.0, 0.997, 0.6), 0, 0.55);
        Dsp.mix(b, Dsp.ksPluck(659.26, 1.0, 0.997, 0.6), (int) (RATE * 0.18), 0.5);
        return Dsp.normalize(b, 0.85);
    }

    /** 按钮：木琴双音（两组随机）。 */
    private static double[] click() {
        double[] b = Dsp.buffer(0.12);
        boolean alt = DspSeed.next() < 0.5;
        double f1 = alt ? 880 : 988, f2 = alt ? 1320 : 1480;
        Dsp.addTone(b, 0, f1, 0.05, 0.6, 0.028, 0.001);
        Dsp.addTone(b, (int) (RATE * 0.03), f2, 0.06, 0.45, 0.03, 0.001);
        Dsp.addNoise(b, 0, 0.002, 0.25, 0.001, 0.0005);
        return Dsp.normalize(b, 0.7);
    }

    /** 悬停：极短噪声 tick。 */
    private static double[] hover() {
        double[] b = Dsp.buffer(0.02);
        Dsp.addNoise(b, 0, 0.004, 0.6, 0.002, 0.0005);
        return Dsp.normalize(b, 0.5);
    }

    /** 悔棋：下滑音 + 轻噪尾。 */
    private static double[] undo() {
        double[] b = Dsp.buffer(0.22);
        Dsp.addSweep(b, 0, 520, 330, 0.12, 0.6, 0.09);
        Dsp.addNoise(b, (int) (RATE * 0.10), 0.05, 0.15, 0.02, 0.004);
        return Dsp.normalize(b, 0.8);
    }

    /** 非法落子：双低音警告。 */
    private static double[] invalid() {
        double[] b = Dsp.buffer(0.20);
        Dsp.addHarmonicTone(b, 0, 150, 0.10, 0.55, 0.05, 0.002, new double[]{0, 0, 0.33});
        Dsp.addHarmonicTone(b, (int) (RATE * 0.07), 118, 0.12, 0.55, 0.06, 0.002, new double[]{0, 0, 0.33});
        return Dsp.normalize(b, 0.75);
    }

    /** 太鼓握子。 */
    private static double[] guessHold() {
        double[] b = Dsp.buffer(0.40);
        Dsp.addTone(b, 0, 92, 0.22, 0.85, 0.09, 0.002);
        Dsp.addNoise(b, 0, 0.006, 0.3, 0.003, 0.001);
        Dsp.addTone(b, 0, 46, 0.30, 0.5, 0.12, 0.002);
        return Dsp.normalize(b, 0.92);
    }

    /** 木质 clack。 */
    private static double[] guessPick() {
        double[] b = Dsp.buffer(0.14);
        Dsp.addBandBurst(b, 0, 1400, 0.006, 0.7, 0.003);
        Dsp.addTone(b, 0, 620, 0.07, 0.6, 0.025, 0.001);
        return Dsp.normalize(b, 0.8);
    }

    /** 三连鼓揭晓 + 低鼓长尾。 */
    private static double[] guessReveal() {
        double[] b = Dsp.buffer(0.95);
        double[] f = {96, 84, 72};
        for (int i = 0; i < 3; i++) {
            int at = (int) (RATE * i * 0.09);
            Dsp.addTone(b, at, f[i], 0.20, 0.8, 0.08, 0.002);
            Dsp.addNoise(b, at, 0.006, 0.28, 0.003, 0.001);
        }
        Dsp.addTone(b, (int) (RATE * 0.27), 60, 0.45, 0.85, 0.25, 0.004);
        Dsp.addNoise(b, (int) (RATE * 0.27), 0.08, 0.3, 0.03, 0.002);
        return Dsp.normalize(b, 0.95);
    }

    /** 猜先胜：KS 上行 G4 A4 D5。 */
    private static double[] guessResultWin() {
        double[] b = Dsp.buffer(1.1);
        double[] notes = {392.0, 440.0, 587.33};
        for (int i = 0; i < notes.length; i++) {
            Dsp.mix(b, Dsp.ksPluck(notes[i], 0.9, 0.9975, 0.6),
                    (int) (RATE * (0.03 + i * 0.09)), 0.55);
        }
        return Dsp.normalize(b, 0.9);
    }

    /** 猜先败：KS 下行两音。 */
    private static double[] guessResultLose() {
        double[] b = Dsp.buffer(1.0);
        double[] notes = {246.94, 196.0};
        for (int i = 0; i < notes.length; i++) {
            Dsp.mix(b, Dsp.ksPluck(notes[i], 0.9, 0.994, 0.45),
                    (int) (RATE * (0.05 + i * 0.16)), 0.55);
        }
        return Dsp.normalize(b, 0.78);
    }

    /** 切页 whoosh：噪声 lowpass 扫频 900→250Hz。 */
    private static double[] pageSwitch() {
        int n = (int) (RATE * 0.18);
        double[] b = new double[n];
        double v = 0;
        for (int i = 0; i < n; i++) {
            double fc = 900 + (250 - 900) * i / n;
            double g = 1 - Math.exp(-2 * Math.PI * fc / RATE);
            v = v + g * ((DspSeed.next() * 2 - 1) - v);
            double env = Math.min(1, i / (0.02 * RATE));
            if (i > n - 0.06 * RATE) {
                env *= (n - i) / (0.06 * RATE);
            }
            b[i] = v * env * 1.6;
        }
        return Dsp.normalize(b, 0.55);
    }

    /** 环境风（原落叶沙沙，语义升级）。 */
    private static double[] leafRustle() {
        double[] b = Dsp.brownNoise(1.2, 0.90);
        Dsp.fadeEdges(b, 0.15);
        return Dsp.normalize(b, 0.5);
    }

    // ---------- BGM v2（spec2 §5.4） ----------

    /** 中国风：D 宫五声古筝 AABA' 八小节 + 分解和弦 + 笛声 + 溪流底噪，约 30s 无缝循环。 */
    private static double[] bgmChinese() {
        double beat = 60.0 / 64;          // BPM 64
        int totalBeats = 32;              // 8 小节 4/4
        double sec = totalBeats * beat;   // 30s
        double[] b = Dsp.buffer(sec);

        // D 宫五声音高表
        double D3 = 146.83, A3 = 220.0, D4 = 293.66, E4 = 329.63, FS4 = 369.99, A4 = 440.0,
                B4 = 493.88, D5 = 587.33, E5 = 659.26, FS5 = 739.99, A5 = 880.0, B5 = 987.77;
        // {freq, startBeat, durBeats} —— A 段主题（4 小节）
        double[][] melA = {
                {D5, 0, 1}, {E5, 1, 0.5}, {FS5, 1.5, 0.5}, {A5, 2, 1}, {B5, 3, 1},
                {A5, 4, 1}, {FS5, 5, 1}, {E5, 6, 1}, {D5, 7, 1},
                {E5, 8, 1}, {FS5, 9, 0.5}, {E5, 9.5, 0.5}, {D5, 10, 1}, {B4, 11, 1},
                {D5, 12, 2}, {A4, 14, 1}, {E5, 15, 1}};
        // B 段对比句（2 小节）
        double[][] melB = {
                {B5, 0, 1}, {A5, 1, 1}, {FS5, 2, 1}, {A5, 3, 1},
                {E5, 4, 1}, {FS5, 5, 0.5}, {E5, 5.5, 0.5}, {D5, 6, 2}};
        // 曲式 A(16拍) + B(+16起,8拍) + A'(前半再现,+24起,8拍)
        playPhrase(b, melA, 0, beat);
        playPhrase(b, melB, 16, beat);
        double[][] melA2 = new double[melA.length][];
        int kept = 0;
        for (double[] n : melA) {
            if (n[1] < 8) {
                melA2[kept++] = n;
            }
        }
        playPhrase(b, java.util.Arrays.copyOf(melA2, kept), 24, beat);

        // 分解和弦伴奏：每小节第 1 拍 D3-A3-D4
        for (int bar = 0; bar < 8; bar++) {
            int at = (int) (bar * 4 * beat * RATE);
            double[] notes = {D3, A3, D4};
            for (int i = 0; i < notes.length; i++) {
                Dsp.mix(b, Dsp.ksPluck(notes[i], 0.9, 0.996, 0.55),
                        at + (int) (i * 0.12 * RATE), 0.18);
            }
        }

        // 笛声副旋律（正弦+vibrato）：B 段与 A' 各一长音
        Dsp.addFlute(b, (int) (18 * beat * RATE), D5, 2 * beat, 0.12);
        Dsp.addFlute(b, (int) (22 * beat * RATE), E4, 2 * beat, 0.10);
        Dsp.addFlute(b, (int) (26 * beat * RATE), D5, 2 * beat, 0.12);

        // 溪流底噪
        double[] stream = Dsp.brownNoise(sec, 0.985);
        for (int i = 0; i < b.length; i++) {
            b[i] += stream[i] * 0.05;
        }

        Dsp.loopCrossfade(b, 0.10); // 首尾 crossfade 保证无缝循环
        return Dsp.normalize(b, 0.55);
    }

    private static void playPhrase(double[] dst, double[][] notes, int beatOffset, double beat) {
        for (double[] n : notes) {
            double freq = n[0];
            double startBeat = beatOffset + n[1];
            double durSec = n[2] * beat + 1.2; // 余韵
            int at = (int) (startBeat * beat * RATE);
            if (at >= dst.length) {
                continue;
            }
            Dsp.mix(dst, Dsp.ksPluck(freq, durSec, 0.998, 0.6), at, 0.5);
        }
    }

    /** 森林自然音：棕噪溪流 + FM 鸟鸣三组 + 风铃一处，12s 无缝循环。 */
    private static double[] bgmForest() {
        double sec = 12.0;
        int len = (int) (sec * RATE);
        double[] b = Dsp.brownNoise(sec, 0.986);
        for (int i = 0; i < len; i++) {
            double lfo = 0.75 + 0.25 * Math.sin(2 * Math.PI * i / RATE * 0.18);
            b[i] *= lfo;
        }
        // FM 鸟鸣：carrier 滑频 + 38Hz 调制
        double[] groups = {1.5, 5.2, 8.6};
        for (double gAt : groups) {
            int chips = 2 + (int) (DspSeed.next() * 2);
            for (int c = 0; c < chips; c++) {
                fmChirp(b, (int) ((gAt + c * 0.19) * RATE));
            }
        }
        // 风铃：高频 KS 泛音簇
        double[] chime = {2093.0, 2349.3, 2637.0, 3136.0};
        for (int i = 0; i < chime.length; i++) {
            Dsp.mix(b, Dsp.ksPluck(chime[i], 2.0, 0.9985, 0.7),
                    (int) (6.0 * RATE + i * 0.04 * RATE), 0.06);
        }
        Dsp.loopCrossfade(b, 0.10);
        return Dsp.normalize(b, 0.5);
    }

    /** 单声鸟鸣 chip：carrier 2400~3100Hz 滑 ±20%，调制 38Hz index 3~5。 */
    private static void fmChirp(double[] dst, int start) {
        int n = (int) (0.18 * RATE);
        if (start + n >= dst.length) {
            return;
        }
        double fc = 2400 + DspSeed.next() * 700;
        double glide = (DspSeed.next() < 0.5 ? -1 : 1) * fc * 0.20;
        double index = 3 + DspSeed.next() * 2;
        double phase = 0, mPhase = 0;
        for (int i = 0; i < n; i++) {
            double env = Math.sin(Math.PI * i / n); // 纺锤包络
            double f = fc + glide * i / n;
            double mod = index * Math.sin(mPhase);
            dst[start + i] += 0.14 * env * Math.sin(phase + mod);
            phase += 2 * Math.PI * f / RATE;
            mPhase += 2 * Math.PI * 38 / RATE;
        }
    }

    /** 局部确定性随机（每次合成调用推进，缓存后不再变化）。 */
    private static final class DspSeed {
        private static final java.util.Random R = new java.util.Random(20260821);

        static double next() {
            return R.nextDouble();
        }
    }

    // ---------- WAV 写出（立体声 16bit PCM） ----------

    private static byte[] wavBytesStereo(double[] l, double[] r) throws IOException {
        int frames = Math.min(l.length, r.length);
        int dataSize = frames * 4;
        ByteArrayOutputStream out = new ByteArrayOutputStream(dataSize + 44);
        writeAscii(out, "RIFF");
        writeIntLE(out, 36 + dataSize);
        writeAscii(out, "WAVE");
        writeAscii(out, "fmt ");
        writeIntLE(out, 16);
        writeShortLE(out, 1);      // PCM
        writeShortLE(out, 2);      // stereo
        writeIntLE(out, RATE);
        writeIntLE(out, RATE * 4); // byte rate
        writeShortLE(out, 4);      // block align
        writeShortLE(out, 16);     // bits
        writeAscii(out, "data");
        writeIntLE(out, dataSize);
        for (int i = 0; i < frames; i++) {
            int lv = (int) Math.max(-32768, Math.min(32767, l[i] * 32767));
            int rv = (int) Math.max(-32768, Math.min(32767, r[i] * 32767));
            out.write(lv & 0xFF);
            out.write((lv >> 8) & 0xFF);
            out.write(rv & 0xFF);
            out.write((rv >> 8) & 0xFF);
        }
        return out.toByteArray();
    }

    private static void writeAscii(ByteArrayOutputStream out, String s) throws IOException {
        out.write(s.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    private static void writeIntLE(ByteArrayOutputStream out, int v) {
        out.write(v & 0xFF);
        out.write((v >> 8) & 0xFF);
        out.write((v >> 16) & 0xFF);
        out.write((v >> 24) & 0xFF);
    }

    private static void writeShortLE(ByteArrayOutputStream out, int v) {
        out.write(v & 0xFF);
        out.write((v >> 8) & 0xFF);
    }
}
