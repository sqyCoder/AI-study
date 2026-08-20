package org.example.gobang.fx;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;

import java.util.Random;

/**
 * 森林背景：静态 Canvas 绘制一次。
 * 天空渐变 → 远山两层锯齿剪影 → 太阳光晕 → 侧边树影（树干矩形+树冠圆形簇）
 * → 底部草地。
 */
public class ForestBackground {

    private final Canvas scene = new Canvas(800, 900);
    private final LeafAnimation leaves = new LeafAnimation();
    private final javafx.scene.layout.StackPane root = new javafx.scene.layout.StackPane();
    private final Random rnd = new Random(7);

    public ForestBackground() {
        draw();
        root.getChildren().addAll(scene, leaves.getCanvas());
        root.setMouseTransparent(true);
    }

    public javafx.scene.Node getNode() {
        return root;
    }

    public LeafAnimation getLeaves() {
        return leaves;
    }

    public void start() {
        leaves.start();
    }

    public void stop() {
        leaves.stop();
    }

    /** 终局花瓣庆祝：临时增至 40 片，3 秒后还原。 */
    public void burstPetals() {
        leaves.burstPetals();
    }

    private void draw() {
        GraphicsContext g = scene.getGraphicsContext2D();

        // 1. 天空渐变
        LinearGradient sky = new LinearGradient(0, 0, 0, 660, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#5f9dd6")),
                new Stop(0.42, Color.web("#a5d3ea")),
                new Stop(0.75, Color.web("#dcefe2")),
                new Stop(1, Color.web("#c9e3bd")));
        g.setFill(sky);
        g.fillRect(0, 0, 800, 660);

        // 2. 太阳光晕
        RadialGradient sun = new RadialGradient(0, 0, 610, 140, 150, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#fff4c8", 0.95)),
                new Stop(0.55, Color.web("#ffecaa", 0.4)),
                new Stop(1, Color.web("#ffecaa", 0)));
        g.setFill(sun);
        g.fillOval(460, 0, 300, 300);

        // 3. 远山两层
        drawMountains(g, 430, 0.5, Color.web("#7fa86f", 0.55));
        drawMountains(g, 480, 0.75, Color.web("#5d8a52", 0.7));

        // 4. 侧边树影
        drawTree(g, 30, 560, 52, 210, 130);
        drawTree(g, 780, 500, 44, 180, 110);
        drawTree(g, 762, 660, 38, 150, 95);
        drawTree(g, 12, 720, 30, 140, 85);

        // 5. 底部草地
        LinearGradient grass = new LinearGradient(0, 600, 0, 900, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#7fae57")),
                new Stop(0.45, Color.web("#5d9440")),
                new Stop(1, Color.web("#3c6e2e")));
        g.setFill(grass);
        g.fillRect(0, 600, 800, 300);

        // 草地上的小草笔触
        g.setStroke(Color.web("#a9d17e", 0.7));
        g.setLineWidth(2);
        for (int i = 0; i < 60; i++) {
            double x = rnd.nextDouble() * 800;
            double y = 620 + rnd.nextDouble() * 260;
            double h = 8 + rnd.nextDouble() * 14;
            g.beginPath();
            g.moveTo(x, y);
            g.quadraticCurveTo(x + (rnd.nextDouble() - 0.5) * 6, y - h * 0.6, x + (rnd.nextDouble() - 0.5) * 8, y - h);
            g.stroke();
        }
    }

    private void drawMountains(GraphicsContext g, int baseY, double ampScale, Color color) {
        int peaks = 7;
        double[] heights = new double[peaks];
        for (int i = 0; i < peaks; i++) {
            heights[i] = (70 + rnd.nextDouble() * 130) * ampScale;
        }
        g.beginPath();
        g.moveTo(0, baseY);
        for (int i = 0; i < peaks; i++) {
            double x0 = 800.0 * i / peaks;
            double x1 = 800.0 * (i + 1) / peaks;
            double midX = (x0 + x1) / 2;
            g.lineTo(midX, baseY - heights[i]);
            g.lineTo(x1, baseY - heights[i] * (0.35 + rnd.nextDouble() * 0.3));
        }
        g.lineTo(800, 900);
        g.lineTo(0, 900);
        g.closePath();
        g.setFill(color);
        g.fill();
    }

    private void drawTree(GraphicsContext g, double x, double groundY, double trunkW, double trunkH, double crownR) {
        // 树干
        g.setFill(Color.web("#5b3d22"));
        g.fillRoundRect(x - trunkW / 2, groundY - trunkH, trunkW, trunkH, 10, 10);
        g.setFill(Color.web("#6e4a28"));
        g.fillRoundRect(x - trunkW / 2 + 3, groundY - trunkH + 4, trunkW - 6, trunkH - 8, 8, 8);
        // 树冠（圆形簇）
        Color[] greens = {Color.web("#2f5d33"), Color.web("#3a6d3e"), Color.web("#275229"), Color.web("#43784a")};
        double cx = x;
        double cy = groundY - trunkH - crownR * 0.45;
        for (int i = 0; i < 5; i++) {
            double ox = (rnd.nextDouble() - 0.5) * crownR * 1.4;
            double oy = (rnd.nextDouble() - 0.5) * crownR * 0.9;
            double r = crownR * (0.55 + rnd.nextDouble() * 0.45);
            g.setFill(greens[rnd.nextInt(greens.length)]);
            g.fillOval(cx + ox - r, cy + oy - r, r * 2, r * 2);
        }
    }

    private void addVignette(GraphicsContext g, double cx, double cy) {
        // 已弃用：四角暗角被移除
    }
}