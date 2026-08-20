package org.example.gobang.audio;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;

/**
 * 素材缺失时的回退方案（spec §9）：用 Java 纯代码合成全部音效与 BGM，
 * 写入临时目录缓存为 WAV 文件，游戏照常可玩且音效完整。
 */
public final class SynthWav {

    public static final int RATE = 44100;
    private static final Path DIR = Paths.get(System.getProperty("java.io.tmpdir"), "gobang_synth_v3");
    private static final Random RND = new Random(20260820);

    private SynthWav() {
    }

    /** 返回可交给 javafx.scene.media.Media 的 URL；生成失败返回 null。 */
    public static String urlFor(String fileName) {
        try {
            Files.createDirectories(DIR);
            Path f = DIR.resolve(fileName);
            if (!Files.exists(f)) {
                double[] samples = synthesize(fileName);
                if (samples == null) return null;
                Files.write(f, wavBytes(samples));
            }
            return f.toUri().toString();
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static double[] synthesize(String name) {
        if (name.startsWith("stone_black_")) return stoneBlack();
        if (name.startsWith("stone_white_")) return stoneWhite();
        switch (name) {
            case "win.wav": return win();
            case "lose.wav": return lose();
            case "draw.wav": return draw();
            case "click.wav": return click();
            case "hover.wav": return hover();
            case "undo.wav": return undo();
            case "invalid.wav": return invalid();
            case "guess_hold.wav": return guessHold();
            case "guess_pick.wav": return guessPick();
            case "guess_reveal.wav": return guessReveal();
            case "guess_result_win.wav": return guessResultWin();
            case "guess_result_lose.wav": return guessResultLose();
            case "leaf_rustle.wav": return leafRustle();
            case "bgm_chinese.wav": return bgmChinese();
            case "bgm_forest.wav": return bgmForest();
            default: return null;
        }
    }

    // ---------- 基础合成单元 ----------

    private static double[] buffer(double sec) {
        return new double[(int) (RATE * sec)];
    }

    private static void addTone(double[] buf, int start, int dur, double freq, double amp, double decaySec, double attackSec) {
        int n = Math.min(dur, buf.length - start);
        if (n <= 0) return;
        double tau = Math.max(1, decaySec * RATE);
        double att = Math.max(1, attackSec * RATE);
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double env = Math.exp(-i / tau);
            if (i < att) env *= i / att;
            buf[start + i] += amp * env * Math.sin(phase);
            phase += 2 * Math.PI * freq / RATE;
        }
    }

    private static void addSweep(double[] buf, int start, int dur, double f0, double f1, double amp, double decaySec) {
        int n = Math.min(dur, buf.length - start);
        if (n <= 0) return;
        double tau = Math.max(1, decaySec * RATE);
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double freq = f0 + (f1 - f0) * i / n;
            buf[start + i] += amp * Math.exp(-i / tau) * Math.sin(phase);
            phase += 2 * Math.PI * freq / RATE;
        }
    }

    private static void addNoise(double[] buf, int start, int dur, double amp, double decaySec, double attackSec) {
        int n = Math.min(dur, buf.length - start);
        if (n <= 0) return;
        double tau = Math.max(1, decaySec * RATE);
        double att = Math.max(1, attackSec * RATE);
        for (int i = 0; i < n; i++) {
            double env = Math.exp(-i / tau);
            if (i < att) env *= i / att;
            buf[start + i] += amp * env * (RND.nextDouble() * 2 - 1);
        }
    }

    /** 木鱼式鼓点：低频正弦快衰减 + 噪声冲击。 */
    private static void addDrum(double[] buf, int start, double freq, double amp) {
        int dur = (int) (RATE * 0.28);
        addTone(buf, start, dur, freq, amp, 0.09, 0.002);
        addTone(buf, start, dur / 2, freq * 1.6, amp * 0.3, 0.05, 0.002);
        addNoise(buf, start, (int) (RATE * 0.05), amp * 0.35, 0.02, 0.002);
    }

    /** 古筝式拨弦：基频 + 泛音，指数衰减。 */
    private static void addPluck(double[] buf, int start, double freq, double amp) {
        int dur = (int) (RATE * 1.1);
        addTone(buf, start, dur, freq, amp, 0.5, 0.004);
        addTone(buf, start, dur, freq * 2, amp * 0.35, 0.3, 0.004);
        addTone(buf, start, dur, freq * 3, amp * 0.15, 0.2, 0.004);
    }

    private static void addChord(double[] buf, int start, double[] freqs, double amp) {
        for (double f : freqs) {
            addTone(buf, start, (int) (RATE * 0.6), f, amp, 0.25, 0.01);
        }
    }

    private static double[] normalize(double[] buf, double peak) {
        double max = 0;
        for (double v : buf) {
            max = Math.max(max, Math.abs(v));
        }
        if (max <= 0) return buf;
        double k = peak / max;
        for (int i = 0; i < buf.length; i++) {
            buf[i] *= k;
        }
        return buf;
    }

    // ---------- 各音效 ----------

    private static double[] stoneBlack() {
        double[] b = buffer(0.3);
        addTone(b, 0, (int) (RATE * 0.3), 96, 0.9, 0.09, 0.002);
        addTone(b, 0, (int) (RATE * 0.16), 192, 0.35, 0.05, 0.002);
        addNoise(b, 0, (int) (RATE * 0.03), 0.22, 0.012, 0.001);
        return normalize(b, 0.9);
    }

    private static double[] stoneWhite() {
        double[] b = buffer(0.18);
        addTone(b, 0, (int) (RATE * 0.16), 660, 0.7, 0.035, 0.002);
        addTone(b, 0, (int) (RATE * 0.08), 1320, 0.3, 0.02, 0.002);
        addNoise(b, 0, (int) (RATE * 0.012), 0.25, 0.006, 0.001);
        return normalize(b, 0.9);
    }

    private static double[] win() {
        double[] b = buffer(1.2);
        double[] notes = {523.25, 659.25, 783.99, 1046.5};
        for (int i = 0; i < notes.length; i++) {
            int start = (int) (RATE * (0.10 + i * 0.12));
            addTone(b, start, (int) (RATE * 0.5), notes[i], 0.5, 0.18, 0.008);
        }
        addChord(b, (int) (RATE * 0.58), notes, 0.4);
        return normalize(b, 0.9);
    }

    private static double[] lose() {
        double[] b = buffer(1.1);
        double[] notes = {392.0, 329.63, 261.63};
        for (int i = 0; i < notes.length; i++) {
            int start = (int) (RATE * (0.10 + i * 0.24));
            addTone(b, start, (int) (RATE * 0.5), notes[i], 0.5, 0.25, 0.01);
        }
        return normalize(b, 0.85);
    }

    private static double[] draw() {
        double[] b = buffer(0.7);
        addTone(b, 0, (int) (RATE * 0.35), 261.63, 0.5, 0.15, 0.008);
        addTone(b, (int) (RATE * 0.22), (int) (RATE * 0.4), 392.0, 0.5, 0.18, 0.008);
        return normalize(b, 0.85);
    }

    private static double[] click() {
        double[] b = buffer(0.06);
        addTone(b, 0, (int) (RATE * 0.05), 1800, 0.7, 0.015, 0.001);
        return normalize(b, 0.7);
    }

    private static double[] hover() {
        double[] b = buffer(0.05);
        addTone(b, 0, (int) (RATE * 0.04), 1400, 0.5, 0.012, 0.001);
        return normalize(b, 0.5);
    }

    private static double[] undo() {
        double[] b = buffer(0.22);
        addTone(b, 0, (int) (RATE * 0.07), 420, 0.6, 0.03, 0.003);
        addTone(b, (int) (RATE * 0.08), (int) (RATE * 0.1), 640, 0.55, 0.04, 0.003);
        return normalize(b, 0.8);
    }

    private static double[] invalid() {
        double[] b = buffer(0.14);
        addTone(b, 0, (int) (RATE * 0.13), 150, 0.5, 0.05, 0.002);
        addTone(b, 0, (int) (RATE * 0.1), 300, 0.35, 0.04, 0.002);
        addTone(b, 0, (int) (RATE * 0.08), 450, 0.25, 0.03, 0.002);
        return normalize(b, 0.7);
    }

    private static double[] guessHold() {
        double[] b = buffer(0.4);
        addDrum(b, 0, 90, 0.8);
        return normalize(b, 0.9);
    }

    private static double[] guessPick() {
        double[] b = buffer(0.16);
        addTone(b, 0, (int) (RATE * 0.06), 950, 0.6, 0.02, 0.002);
        addTone(b, (int) (RATE * 0.07), (int) (RATE * 0.08), 1450, 0.6, 0.025, 0.002);
        return normalize(b, 0.8);
    }

    private static double[] guessReveal() {
        double[] b = buffer(0.85);
        addDrum(b, 0, 115, 0.7);
        addDrum(b, (int) (RATE * 0.09), 100, 0.75);
        addDrum(b, (int) (RATE * 0.18), 85, 0.8);
        addTone(b, (int) (RATE * 0.27), (int) (RATE * 0.45), 65, 0.85, 0.22, 0.004);
        addNoise(b, (int) (RATE * 0.27), (int) (RATE * 0.08), 0.3, 0.03, 0.002);
        return normalize(b, 0.95);
    }

    private static double[] guessResultWin() {
        double[] b = buffer(0.9);
        double[] notes = {783.99, 987.77, 1174.66, 1567.98};
        for (int i = 0; i < notes.length; i++) {
            int start = (int) (RATE * (0.05 + i * 0.09));
            addTone(b, start, (int) (RATE * 0.4), notes[i], 0.5, 0.2, 0.006);
        }
        addChord(b, (int) (RATE * 0.42), notes, 0.35);
        return normalize(b, 0.9);
    }

    private static double[] guessResultLose() {
        double[] b = buffer(0.85);
        double[] notes = {330.0, 311.13, 293.66};
        for (int i = 0; i < notes.length; i++) {
            int start = (int) (RATE * (0.1 + i * 0.2));
            addTone(b, start, (int) (RATE * 0.45), notes[i], 0.5, 0.22, 0.01);
        }
        return normalize(b, 0.75);
    }

    private static double[] leafRustle() {
        double[] b = buffer(0.5);
        addNoise(b, 0, (int) (RATE * 0.5), 0.5, 0.2, 0.08);
        return normalize(b, 0.5);
    }

    /** 中国风 BGM：五声音阶拨弦旋律循环（末尾留静音段保证无缝循环）。 */
    private static double[] bgmChinese() {
        double[] b = buffer(11.5);
        double[] scale = {587.33, 659.25, 783.99, 880.0, 1046.5, 1174.66}; // D5 E5 G5 A5 C6 D6
        int[] seq = {0, 1, 2, 3, 1, 4, 2, 5, 3, 2, 1, 0, 3, 1, 2, 4};
        for (int i = 0; i < seq.length; i++) {
            int start = (int) (RATE * (0.25 + i * 0.62));
            double amp = 0.32 + RND.nextDouble() * 0.12;
            addPluck(b, start, scale[seq[i]] / 2, amp); // 低八度拨弦
            if (i % 4 == 1) {
                addPluck(b, start + (int) (RATE * 0.31), scale[(seq[i] + 2) % scale.length] / 2, amp * 0.55);
            }
        }
        addNoise(b, 0, (int) (RATE * 0.12), 0.02, 0.05, 0.0); // 极轻的底噪
        return normalize(b, 0.55);
    }

    /** 森林自然音 BGM：棕色噪声溪流 + 鸟鸣，首尾淡入淡出保证无缝。 */
    private static double[] bgmForest() {
        int len = (int) (RATE * 9.0);
        double[] b = new double[len];
        double noise = 0;
        for (int i = 0; i < len; i++) {
            noise = noise * 0.985 + (RND.nextDouble() * 2 - 1) * 0.06;
            double lfo = 0.75 + 0.25 * Math.sin(2 * Math.PI * i / RATE * 0.22);
            b[i] = noise * lfo * 0.9;
        }
        // 鸟鸣：3 处，每处两声
        int[] at = {(int) (RATE * 1.6), (int) (RATE * 4.3), (int) (RATE * 6.8)};
        for (int a : at) {
            for (int k = 0; k < 2; k++) {
                int start = a + (int) (RATE * (0.12 * k));
                addSweep(b, start, (int) (RATE * 0.16), 2600, 3400, 0.16, 0.1);
                addSweep(b, start + (int) (RATE * 0.07), (int) (RATE * 0.14), 3200, 2800, 0.12, 0.09);
            }
        }
        // 首尾 50ms 淡入淡出防循环爆音
        int fade = (int) (RATE * 0.05);
        for (int i = 0; i < fade; i++) {
            b[i] *= (double) i / fade;
            b[len - 1 - i] *= (double) i / fade;
        }
        return normalize(b, 0.5);
    }

    // ---------- WAV 写出 ----------

    private static byte[] wavBytes(double[] samples) throws IOException {
        int dataSize = samples.length * 2;
        ByteArrayOutputStream out = new ByteArrayOutputStream(dataSize + 44);
        writeAscii(out, "RIFF");
        writeIntLE(out, 36 + dataSize);
        writeAscii(out, "WAVE");
        writeAscii(out, "fmt ");
        writeIntLE(out, 16);
        writeShortLE(out, 1); // PCM
        writeShortLE(out, 1); // mono
        writeIntLE(out, RATE);
        writeIntLE(out, RATE * 2); // byte rate
        writeShortLE(out, 2); // block align
        writeShortLE(out, 16); // bits
        writeAscii(out, "data");
        writeIntLE(out, dataSize);
        for (double s : samples) {
            int v = (int) Math.max(-32768, Math.min(32767, s * 32767));
            out.write(v & 0xFF);
            out.write((v >> 8) & 0xFF);
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