package org.example.gobang.fx;

import javafx.animation.AnimationTimer;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.StrokeLineCap;
import javafx.util.Duration;

import org.example.gobang.audio.SoundManager;
import org.example.gobang.audio.SoundType;
import org.example.gobang.model.Move;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 终局大片式演出（spec2 §4.4，吸收原 WinEffect 职责）：
 * WIN  —— 压暗→五连逐子点亮(90ms 步进)→光带扫描+Bloom 辉光
 *         →金色喷泉+全屏光晕+强震+胜利音(t=1100)；
 * LOSE —— 冷蓝光带 + 根节点下沉 + 低沉音；
 * DRAW —— 中性光尘缓落。
 * 主 AnimationTimer 合成全部 fxCanvas 元素；stop() 由 cleanupEffects 调用。
 */
public final class VictorySequence {

    public enum Kind {WIN, LOSE, DRAW}

    /** 宿主回调（GameView 实现）。 */
    public interface Host {
        /** 以 dimAlpha 重绘棋子层：五连子保持全亮，其余压暗。 */
        void dimStones(Set<Long> winKeys, double dimAlpha);

        void shake(double amp, double durMs);

        Node sinkNode();

        Particles particles();
    }

    private static final int FLASH_MS = 180;
    private static final int RING_MS = 260;
    private static final int STEP_MS = 90;
    private static final int BEAM_START = 600;
    private static final int BEAM_DUR = 500;
    private static final int PEAK_MS = 1100;
    private static final int HALO_MS = 750;

    private final javafx.scene.canvas.Canvas fxCanvas;
    private final Host host;
    private final double cell;
    private final double grid;
    private final double stoneR;

    private Kind kind;
    private double[][] pts;
    private Set<Long> winKeys;
    private AnimationTimer timer;
    private long startNanos = -1;
    private Timeline dimTl;
    private boolean peakFired;
    private final boolean[] sparkFired = new boolean[5];
    private boolean done;

    public VictorySequence(javafx.scene.canvas.Canvas fxCanvas, Host host,
                           double cell, double grid, double stoneR) {
        this.fxCanvas = fxCanvas;
        this.host = host;
        this.cell = cell;
        this.grid = grid;
        this.stoneR = stoneR;
    }

    public void play(Kind kind, List<Move> winLine) {
        stop();
        this.kind = kind;
        done = false;
        if (kind == Kind.DRAW) {
            host.particles().softFall();
            SoundManager.play(SoundType.DRAW);
            return;
        }
        pts = new double[winLine.size()][2];
        winKeys = new HashSet<>();
        for (int i = 0; i < winLine.size(); i++) {
            Move m = winLine.get(i);
            pts[i][0] = grid + m.col * cell;
            pts[i][1] = grid + m.row * cell;
            winKeys.add((long) m.row * 1000 + m.col);
        }
        // 压暗过渡 200ms（非胜子 → 0.38/0.45，四帧阶梯）
        double dimTo = kind == Kind.WIN ? 0.38 : 0.45;
        dimTl = new Timeline(
                new KeyFrame(Duration.ZERO, e -> host.dimStones(winKeys, 1)),
                new KeyFrame(Duration.millis(40), e -> host.dimStones(winKeys, 1 - (1 - dimTo) / 3)),
                new KeyFrame(Duration.millis(120), e -> host.dimStones(winKeys, 1 - 2 * (1 - dimTo) / 3)),
                new KeyFrame(Duration.millis(200), e -> host.dimStones(winKeys, dimTo)));
        dimTl.play();

        if (kind == Kind.LOSE) {
            SoundManager.play(SoundType.LOSE);
            // 根节点下沉 5px
            Node sink = host.sinkNode();
            Timeline sinkTl = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(sink.translateYProperty(), 0)),
                    new KeyFrame(Duration.millis(400),
                            new KeyValue(sink.translateYProperty(), 5)));
            sinkTl.play();
        }

        startNanos = -1;
        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                frame(now);
            }
        };
        timer.start();
    }

    public boolean isDone() {
        return done;
    }

    /** 完全清理：停表/清屏/复位下沉。 */
    public void stop() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
        if (dimTl != null) {
            dimTl.stop();
            dimTl = null;
        }
        Node sink = host.sinkNode();
        sink.setTranslateY(0);
        fxCanvas.getGraphicsContext2D().clearRect(0, 0, fxCanvas.getWidth(), fxCanvas.getHeight());
        done = true;
    }

    private void frame(long now) {
        if (startNanos < 0) {
            startNanos = now;
        }
        long t = (now - startNanos) / 1_000_000;
        GraphicsContext g = fxCanvas.getGraphicsContext2D();
        g.clearRect(0, 0, fxCanvas.getWidth(), fxCanvas.getHeight());

        Color beamColor = kind == Kind.WIN ? Color.web(Theme.GOLD_BRIGHT) : Color.web(Theme.FX_BLUE);

        // ---- 逐子点亮 ----
        for (int i = 0; i < pts.length && i < 5; i++) {
            int st = i * STEP_MS;
            if (!sparkFired[i] && t >= st) {
                sparkFired[i] = true;
                host.particles().sparks(pts[i][0], pts[i][1], 3);
            }
            if (t >= st && t <= st + FLASH_MS) {
                double p = (t - st) / (double) FLASH_MS;
                g.setGlobalAlpha(0.85 * (1 - p));
                g.setFill(Color.WHITE);
                g.fillOval(pts[i][0] - stoneR, pts[i][1] - stoneR, stoneR * 2, stoneR * 2);
                g.setGlobalAlpha(1);
            }
            if (t >= st && t <= st + RING_MS) {
                double p = (t - st) / (double) RING_MS;
                double r = stoneR + 11 * p;
                g.setStroke(Color.web(toHex(beamColor), 0.9 * (1 - p)));
                g.setLineWidth(3);
                g.strokeOval(pts[i][0] - r, pts[i][1] - r, r * 2, r * 2);
            }
        }

        // ---- 光带 ----
        if (t >= BEAM_START) {
            double p = Math.min(1, (t - BEAM_START) / (double) BEAM_DUR);
            drawBeam(g, beamColor, p);
        }

        // ---- 峰值事件 ----
        if (!peakFired && t >= PEAK_MS && kind == Kind.WIN) {
            peakFired = true;
            double mx = (pts[0][0] + pts[pts.length - 1][0]) / 2;
            double my = (pts[0][1] + pts[pts.length - 1][1]) / 2;
            host.particles().fountain(mx, my);
            host.particles().petalBurst(26);
            host.shake(8, 600);
            SoundManager.play(SoundType.WIN);
        }

        // ---- 全屏光晕脉冲 ----
        if (kind == Kind.WIN && t >= PEAK_MS && t <= PEAK_MS + HALO_MS) {
            double p = (t - PEAK_MS) / (double) HALO_MS;
            double mx = (pts[0][0] + pts[pts.length - 1][0]) / 2;
            double my = (pts[0][1] + pts[pts.length - 1][1]) / 2;
            double r = 500;
            g.setFill(new RadialGradient(0, 0, mx, my, r, false, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.web(Theme.GOLD_BRIGHT, 0.30 * (1 - p))),
                    new Stop(1, Color.web(Theme.GOLD_BRIGHT, 0))));
            g.fillOval(mx - r, my - r, r * 2, r * 2);
        }
    }

    /** 光带：p<1 扫描段（白头亮点），p=1 整条常亮三层手绘辉光（外柔光/中光/亮芯）。 */
    private void drawBeam(GraphicsContext g, Color color, double p) {
        g.setLineCap(StrokeLineCap.ROUND);
        double ex = pts[0][0], ey = pts[0][1];
        double tx = lerp(pts[0][0], pts[pts.length - 1][0], p);
        double ty = lerp(pts[0][1], pts[pts.length - 1][1], p);
        if (p < 1) {
            g.setStroke(Color.web(toHex(color), 0.85));
            g.setLineWidth(6);
            g.strokeLine(ex, ey, tx, ty);
            g.setFill(Color.WHITE);
            g.fillOval(tx - 4, ty - 4, 8, 8);
        } else {
            // 外层柔光
            g.setStroke(Color.web(toHex(color), 0.16));
            g.setLineWidth(16);
            strokePolyline(g);
            // 中层光晕
            g.setStroke(Color.web(toHex(color), 0.42));
            g.setLineWidth(9);
            strokePolyline(g);
            // 亮芯
            g.setStroke(Color.web("#fff6d8", 0.95));
            g.setLineWidth(3.5);
            strokePolyline(g);
        }
    }

    private void strokePolyline(GraphicsContext g) {
        g.beginPath();
        g.moveTo(pts[0][0], pts[0][1]);
        for (int i = 1; i < pts.length; i++) {
            g.lineTo(pts[i][0], pts[i][1]);
        }
        g.stroke();
    }

    private static double lerp(double a, double b, double k) {
        return a + (b - a) * k;
    }

    private static String toHex(Color c) {
        return String.format("#%02x%02x%02x",
                (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255));
    }
}
