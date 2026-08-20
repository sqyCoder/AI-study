package org.example.gobang.fx;

import javafx.animation.AnimationTimer;
import javafx.animation.PauseTransition;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import org.example.gobang.audio.SoundManager;
import org.example.gobang.audio.SoundType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 落叶装饰动画：8~10 片，AnimationTimer 60fps；
 * 下落速度 30~80px/s、左右摆动 20~40px、自转、尺寸 8~20px、黄/绿/棕；
 * y 超界从顶部随机 x 重生（相位错开防同排）；
 * 窗口失焦时暂停；终局临时增至 40 片花瓣庆祝 3 秒后还原。
 */
public class LeafAnimation {

    private static final int NORMAL_COUNT = 10;
    private static final int BURST_COUNT = 40;

    private static final Color[] LEAF_COLORS = {
            Color.web("#d4af37", 0.92),
            Color.web("#9bc44f", 0.92),
            Color.web("#a06c3c", 0.92),
            Color.web("#8fbf3f", 0.92)
    };
    private static final Color[] PETAL_COLORS = {
            Color.web("#f4a9b8", 0.95),
            Color.web("#f8c9d3", 0.95),
            Color.web("#f48fa5", 0.95),
            Color.web("#f2d4de", 0.95)
    };

    private final Canvas canvas = new Canvas(800, 900);
    private final Random rnd = new Random();
    private final List<Leaf> leaves = new ArrayList<>();
    private final AnimationTimer timer;

    private int targetCount = NORMAL_COUNT;
    private boolean petals = false;
    private volatile boolean paused = false;
    private long lastNanos = -1;
    private boolean running = false;
    private long nextRustle = 0;

    public LeafAnimation() {
        spawn(NORMAL_COUNT, false);
        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
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
                step(dt);
            }
        };
    }

    public Canvas getCanvas() {
        return canvas;
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
        canvas.getGraphicsContext2D().clearRect(0, 0, 800, 900);
    }

    public synchronized void setPaused(boolean p) {
        paused = p;
        lastNanos = -1;
    }

    /** 终局花瓣庆祝：40 片 3 秒后还原。 */
    public void burstPetals() {
        burstTo(BURST_COUNT, 3000);
    }

    public void burstTo(int count, long keepMs) {
        targetCount = Math.max(1, count);
        petals = true;
        PauseTransition revert = new PauseTransition(Duration.millis(keepMs));
        revert.setOnFinished(e -> {
            petals = false;
            targetCount = NORMAL_COUNT;
        });
        revert.play();
    }

    private void spawn(int n, boolean atTop) {
        for (int i = 0; i < n; i++) {
            boolean petal = petals && rnd.nextDouble() < 0.85;
            Leaf l = new Leaf();
            l.color = petal ? PETAL_COLORS[rnd.nextInt(PETAL_COLORS.length)]
                    : LEAF_COLORS[rnd.nextInt(LEAF_COLORS.length)];
            l.w = petal ? 6 + rnd.nextDouble() * 6 : 8 + rnd.nextDouble() * 12;
            l.h = l.w * (petal ? 0.9 : 0.5);
            l.baseX = 10 + rnd.nextDouble() * 780;
            l.x = l.baseX;
            l.y = atTop ? -30 : rnd.nextDouble() * 900;
            l.phase = rnd.nextDouble() * Math.PI * 2;
            l.swaySpeed = 1 + rnd.nextDouble() * 1.5;
            l.swayAmp = 20 + rnd.nextDouble() * 20;
            l.fallSpeed = 30 + rnd.nextDouble() * 50;
            l.rotSpeed = (rnd.nextBoolean() ? 1 : -1) * (1 + rnd.nextDouble() * 2);
            l.rot = rnd.nextDouble() * Math.PI;
            leaves.add(l);
        }
    }

    private void step(double dt) {
        while (leaves.size() < targetCount) {
            spawn(1, true);
        }
        while (leaves.size() > targetCount) {
            leaves.remove(leaves.size() - 1);
        }
        long now = System.currentTimeMillis();
        if (now >= nextRustle) {
            nextRustle = now + 7000 + rnd.nextInt(4000);
            SoundManager.play(SoundType.LEAF_RUSTLE, 0.12);
        }
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, 800, 900);
        for (Leaf l : leaves) {
            l.y += l.fallSpeed * dt;
            l.phase += l.swaySpeed * dt;
            l.rot += l.rotSpeed * dt;
            l.x = l.baseX + Math.sin(l.phase) * l.swayAmp;
            if (l.y > 930) {
                respawn(l);
            }
            g.save();
            g.translate(l.x, l.y);
            g.rotate(Math.toDegrees(l.rot));
            g.setFill(l.color);
            g.fillOval(-l.w / 2, -l.h / 2, l.w, l.h);
            g.restore();
        }
    }

    private void respawn(Leaf l) {
        l.baseX = 10 + rnd.nextDouble() * 780;
        l.x = l.baseX;
        l.y = -30;
        l.phase = rnd.nextDouble() * Math.PI * 2;
    }

    private static class Leaf {
        double x, y, baseX, w, h, fallSpeed, swayAmp, swaySpeed, rot, rotSpeed, phase;
        Color color;
    }
}