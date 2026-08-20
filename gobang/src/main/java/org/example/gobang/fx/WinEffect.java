package org.example.gobang.fx;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.util.Duration;

import org.example.gobang.model.Move;

import java.util.List;

/**
 * 终局五连高亮：胜方 5 子发光圆环 + 连线描边动画（透明度脉动 2 次后定格）。
 */
public class WinEffect {

    private final Canvas canvas;
    private final DoubleProperty alpha = new SimpleDoubleProperty(1);
    private List<Move> line;
    private Timeline timeline;

    public WinEffect(Canvas canvas) {
        this.canvas = canvas;
        alpha.addListener((ob, o, n) -> redraw(n.doubleValue()));
    }

    public void play(List<Move> winLine) {
        stop();
        line = winLine;
        alpha.set(1);
        timeline = new Timeline(
                new KeyFrame(Duration.ZERO, e -> redraw(1)),
                new KeyFrame(Duration.millis(220), new KeyValue(alpha, 0.25)),
                new KeyFrame(Duration.millis(440), new KeyValue(alpha, 1)),
                new KeyFrame(Duration.millis(660), new KeyValue(alpha, 0.25)),
                new KeyFrame(Duration.millis(880), new KeyValue(alpha, 1)),
                new KeyFrame(Duration.millis(1000), e -> redraw(0.85))
        );
        timeline.play();
    }

    private void redraw(double a) {
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, 700, 700);
        if (line == null || line.isEmpty()) return;
        g.setLineCap(StrokeLineCap.ROUND);
        double[][] pts = new double[line.size()][2];
        for (int i = 0; i < line.size(); i++) {
            Move m = line.get(i);
            pts[i][0] = m.col * 50.0;
            pts[i][1] = m.row * 50.0;
        }
        // 发光层
        g.setStroke(Color.web("#ffd54a", a * 0.3));
        g.setLineWidth(13);
        g.beginPath();
        g.moveTo(pts[0][0], pts[0][1]);
        for (int i = 1; i < pts.length; i++) {
            g.lineTo(pts[i][0], pts[i][1]);
        }
        g.stroke();
        // 主线
        g.setStroke(Color.web("#ffe082", a));
        g.setLineWidth(4.5);
        g.beginPath();
        g.moveTo(pts[0][0], pts[0][1]);
        for (int i = 1; i < pts.length; i++) {
            g.lineTo(pts[i][0], pts[i][1]);
        }
        g.stroke();
        // 发光圆环
        for (double[] p : pts) {
            g.setStroke(Color.web("#ffd54a", a * 0.28));
            g.setLineWidth(9);
            g.strokeOval(p[0] - 29, p[1] - 29, 58, 58);
            g.setStroke(Color.web("#ffd54a", a));
            g.setLineWidth(3.5);
            g.strokeOval(p[0] - 25, p[1] - 25, 50, 50);
        }
    }

    public void stop() {
        if (timeline != null) {
            timeline.stop();
            timeline = null;
        }
        line = null;
        canvas.getGraphicsContext2D().clearRect(0, 0, 700, 700);
    }
}