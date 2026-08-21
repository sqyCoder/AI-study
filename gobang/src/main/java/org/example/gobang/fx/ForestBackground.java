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
 * 晨雾暖阳森林背景（spec2 修订版：恢复森林世界，保留精修质感）：
 * 晨空渐变 → 太阳光晕+斜射光柱 → 三层远山（大气透视）→ 山谷雾带
 * → 中景林线 → 草地 → 林间木桌平台（承托棋盘）→ 前景框景树影
 * → 草丛/野花点缀 → 四角暗角。静态 Canvas 绘制一次，种子固定。
 * 动效层（落叶/光尘）由全局 Particles 单例负责。
 */
public class ForestBackground {

    private final Canvas scene = new Canvas(800, 900);
    private final javafx.scene.layout.StackPane root = new javafx.scene.layout.StackPane();
    private final Random rnd = new Random(12);

    public ForestBackground() {
        draw();
        root.getChildren().add(scene);
        root.setMouseTransparent(true);
    }

    public javafx.scene.Node getNode() {
        return root;
    }

    /** 兼容旧调用：启动/停止/暂停全部转发给全局粒子引擎。 */
    public void start() {
        Particles.get().start();
    }

    public void stop() {
        Particles.get().stop();
    }

    public void setPaused(boolean p) {
        Particles.get().setPaused(p);
    }

    private void draw() {
        GraphicsContext g = scene.getGraphicsContext2D();

        // 1. 晨空渐变（地平线泛暖）
        g.setFill(new LinearGradient(0, 0, 0, 660, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#8ec3e8")),
                new Stop(0.45, Color.web("#c9e4ef")),
                new Stop(0.75, Color.web("#eee9cf")),
                new Stop(1, Color.web("#f6ecd2"))));
        g.fillRect(0, 0, 800, 660);

        // 2. 太阳光晕 + 日核
        g.setFill(new RadialGradient(0, 0, 585, 140, 260, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#fff8dc", 0.95)),
                new Stop(0.4, Color.web("#ffedb0", 0.38)),
                new Stop(1, Color.web("#ffedb0", 0))));
        g.fillOval(325, -120, 520, 520);
        g.setFill(Color.web("#fffdf2"));
        g.fillOval(585 - 34, 140 - 34, 68, 68);

        // 3. 斜射光柱（自太阳向左下）
        double[] angles = {-52, -36, -21, -7, 8};
        double[] widths = {44, 58, 50, 64, 40};
        for (int i = 0; i < angles.length; i++) {
            drawRay(g, 585, 140, angles[i], 620 + i * 30, widths[i], 0.10 + (i % 2) * 0.04);
        }

        // 4. 三层远山（大气透视：远淡近深，圆润山脊）
        drawRidge(g, 452, 95, Color.web("#b7d4c2", 0.92));
        drawRidge(g, 512, 125, Color.web("#8fb89b", 0.95));
        drawRidge(g, 572, 150, Color.web("#67997a"));

        // 5. 山谷雾带
        drawMist(g, 468, 54, 0.16);
        drawMist(g, 528, 60, 0.19);
        drawMist(g, 592, 70, 0.22);

        // 6. 中景林线剪影
        drawTreeline(g, 600, Color.web("#4a7d54"));

        // 7. 草地
        g.setFill(new LinearGradient(0, 620, 0, 900, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#93c06b")),
                new Stop(0.45, Color.web("#63a047")),
                new Stop(1, Color.web("#3f7434"))));
        g.fillRect(0, 618, 800, 282);

        // 8. 前景框景树影（左右两侧，压住画面边缘）
        drawFramingTree(g, true);
        drawFramingTree(g, false);

        // 10. 草丛与野花点缀
        drawGrassTufts(g);
        drawFlowerSpecks(g);

        // 11. 四角暗角
        drawCornerVignette(g, 0, 0);
        drawCornerVignette(g, 800, 0);
        drawCornerVignette(g, 0, 900);
        drawCornerVignette(g, 800, 900);
    }

    /** 斜射光柱：绕太阳旋转的渐变楔形。 */
    private void drawRay(GraphicsContext g, double sx, double sy,
                         double angleDeg, double len, double width, double alpha) {
        g.save();
        g.translate(sx, sy);
        g.rotate(angleDeg);
        g.setFill(new LinearGradient(0, 0, len, 0, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#fff4cc", alpha)),
                new Stop(1, Color.web("#fff4cc", 0))));
        g.fillRect(0, -width / 2, len, width);
        g.restore();
    }

    /** 圆润山脊：随机峰点 + 二次曲线平滑连接，填充到画布底部。 */
    private void drawRidge(GraphicsContext g, double baseY, double amp, Color color) {
        int n = 9;
        double[] xs = new double[n + 2];
        double[] ys = new double[n + 2];
        for (int i = 0; i <= n + 1; i++) {
            xs[i] = -40 + i * (880.0 / n);
            ys[i] = baseY - rnd.nextDouble() * amp;
        }
        g.beginPath();
        g.moveTo(xs[0], 900);
        g.lineTo(xs[0], ys[0]);
        for (int i = 1; i <= n + 1; i++) {
            double cx = (xs[i - 1] + xs[i]) / 2;
            double cy = Math.min(ys[i - 1], ys[i]) - rnd.nextDouble() * amp * 0.35;
            g.quadraticCurveTo(cx, cy, xs[i], ys[i]);
        }
        g.lineTo(xs[n + 1], 900);
        g.closePath();
        g.setFill(color);
        g.fill();
    }

    /** 柔和雾带：垂直渐变白带。 */
    private void drawMist(GraphicsContext g, double y, double h, double alpha) {
        g.setFill(new LinearGradient(0, y - h / 2, 0, y + h / 2, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#ffffff", 0)),
                new Stop(0.5, Color.web("#ffffff", alpha)),
                new Stop(1, Color.web("#ffffff", 0))));
        g.fillRect(0, y - h / 2, 800, h);
    }

    /** 中景林线：树冠圆簇连绵 + 底部填充。 */
    private void drawTreeline(GraphicsContext g, double baseY, Color color) {
        g.setFill(color);
        double x = -20;
        while (x < 820) {
            double r = 24 + rnd.nextDouble() * 26;
            double cy = baseY - rnd.nextDouble() * 22;
            g.fillOval(x - r, cy - r, r * 2, r * 2);
            x += r * 0.9;
        }
        g.fillRect(0, baseY, 800, 660 - baseY + 40);
    }

    /** 前景框景树：弯曲树干 + 双色树冠簇 + 朝阳侧微高光。 */
    private void drawFramingTree(GraphicsContext g, boolean left) {
        double trunkX = left ? 14 : 762;
        double canopyCx = left ? 58 : 742;
        double canopyCy = left ? 430 : 396;

        // 树干
        g.setFill(new LinearGradient(trunkX, 0, trunkX + 40, 0, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#3a2a18")),
                new Stop(1, Color.web("#55402a"))));
        g.fillRoundRect(trunkX, canopyCy + 40, 38, 900 - (canopyCy + 40), 14, 14);

        // 树冠簇（深色为主）
        Color[] darks = {Color.web("#2f5d33"), Color.web("#27502a"), Color.web("#356a3a")};
        for (int i = 0; i < 9; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double d = rnd.nextDouble() * 62;
            double r = 42 + rnd.nextDouble() * 52;
            g.setFill(darks[rnd.nextInt(darks.length)]);
            g.fillOval(canopyCx + Math.cos(a) * d - r, canopyCy + Math.sin(a) * d * 0.72 - r,
                    r * 2, r * 2);
        }
        // 朝阳侧高光簇（柔和、小而偏）
        double hiDir = left ? 1 : -1;
        for (int i = 0; i < 3; i++) {
            double r = 13 + rnd.nextDouble() * 12;
            g.setFill(Color.web("#7cba6e", 0.22));
            g.fillOval(canopyCx + hiDir * (30 + rnd.nextDouble() * 34) - r,
                    canopyCy - 36 + rnd.nextDouble() * 64 - r, r * 2, r * 2);
        }
    }

    /** 草丛笔触（双色）。 */
    private void drawGrassTufts(GraphicsContext g) {
        for (int i = 0; i < 90; i++) {
            double x = rnd.nextDouble() * 800;
            double y = 640 + rnd.nextDouble() * 250;
            double h = 10 + rnd.nextDouble() * 18;
            g.setStroke(rnd.nextBoolean()
                    ? Color.web("#b9dd8e", 0.7)
                    : Color.web("#56923f", 0.8));
            g.setLineWidth(1.6);
            g.beginPath();
            g.moveTo(x, y);
            g.quadraticCurveTo(x + (rnd.nextDouble() - 0.5) * 8, y - h * 0.6,
                    x + (rnd.nextDouble() - 0.5) * 12, y - h);
            g.stroke();
        }
    }

    /** 野花微点。 */
    private void drawFlowerSpecks(GraphicsContext g) {
        for (int i = 0; i < 26; i++) {
            double x = rnd.nextDouble() * 800;
            double y = 650 + rnd.nextDouble() * 230;
            g.setFill(rnd.nextBoolean()
                    ? Color.web("#f2e18c", 0.65)
                    : Color.web("#ffffff", 0.55));
            g.fillOval(x, y, 2.4, 2.4);
        }
    }

    private void drawCornerVignette(GraphicsContext g, double cx, double cy) {
        g.setFill(new RadialGradient(0, 0, cx, cy, 460, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(16, 28, 16, 0.26)),
                new Stop(1, Color.rgb(16, 28, 16, 0))));
        g.fillOval(cx - 460, cy - 460, 920, 920);
    }
}
