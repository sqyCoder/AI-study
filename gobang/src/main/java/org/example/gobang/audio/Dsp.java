package org.example.gobang.audio;

import java.util.Random;

/**
 * DSP 工具箱（spec2 §5）：包络振荡器 / 噪声 / Karplus-Strong 物理拨弦 /
 * Schroeder 混响（4 comb → 2 allpass，双声道去相关）/ tanh 软限幅 /
 * brown noise / 带通瞬态模拟。全部静态纯函数，采样率固定 44100。
 */
public final class Dsp {

    public static final int RATE = 44100;

    private static final Random RND = new Random(20260821);

    private Dsp() {
    }

    /** 重置随机种子（BGM 等需要确定性输出时调用）。 */
    public static void seed(long s) {
        RND.setSeed(s);
    }

    public static double[] buffer(double sec) {
        return new double[(int) (RATE * sec)];
    }

    // ---------- 包络振荡器 ----------

    /** 指数衰减正弦（含 attack），写入 buf[start...]。 */
    public static void addTone(double[] buf, int start, double freq, double durSec,
                               double amp, double decaySec, double attackSec) {
        int n = Math.min((int) (durSec * RATE), buf.length - start);
        if (n <= 0) {
            return;
        }
        double tau = Math.max(1, decaySec * RATE);
        double att = Math.max(1, attackSec * RATE);
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double env = Math.exp(-i / tau);
            if (i < att) {
                env *= i / att;
            }
            buf[start + i] += amp * env * Math.sin(phase);
            phase += 2 * Math.PI * freq / RATE;
        }
    }

    /** 正弦+整数次谐波族（木琴/低音鼓体感）。 */
    public static void addHarmonicTone(double[] buf, int start, double freq, double durSec,
                                       double amp, double decaySec, double attackSec, double[] harmAmps) {
        addTone(buf, start, freq, durSec, amp, decaySec, attackSec);
        for (int h = 1; h < harmAmps.length; h++) {
            if (harmAmps[h] > 0) {
                addTone(buf, start, freq * (h + 1), durSec, amp * harmAmps[h],
                        decaySec * (1.0 / (h + 1)), attackSec);
            }
        }
    }

    /** 频率滑音正弦。 */
    public static void addSweep(double[] buf, int start, double f0, double f1, double durSec,
                                double amp, double decaySec) {
        int n = Math.min((int) (durSec * RATE), buf.length - start);
        if (n <= 0) {
            return;
        }
        double tau = Math.max(1, decaySec * RATE);
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double freq = f0 + (f1 - f0) * i / n;
            buf[start + i] += amp * Math.exp(-i / tau) * Math.sin(phase);
            phase += 2 * Math.PI * freq / RATE;
        }
    }

    /** 笛声：正弦 + vibrato（spec2 §5.4）。 */
    public static void addFlute(double[] buf, int start, double freq, double durSec, double amp) {
        int n = Math.min((int) (durSec * RATE), buf.length - start);
        if (n <= 0) {
            return;
        }
        double att = 0.15 * RATE;
        double rel = 0.3 * RATE;
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double vib = 1 + 0.004 * Math.sin(2 * Math.PI * 5.5 * i / RATE);
            double env = 1;
            if (i < att) {
                env = i / att;
            } else if (i > n - rel) {
                env = Math.max(0, (n - i) / rel);
            }
            buf[start + i] += amp * env * Math.sin(phase);
            phase += 2 * Math.PI * freq * vib / RATE;
        }
    }

    // ---------- 噪声 ----------

    /** 白噪声爆发（attack+指数衰减）。 */
    public static void addNoise(double[] buf, int start, double durSec,
                                double amp, double decaySec, double attackSec) {
        int n = Math.min((int) (durSec * RATE), buf.length - start);
        if (n <= 0) {
            return;
        }
        double tau = Math.max(1, decaySec * RATE);
        double att = Math.max(1, attackSec * RATE);
        for (int i = 0; i < n; i++) {
            double env = Math.exp(-i / tau);
            if (i < att) {
                env *= i / att;
            }
            buf[start + i] += amp * env * (RND.nextDouble() * 2 - 1);
        }
    }

    /** 带通瞬态模拟：噪声 × 同频环形正弦（敲击 click 的频谱感）。 */
    public static void addBandBurst(double[] buf, int start, double centerFreq, double durSec,
                                    double amp, double tau) {
        int n = Math.min((int) (durSec * RATE), buf.length - start);
        if (n <= 0) {
            return;
        }
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double env = Math.exp(-i / (tau * RATE));
            double ring = Math.sin(phase);
            buf[start + i] += amp * env * ring * (RND.nextDouble() * 2 - 1) * 1.4;
            phase += 2 * Math.PI * centerFreq / RATE;
        }
    }

    /** brown noise（积分白噪），leak 越小越闷。 */
    public static double[] brownNoise(double sec, double leak) {
        int n = (int) (sec * RATE);
        double[] b = new double[n];
        double v = 0;
        for (int i = 0; i < n; i++) {
            v = v * leak + (RND.nextDouble() * 2 - 1) * (1 - leak) * 4;
            b[i] = v;
        }
        return b;
    }

    // ---------- Karplus-Strong 拨弦（spec2 §5.2） ----------

    /**
     * KS 物理拨弦：环形延迟线 + 低通平均反馈；双弦 detune 合成合唱感。
     *
     * @param lpG 激励预滤系数（越小越闷：古琴 0.35 / 古筝 0.6）
     * @param damp 反馈衰减（古筝 0.998 / 古琴 0.992）
     */
    public static double[] ksPluck(double freq, double durSec, double damp, double lpG) {
        int n = (int) (durSec * RATE);
        double[] out = new double[n];
        // 主弦与两根 detune ±0.15% 弦各 1/3 混合（合唱效应）
        double detune = freq * 0.0015;
        double[] main = ksString(freq, durSec, damp, lpG);
        double[] s2 = ksString(freq + detune, durSec, damp, lpG);
        double[] s3 = ksString(freq - detune, durSec, damp, lpG);
        for (int i = 0; i < n; i++) {
            out[i] = (main[i] + s2[i] + s3[i]) / 3;
        }
        return out;
    }

    private static double[] ksString(double freq, double durSec, double damp, double lpG) {
        int n = (int) (durSec * RATE);
        double[] out = new double[n];
        int N = Math.max(2, (int) Math.round(RATE / freq));
        double[] buf = new double[N];
        // 激励：白噪声经一阶低通预滤（软化击弦瞬态）
        double prev = 0;
        for (int i = 0; i < N; i++) {
            double w = RND.nextDouble() * 2 - 1;
            prev = prev + lpG * (w - prev);
            buf[i] = prev;
        }
        int p = 0;
        for (int i = 0; i < n; i++) {
            double cur = buf[p];
            int nxt = (p + 1) % N;
            buf[p] = damp * (buf[p] + buf[nxt]) * 0.5;
            out[i] = cur;
            p = nxt;
        }
        return out;
    }

    /** 把已生成的拨弦片段叠加进目标缓冲。 */
    public static void mix(double[] dst, double[] src, int start, double amp) {
        int n = Math.min(src.length, dst.length - start);
        for (int i = 0; i < n; i++) {
            dst[start + i] += src[i] * amp;
        }
    }

    // ---------- Schroeder 混响（spec2 §5.1） ----------

    private static final int[] COMB_DELAYS = {1557, 1617, 1491, 1422};
    private static final double[] COMB_FB = {0.80, 0.79, 0.81, 0.78};
    private static final int[] AP_DELAYS = {347, 113};
    private static final double AP_GAIN = 0.7;

    /**
     * 干/湿混合混响。shift 为右声道去相关延迟样本数（0=左声道）。
     * 输出 = dry + wet × verb(input)，已软限幅。
     */
    public static double[] reverb(double[] dry, double wet, int shift) {
        int n = dry.length;
        double[] w = new double[n + shift];
        System.arraycopy(dry, 0, w, shift, n);
        // 并联 4 comb
        double[] sum = new double[w.length];
        for (int c = 0; c < COMB_DELAYS.length; c++) {
            double[] t = w.clone();
            comb(t, COMB_DELAYS[c], COMB_FB[c]);
            for (int i = 0; i < sum.length; i++) {
                sum[i] += t[i];
            }
        }
        for (int i = 0; i < sum.length; i++) {
            sum[i] *= 0.25;
        }
        // 串联 2 allpass
        allpass(sum, AP_DELAYS[0], AP_GAIN);
        allpass(sum, AP_DELAYS[1], AP_GAIN);

        double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            out[i] = dry[i] + wet * sum[i];
        }
        return softClip(out);
    }

    private static void comb(double[] b, int delay, double fb) {
        for (int i = delay; i < b.length; i++) {
            b[i] += fb * b[i - delay];
        }
    }

    private static void allpass(double[] b, int delay, double gain) {
        int n = b.length;
        double[] x = b.clone();
        // 标准全通：y[n] = -g*x[n] + x[n-D] + g*y[n-D]
        for (int i = delay; i < n; i++) {
            b[i] = -gain * x[i] + x[i - delay] + gain * b[i - delay];
        }
    }

    // ---------- 后处理 ----------

    /** tanh 软限幅防爆音。 */
    public static double[] softClip(double[] b) {
        double k = Math.tanh(1.5);
        for (int i = 0; i < b.length; i++) {
            b[i] = Math.tanh(1.5 * b[i]) / k;
        }
        return b;
    }

    public static double[] normalize(double[] b, double peak) {
        double max = 0;
        for (double v : b) {
            max = Math.max(max, Math.abs(v));
        }
        if (max <= 0) {
            return b;
        }
        double k = peak / max;
        for (int i = 0; i < b.length; i++) {
            b[i] *= k;
        }
        return b;
    }

    /** 首尾淡入淡出防循环爆音。 */
    public static void fadeEdges(double[] b, double sec) {
        int f = (int) (sec * RATE);
        f = Math.min(f, b.length / 2);
        for (int i = 0; i < f; i++) {
            double k = (double) i / f;
            b[i] *= k;
            b[b.length - 1 - i] *= k;
        }
    }

    /** 循环无缝：尾部 crossfade 到头部（长度 sec）。 */
    public static void loopCrossfade(double[] b, double sec) {
        int f = (int) (sec * RATE);
        if (f * 2 >= b.length) {
            return;
        }
        for (int i = 0; i < f; i++) {
            double k = (double) i / f;
            b[i] = b[i] * k + b[b.length - f + i] * (1 - k);
        }
        // 尾部截掉已混合段
        double[] out = new double[b.length - f];
        System.arraycopy(b, 0, out, 0, out.length);
        System.arraycopy(out, 0, b, 0, out.length);
        for (int i = out.length; i < b.length; i++) {
            b[i] = 0;
        }
    }
}
