package org.example.ui;

import java.net.URL;
import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.layout.StackPane;

/**
 * 主控制器（spec §4.3.3）。
 * M1 阶段职责：棋盘容器随窗口缩放保持正方形（spec §4.5）；
 * 其余能力（引擎交互、设置、统计、动画）在后续里程碑中逐步并入。
 */
public class GameController implements Initializable {

    /** 棋盘底板（正方形区域，side = min(可用宽,可用高) - 2×PADDING） */
    @FXML
    private StackPane boardPane;

    /** 棋盘容器（占满 center 区域） */
    @FXML
    private StackPane boardArea;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // 窗口拉伸时重算棋盘边长，始终保持正方形
        boardArea.widthProperty().addListener((o, oldV, newV) -> relayout());
        boardArea.heightProperty().addListener((o, oldV, newV) -> relayout());
        relayout();
    }

    /** 按 spec §4.5 布局公式更新棋盘边长 */
    private void relayout() {
        double side = BoardLayout.boardSize(boardArea.getWidth(), boardArea.getHeight());
        if (side <= 0) {
            return;
        }
        boardPane.setMinSize(side, side);
        boardPane.setPrefSize(side, side);
        boardPane.setMaxSize(side, side);
    }
}