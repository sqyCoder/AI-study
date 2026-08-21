package org.example.gobang.fx;

import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import org.example.gobang.audio.SoundManager;
import org.example.gobang.audio.SoundType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 统一粒子引擎（spec2 §4.7 + 森林修订版）：
 * 单例 AnimationTimer；对象池 300 预分配零 GC；
 * 类型：AMBIENT_DUST 花粉光尘（常驻12）/ LEAF 精修落叶（常驻10，带叶脉）
 * / SPARK 落子火星 / FOUNTAIN 胜利喷泉 / SOFT_FALL 平局光尘
 * / PETAL 金瓣（终局庆祝）。
 * 支持注册逐帧回调（最后一手呼吸标记复用同一 timer）。
 */
public final class Particles {

    private static final int POOL_SIZE = 300;
    private static final int DUST_COUNT = 12;
    private static final int LEAF_COUNT = 10;

    public static final int DUST = 0;
    public static final int SPARK = 1;
    public static final int FOUNTAIN = 2;
    public static final int SOFT_FALL = 3;
    public static final int LEAF = 4;
    public static final int PETAL = 5;

    private static final Color[] SPARK_COLORS = {
            Color.web("#ffd54a"), Color.web("#fff2cc")};
    private static final Color[] FOUNTAIN_COLORS = {
            Color.web("#ffd54a"), Color.web("#ffffff"), Color.web("#ffe9c0")};
    private static final Color[] LEAF_COLORS = {
            Color.web("#d4af37", 0.92),
            Color.web("#9bc44f", 0.92),
            Color.web("#a06c3c", 0.92),
            Color.web("#8fbf3f", 0.92)};
    private static final Color[] GOLD_PETAL_COLORS = {
            Color.web("#ffd54a"), Color.web("#ffe082"), Color.web("#fff2cc")};

    // ---- 单例 ----
    private static final Particles INSTANCE = new Particles();

    public static Particles get() {
        return INSTANCE;
    }

    private final Canvas canvas = new Canvas(800, 900);
    private final Random rnd = new Random();
    private final Particle[] pool = new Particle[POOL_SIZE];
    private final List<Particle> active = new ArrayList<>(POOL_SIZE);
    private final List<Runnable> frameHooks = new ArrayList<>();

    private final AnimationTimer timer = new AnimationTimer() {
        @Override
        public void handle(long now) {
            tick(now);
        }
    };

    private volatile boolean paused = false;
    private boolean running = false;
    private long lastNanos = -1;
    private long nextWind = 0;

    private static class Particle {
        int type;
        double x, y, vx, vy, g;
        double life, maxLife;
        double size, sizeEnd;
        double baseAlpha;
        double phase, swayAmp, swayPeriod;
        double rot, vr;      // 叶片自转
        double leafW, leafH; // 叶片外形
        Color color;
        Color vein;
    }

    private Particles() {
        canvas.setMouseTransparent(true);
        for (int i = 0; i < POOL_SIZE; i++) {
            pool[i] = new Particle();
        }
        for (int i = 0; i < DUST_COUNT; i++) {
            spawnDust(rnd.nextDouble() * 900);
        }
        for (int i = 0; i < LEAF_COUNT; i++) {
            spawnLeaf(rnd.nextDouble() * 900);
        }
    }

    public Canvas getCanvas() {
        return canvas;
    }

    /** 注册逐帧回调（FX 线程，在 timer 内执行）。 */
    public void addFrameHook(Runnable r) {
        frameHooks.add(r);
    }

    public synchronized void start() {
        if (!running) {
            running = true;
            lastNanos = -1;
            timer.start();
        }
    }

    public synchronized void stop() {
        running = false;
        timer.stop();
        clear();
    }

    public synchronized void setPaused(boolean p) {
        paused = p;
        lastNanos = -1;
    }

    public synchronized void clear() {
        active.clear();
        canvas.getGraphicsContext2D().clearRect(0, 0, 800, 900);
    }

    // ---------- 发射接口（窗口坐标 800×900） ----------

    /** 落子/点亮火星：径向散射向上偏置。 */
    public void sparks(double x, double y, int n) {
        for (int i = 0; i < n; i++) {
            Particle p = obtain(SPARK);
            if (p == null) {
                return;
            }
            double ang = -Math.PI / 2 + (rnd.nextDouble() - 0.5) * Math.PI * 1.2;
            double sp = 60 + rnd.nextDouble() * 80;
            p.x = x;
            p.y = y;
            p.vx = Math.cos(ang) * sp;
            p.vy = Math.sin(ang) * sp;
            p.g = 300;
            p.maxLife = 0.35 + rnd.nextDouble() * 0.15;
            p.life = p.maxLife;
            p.size = 3;
            p.sizeEnd = 0;
            p.baseAlpha = 0.9;
            p.color = SPARK_COLORS[rnd.nextInt(SPARK_COLORS.length)];
        }
    }

    /** 胜利金色喷泉：上抛扇形 70 颗。 */
    public void fountain(double x, double y) {
        for (int i = 0; i < 70; i++) {
            Particle p = obtain(FOUNTAIN);
            if (p == null) {
                return;
            }
            double ang = -Math.PI / 2 + (rnd.nextDouble() - 0.5) * (Math.PI / 3);
            double sp = 180 + rnd.nextDouble() * 140;
            p.x = x + (rnd.nextDouble() - 0.5) * 30;
            p.y = y;
            p.vx = Math.cos(ang) * sp;
            p.vy = Math.sin(ang) * sp;
            p.g = 420;
            p.maxLife = 0.9 + rnd.nextDouble() * 0.6;
            p.life = p.maxLife;
            p.size = 2 + rnd.nextDouble() * 2;
            p.sizeEnd = 0;
            p.baseAlpha = 0.95;
            p.color = FOUNTAIN_COLORS[rnd.nextInt(FOUNTAIN_COLORS.length)];
        }
    }

    /** 平局中性光尘缓落。 */
    public void softFall() {
        for (int i = 0; i < 20; i++) {
            Particle p = obtain(SOFT_FALL);
            if (p == null) {
                return;
            }
            p.x = 60 + rnd.nextDouble() * 680;
            p.y = -20 - rnd.nextDouble() * 200;
            p.vx = 0;
            p.vy = 15 + rnd.nextDouble() * 20;
            p.g = 0;
            p.maxLife = 30;
            p.life = 30;
            p.size = 1.5 + rnd.nextDouble() * 1.5;
            p.sizeEnd = p.size;
            p.baseAlpha = 0.25;
            p.phase = rnd.nextDouble() * Math.PI * 2;
            p.swayAmp = 8;
            p.swayPeriod = 2 + rnd.nextDouble() * 2;
            p.color = Color.web("#d9d4c8");
        }
    }

    /** 终局金瓣庆祝：自顶部撒落 n 片金色花瓣。 */
    public void petalBurst(int n) {
        for (int i = 0; i < n; i++) {
            Particle p = obtain(PETAL);
            if (p == null) {
                return;
            }
            initLeafShape(p, GOLD_PETAL_COLORS[rnd.nextInt(GOLD_PETAL_COLORS.length)],
                    Color.web("#b8860b", 0.5));
            p.x = rnd.nextDouble() * 800;
            p.y = -20 - rnd.nextDouble() * 260;
            p.vy = 60 + rnd.nextDouble() * 50;
            p.maxLife = 30;
            p.life = 30;
            p.baseAlpha = 0.95;
        }
    }

    // ---------- 落叶 / 光尘 ----------

    private void spawnDust(double startY) {
        Particle p = obtain(DUST);
        if (p == null) {
            return;
        }
        p.x = rnd.nextDouble() * 800;
        p.y = startY > 0 ? startY : 900 + rnd.nextDouble() * 100;
        p.vx = (rnd.nextDouble() - 0.5) * 12;
        p.vy = -(4 + rnd.nextDouble() * 6);
        p.g = 0;
        p.maxLife = -1; // 常驻
        p.life = -1;
        p.size = 1 + rnd.nextDouble() * 1.5;
        p.sizeEnd = p.size;
        p.baseAlpha = 0.05 + rnd.nextDouble() * 0.10;
        p.phase = rnd.nextDouble() * Math.PI * 2;
        p.swayAmp = 10 + rnd.nextDouble() * 8;
        p.swayPeriod = 3 + rnd.nextDouble() * 3;
        p.color = Color.web("#fff3d0");
    }

    private void spawnLeaf(double startY) {
        Particle p = obtain(LEAF);
        if (p == null) {
            return;
        }
        Color c = LEAF_COLORS[rnd.nextInt(LEAF_COLORS.length)];
        initLeafShape(p, c, c.deriveColor(0, 1, 0.45, 1));
        p.x = 10 + rnd.nextDouble() * 780;
        p.y = startY;
        p.vx = 0;
        p.vy = 30 + rnd.nextDouble() * 50;
        p.g = 0;
        p.maxLife = -1; // 常驻，落出屏后从顶部重生
        p.life = -1;
        p.baseAlpha = 0.92;
    }

    private void initLeafShape(Particle p, Color body, Color vein) {
        p.leafW = 10 + rnd.nextDouble() * 10;
        p.leafH = p.leafW * 0.55;
        p.rot = rnd.nextDouble() * Math.PI;
        p.vr = (rnd.nextBoolean() ? 1 : -1) * (0.8 + rnd.nextDouble() * 1.6);
        p.phase = rnd.nextDouble() * Math.PI * 2;
        p.swayAmp = 20 + rnd.nextDouble() * 20;
        p.swayPeriod = 1 + rnd.nextDouble() * 1.5;
        p.color = body;
        p.vein = vein;
    }

    // ---------- 主循环 ----------

    private Particle obtain(int type) {
        if (active.size() >= POOL_SIZE) {
            if (type != DUST) {
                return null;
            }
            // 池满时回收一颗最老的普通粒子给常驻尘
            for (int i = 0; i < active.size(); i++) {
                if (active.get(i).type != DUST) {
                    return active.remove(i);
                }
            }
            return null;
        }
        Particle p = pool[active.size()];
        p.type = type;
        active.add(p);
        return p;
    }

    private void tick(long now) {
        if (paused) {
            lastNanos = -1;
            return;
        }
        if (lastNanos < 0) {
            lastNanos = now;
            return;
        }
        double dt = Math.min((now - lastNanos) / 1e9, 0.05);
        lastNanos = now;

        long ms = System.currentTimeMillis();
        if (ms >= nextWind) {
            nextWind = ms + 8000 + rnd.nextInt(6000);
            SoundManager.play(SoundType.LEAF_RUSTLE, 0.10);
        }

        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, 800, 900);
        double t = ms / 1000.0;

        for (int i = active.size() - 1; i >= 0; i--) {
            Particle p = active.get(i);
            step(p, dt, t);
            boolean persistent = p.type == DUST || p.type == LEAF;
            boolean dead = false;
            if (persistent) {
                if (p.type == DUST && p.y < -12) {
                    respawnDust(p);
                } else if (p.type == LEAF && p.y > 930) {
                    respawnLeaf(p);
                }
            } else {
                p.life -= dt;
                dead = p.life <= 0 || p.y > 950;
            }
            draw(g, p, t);
            if (dead) {
                int last = active.size() - 1;
                active.set(i, active.get(last));
                active.remove(last);
            }
        }
        // 维持常驻落叶数量（池被挤占时补回）
        int leaves = 0;
        for (Particle p : active) {
            if (p.type == LEAF) {
                leaves++;
            }
        }
        while (leaves < LEAF_COUNT) {
            int before = active.size();
            spawnLeaf(-20 - rnd.nextDouble() * 60);
            if (active.size() == before) {
                break; // 池满且无可回收
            }
            leaves++;
        }
        for (Runnable r : frameHooks) {
            r.run();
        }
    }

    private void step(Particle p, double dt, double t) {
        p.vy += p.g * dt;
        p.x += p.vx * dt;
        p.y += p.vy * dt;
        if (p.type == DUST || p.type == SOFT_FALL || p.type == LEAF || p.type == PETAL) {
            p.phase += (2 * Math.PI / p.swayPeriod) * dt;
            p.x += Math.sin(p.phase) * p.swayAmp * dt;
        }
        if (p.type == LEAF || p.type == PETAL) {
            p.rot += p.vr * dt;
        }
    }

    private void respawnDust(Particle p) {
        p.x = rnd.nextDouble() * 800;
        p.y = 910;
        p.vx = (rnd.nextDouble() - 0.5) * 12;
        p.vy = -(4 + rnd.nextDouble() * 6);
    }

    private void respawnLeaf(Particle p) {
        Color c = LEAF_COLORS[rnd.nextInt(LEAF_COLORS.length)];
        initLeafShape(p, c, c.deriveColor(0, 1, 0.45, 1));
        p.x = 10 + rnd.nextDouble() * 780;
        p.y = -25;
        p.vy = 30 + rnd.nextDouble() * 50;
    }

    private void draw(GraphicsContext g, Particle p, double t) {
        if (p.type == LEAF || p.type == PETAL) {
            double k = p.maxLife > 0 ? Math.max(0, Math.min(1, p.life / p.maxLife)) : 1;
            drawLeaf(g, p, k);
            return;
        }
        double size;
        double alpha;
        if (p.type == DUST) {
            size = p.size;
            alpha = p.baseAlpha * (0.6 + 0.4 * Math.sin(t * 1.3 + p.phase));
        } else {
            double k = Math.max(0, Math.min(1, p.life / p.maxLife));
            size = p.sizeEnd + (p.size - p.sizeEnd) * k;
            alpha = p.baseAlpha * Math.min(1, k * 2);
        }
        if (size <= 0.05 || alpha <= 0.004) {
            return;
        }
        g.setGlobalAlpha(alpha);
        g.setFill(p.color);
        g.fillOval(p.x - size / 2, p.y - size / 2, size, size);
        g.setGlobalAlpha(1);
    }

    /** 精修叶片：双二次曲线叶形 + 中脉叶脉。 */
    private void drawLeaf(GraphicsContext g, Particle p, double lifeK) {
        double alpha = p.type == LEAF ? p.baseAlpha : p.baseAlpha * Math.min(1, lifeK * 2);
        if (alpha <= 0.01) {
            return;
        }
        g.save();
        g.setGlobalAlpha(alpha);
        g.translate(p.x, p.y);
        g.rotate(Math.toDegrees(p.rot));
        double w = p.leafW;
        double h = p.leafH;
        g.setFill(p.color);
        g.beginPath();
        g.moveTo(-w / 2, 0);
        g.quadraticCurveTo(0, -h, w / 2, 0);
        g.quadraticCurveTo(0, h, -w / 2, 0);
        g.fill();
        g.setStroke(p.vein);
        g.setLineWidth(1);
        g.strokeLine(-w / 2 + 1, 0, w / 2 - 1, 0);
        g.restore();
    }
}
