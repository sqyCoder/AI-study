package org.example.gobang.fx;

import javafx.animation.AnimationTimer;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;

/**
 * 落子物理动效（spec2 §4.1，总时长 360ms）：
 * 下落挤压(0~70) → 接触帧[音效/双涟漪/火星/微震/阴影渐现](70)
 * → 回弹(70~200) → 沉降稳定(200~360) → 并入静态层。
 * AI 与人类落子共用；悔棋/重开走 cancel() 立即还原一致状态。
 */
public final class StoneAnimator {

    /** 宿主回调（由 GameView 实现）。 */
    public interface Host {
        GraphicsContext fx();

        /** 重绘静态棋子层但排除最后一手（动画层接管它）。 */
        void renderStonesExcludingLast();

        /** 用标准材质画一颗子（cx,cy 为几何中心）。 */
        void paintStoneAt(GraphicsContext g, double cx, double cy, int color, double alpha);

        /** 接触帧反馈：落子音效 + SPARK 粒子 + 棋盘微震。 */
        void onImpact(double cx, double cy, int color);

        /** 动画自然结束（并入静态层）。 */
        void onDone();
    }

    private static final int DROP_MS = 70;
    private static final int BOUNCE_MS = 130;   // 70~200
    private static final int SETTLE_MS = 160;   // 200~360
    private static final int TOTAL_MS = DROP_MS + BOUNCE_MS + SETTLE_MS;
    private static final int RIPPLE_MS = 320;

    private final CanvasFx canvas;
    private AnimationTimer timer;
    private long startNanos;
    private boolean active;
    private boolean impactFired;
    private double cx;
    private double cy;
    private int color;
    private Host host;

    /** 仅包裹 clear 尺寸，避免直接依赖具体 Canvas 类型。 */
    public interface CanvasFx {
        GraphicsContext g();

        void clear();
    }

    public StoneAnimator(CanvasFx canvas) {
        this.canvas = canvas;
    }

    public boolean isActive() {
        return active;
    }

    public void play(double cx, double cy, int color, Host host) {
        cancel();
        this.cx = cx;
        this.cy = cy;
        this.color = color;
        this.host = host;
        this.impactFired = false;
        host.renderStonesExcludingLast();
        active = true;
        startNanos = -1;
        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                frame(now);
            }
        };
        timer.start();
    }

    /** 立即停止并清屏（不回调 onDone，由调用方负责 renderStones 还原）。 */
    public void cancel() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
        if (active) {
            canvas.clear();
        }
        active = false;
    }

    private void frame(long now) {
        if (startNanos < 0) {
            startNanos = now;
        }
        long t = (now - startNanos) / 1_000_000;

        if (!impactFired && t >= DROP_MS) {
            impactFired = true;
            host.onImpact(cx, cy, color);
        }
        if (t >= TOTAL_MS) {
            finish();
            return;
        }

        // ---- 姿态插值 ----
        double offsetY;
        double scale;
        double alpha;
        if (t < DROP_MS) {
            double k = easeOutQuad(t / (double) DROP_MS);
            offsetY = -14 * (1 - k);
            scale = 1.18 - (1.18 - 0.94) * k;
            alpha = Math.min(1, t / 30.0);
        } else if (t < DROP_MS + BOUNCE_MS) {
            double p = (t - DROP_MS) / (double) BOUNCE_MS;
            offsetY = 0;
            alpha = 1;
            scale = 0.94 + (1.06 - 0.94) * Theme.easeOutBack(p);
        } else {
            double p = (t - DROP_MS - BOUNCE_MS) / (double) SETTLE_MS;
            offsetY = 0;
            alpha = 1;
            scale = 1.06 + (1.0 - 1.06) * easeOutQuad(p);
        }

        // 阴影参数：offset 6→3 / alpha .45→.32
        double sk = Math.min(1, t / (double) TOTAL_MS);
        double shadowOff = 6 - 3 * sk;
        double shadowAlpha = 0.45 - 0.13 * sk;

        GraphicsContext g = canvas.g();
        canvas.clear();

        // 双涟漪
        drawRipple(g, t, DROP_MS, 0.55);
        drawRipple(g, t, DROP_MS + 70, 0.40);

        // 软阴影
        double sr = GameView.STONE_R * 1.05;
        g.setFill(new RadialGradient(0, 0, cx + shadowOff * 0.5, cy + shadowOff * 0.7, sr,
                false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(0, 0, 0, shadowAlpha)),
                new Stop(1, Color.rgb(0, 0, 0, 0))));
        g.fillOval(cx - sr + shadowOff * 0.5, cy - sr + shadowOff * 0.7, sr * 2, sr * 2);

        // 棋子本体（缩放/位移变换）
        g.save();
        g.translate(cx, cy + offsetY);
        g.scale(scale, scale);
        host.paintStoneAt(g, 0, 0, color, alpha);
        g.restore();
    }

    private void drawRipple(GraphicsContext g, long t, int startMs, double a0) {
        if (t < startMs) {
            return;
        }
        double rp = Math.min(1, (t - startMs) / (double) RIPPLE_MS);
        double r = GameView.STONE_R + 13 * rp;
        g.setStroke(Color.web(Theme.GOLD_BRIGHT, a0 * (1 - rp)));
        g.setLineWidth(3 - 2 * rp);
        g.strokeOval(cx - r, cy - r, r * 2, r * 2);
    }

    private void finish() {
        timer.stop();
        timer = null;
        active = false;
        canvas.clear();
        Host h = host;
        host = null;
        if (h != null) {
            h.onDone();
        }
    }

    private static double easeOutQuad(double t) {
        return 1 - (1 - t) * (1 - t);
    }
}
