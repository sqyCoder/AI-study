package org.example.ui;

import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * 方块节点工厂（spec §4.3.2）：按数值与格宽生成/更新方块节点。
 * <p>
 * 节点＝StackPane 内嵌 Label；CSS 类 tile-2 … tile-2048 / tile-super 控制配色；
 * 字号随格宽联动 + 按位数衰减（适配 3×3 ~ 8×8 任意格宽）。
 */
public final class TileViewFactory {

    private TileViewFactory() {
        // 工具类，禁止实例化
    }

    /** 字号系数表（× cellSize）：1 位 0.48 / 2 位 0.40 / 3 位 0.32 / 4 位 0.26 / 5 位及以上 0.22。 */
    private static final double[] FONT_FACTORS = {0.48, 0.40, 0.32, 0.26, 0.22};

    /** 按数值与格宽创建方块节点（初始尺寸/字号随格宽）。 */
    public static StackPane createTile(int value, double cellSize) {
        StackPane tile = new StackPane();
        tile.getStyleClass().add("tile");
        Label label = new Label();
        label.getStyleClass().add("tile-label");
        tile.getChildren().add(label);
        restyleTile(tile, value, cellSize);
        return tile;
    }

    /** 更新方块样式：数值文案、配色类、字号、尺寸（窗口缩放/合并后调用）。 */
    public static void restyleTile(StackPane tile, int value, double cellSize) {
        tile.getStyleClass().removeIf(c -> c.startsWith("tile-"));
        tile.getStyleClass().add(tileClass(value));
        Label label = (Label) tile.getChildren().get(0);
        label.setText(String.valueOf(value));
        label.setFont(Font.font("System", FontWeight.BOLD, Math.max(8, cellSize * fontFactor(value))));
        tile.setPrefSize(cellSize, cellSize);
        tile.setMinSize(cellSize, cellSize);
        tile.setMaxSize(cellSize, cellSize);
    }

    private static String tileClass(int value) {
        return value > 2048 ? "tile-super" : "tile-" + value;
    }

    private static double fontFactor(int value) {
        int digits = String.valueOf(value).length();
        return FONT_FACTORS[Math.min(digits, FONT_FACTORS.length) - 1];
    }
}
