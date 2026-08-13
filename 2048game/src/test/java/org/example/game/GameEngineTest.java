package org.example.game;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 引擎单元测试（spec §6 清单 1–13），JUnit 5，无 GUI 运行。
 * 关键用例用参数化测试覆盖 N=3、4、8。
 */
class GameEngineTest {

    // ===== 1. 构造校验 =====

    @Test
    void 尺寸越界抛异常() {
        assertThrows(IllegalArgumentException.class, () -> new GameEngine(2));
        assertThrows(IllegalArgumentException.class, () -> new GameEngine(9));
        assertThrows(IllegalArgumentException.class, () -> new GameEngine(0));
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 4, 8})
    void 合法尺寸正常构造(int size) {
        GameEngine engine = new GameEngine(size);
        assertEquals(size, engine.getSize());
    }

    // ===== 2. 初始局面 =====

    @ParameterizedTest
    @ValueSource(ints = {3, 4, 8})
    void 新开局恰好两个方块(int size) {
        GameEngine engine = new GameEngine(size);
        engine.startNewGame(size);
        assertEquals(2, countNonZero(engine));
        assertEquals(0, engine.getScore());
        assertEquals(0, engine.getSteps());
        assertFalse(engine.isGameOver());
        assertFalse(engine.isWon());
    }

    // ===== 3. 滑动基本 =====

    @Test
    void 无变化移动返回未移动() {
        GameEngine e = new GameEngine(4);
        e.setGrid(tiles(new int[][]{
                {2, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}}));
        MoveResult r = e.move(Direction.LEFT);
        assertFalse(r.moved());
        assertGrid(e, new int[][]{
                {2, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}});
    }

    @Test
    void 跨空格合并() {
        GameEngine e = new GameEngine(4);
        e.setGrid(tiles(new int[][]{
                {0, 2, 0, 2}, {0, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}}));
        MoveResult r = e.move(Direction.LEFT);
        assertTrue(r.moved());
        assertGrid(e, new int[][]{
                {4, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}}, r.spawned());
        assertEquals(4, r.scoreDelta());
    }

    // ===== 4. 一次合并规则 =====

    @Test
    void 无连锁合并四连2() {
        GameEngine e = new GameEngine(4);
        e.setGrid(tiles(new int[][]{
                {2, 2, 2, 2}, {0, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}}));
        MoveResult r = e.move(Direction.LEFT);
        assertGrid(e, new int[][]{
                {4, 4, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}}, r.spawned());
    }

    @Test
    void 三连2合并前两格() {
        GameEngine e = new GameEngine(4);
        e.setGrid(tiles(new int[][]{
                {2, 2, 2, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}}));
        MoveResult r = e.move(Direction.LEFT);
        assertGrid(e, new int[][]{
                {4, 2, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}}, r.spawned());
    }

    @Test
    void 两组相邻合并互不干扰() {
        GameEngine e = new GameEngine(4);
        e.setGrid(tiles(new int[][]{
                {4, 4, 8, 8}, {0, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}}));
        MoveResult r = e.move(Direction.LEFT);
        assertGrid(e, new int[][]{
                {8, 16, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}}, r.spawned());
    }

    @Test
    void 跨空位合并() {
        GameEngine e = new GameEngine(4);
        e.setGrid(tiles(new int[][]{
                {2, 0, 2, 4}, {0, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}}));
        MoveResult r = e.move(Direction.LEFT);
        assertGrid(e, new int[][]{
                {4, 4, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}}, r.spawned());
    }

    @Test
    void 三乘三合并规则() {
        GameEngine e = new GameEngine(3);
        e.setGrid(tiles(new int[][]{
                {2, 2, 2}, {0, 0, 0}, {0, 0, 0}}));
        MoveResult r = e.move(Direction.LEFT);
        assertGrid(e, new int[][]{{4, 2, 0}, {0, 0, 0}, {0, 0, 0}}, r.spawned());
    }

    @Test
    void 八乘八合并规则() {
        GameEngine e = new GameEngine(8);
        e.setGrid(tiles(new int[][]{
                {2, 2, 2, 2, 2, 2, 2, 2}, {0, 0, 0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0, 0, 0}, {0, 0, 0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0, 0, 0}, {0, 0, 0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0, 0, 0}, {0, 0, 0, 0, 0, 0, 0, 0}}));
        MoveResult r = e.move(Direction.LEFT);
        assertGrid(e, new int[][]{
                {4, 4, 4, 4, 0, 0, 0, 0}, {0, 0, 0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0, 0, 0}, {0, 0, 0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0, 0, 0}, {0, 0, 0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0, 0, 0}, {0, 0, 0, 0, 0, 0, 0, 0}}, r.spawned());
    }

    // ===== 5. 方向正确性（与手推结果一致） =====

    @ParameterizedTest
    @ValueSource(ints = {3, 4})
    void 各方向滑动与手推一致(int size) {
        GameEngine e = new GameEngine(size);
        if (size == 3) {
            int[][] before = {{2, 0, 4}, {0, 4, 2}, {4, 0, 8}};
            e.setGrid(tiles(before));
            MoveResult r = e.move(Direction.LEFT);
            assertGrid(e, new int[][]{{2, 4, 0}, {4, 2, 0}, {4, 8, 0}}, r.spawned());

            e.setGrid(tiles(before));
            r = e.move(Direction.RIGHT);
            assertGrid(e, new int[][]{{0, 2, 4}, {0, 4, 2}, {0, 4, 8}}, r.spawned());

            e.setGrid(tiles(before));
            r = e.move(Direction.UP);
            assertGrid(e, new int[][]{{2, 4, 4}, {4, 0, 2}, {0, 0, 8}}, r.spawned());

            e.setGrid(tiles(before));
            r = e.move(Direction.DOWN);
            assertGrid(e, new int[][]{{0, 0, 4}, {2, 0, 2}, {4, 4, 8}}, r.spawned());
        } else {
            int[][] before = {{2, 0, 4, 8}, {0, 2, 0, 4}, {4, 0, 2, 0}, {0, 4, 0, 2}};
            e.setGrid(tiles(before));
            MoveResult r = e.move(Direction.LEFT);
            assertGrid(e, new int[][]{{2, 4, 8, 0}, {2, 4, 0, 0}, {4, 2, 0, 0}, {4, 2, 0, 0}}, r.spawned());

            e.setGrid(tiles(before));
            r = e.move(Direction.RIGHT);
            assertGrid(e, new int[][]{{0, 2, 4, 8}, {0, 0, 2, 4}, {0, 0, 4, 2}, {0, 0, 4, 2}}, r.spawned());

            e.setGrid(tiles(before));
            r = e.move(Direction.UP);
            assertGrid(e, new int[][]{{2, 2, 4, 8}, {4, 4, 2, 4}, {0, 0, 0, 2}, {0, 0, 0, 0}}, r.spawned());

            e.setGrid(tiles(before));
            r = e.move(Direction.DOWN);
            assertGrid(e, new int[][]{{0, 0, 0, 0}, {0, 0, 0, 8}, {2, 2, 4, 4}, {4, 4, 2, 2}}, r.spawned());
        }
    }

    @Test
    void 八乘八方向正确() {
        GameEngine e = new GameEngine(8);
        int[][] before = new int[8][8];
        before[0] = new int[]{2, 0, 2, 0, 2, 0, 2, 0};
        e.setGrid(tiles(before));

        MoveResult r = e.move(Direction.LEFT);
        int[][] expectedLeft = new int[8][8];
        expectedLeft[0] = new int[]{4, 4, 0, 0, 0, 0, 0, 0};
        assertGrid(e, expectedLeft, r.spawned());

        e.setGrid(tiles(before));
        r = e.move(Direction.RIGHT);
        int[][] expectedRight = new int[8][8];
        expectedRight[0] = new int[]{0, 0, 0, 0, 0, 0, 4, 4};
        assertGrid(e, expectedRight, r.spawned());

        e.setGrid(tiles(before));
        r = e.move(Direction.UP);
        int[][] expectedUp = new int[8][8];
        expectedUp[0] = new int[]{2, 0, 2, 0, 2, 0, 2, 0};
        assertGrid(e, expectedUp, r.spawned());

        e.setGrid(tiles(before));
        r = e.move(Direction.DOWN);
        int[][] expectedDown = new int[8][8];
        expectedDown[7] = new int[]{2, 0, 2, 0, 2, 0, 2, 0};
        assertGrid(e, expectedDown, r.spawned());
    }

    // ===== 6. 计分 =====

    @Test
    void 多组合并计分累计正确() {
        GameEngine e = new GameEngine(4);
        e.setGrid(tiles(new int[][]{
                {4, 4, 8, 8}, {2, 2, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}}));
        MoveResult r = e.move(Direction.LEFT);
        assertTrue(r.moved());
        assertEquals(8 + 16 + 4, r.scoreDelta());
        assertEquals(28, e.getScore());
        assertGrid(e, new int[][]{
                {8, 16, 0, 0}, {4, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}}, r.spawned());
    }

    @Test
    void 合并两格得分等于和() {
        GameEngine e = new GameEngine(4);
        e.setGrid(tiles(new int[][]{
                {8, 8, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}}));
        e.move(Direction.LEFT);
        assertEquals(16, e.getScore());
    }

    // ===== 7. 无效移动无副作用 =====

    @Test
    void 无效移动无任何副作用() {
        GameEngine e = new GameEngine(4);
        int[][] before = {{2, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}};
        e.setGrid(tiles(before));
        MoveResult r = e.move(Direction.LEFT);
        assertFalse(r.moved());
        assertEquals(0, e.getScore());
        assertEquals(0, e.getSteps());
        assertFalse(e.canUndo());
        assertNull(e.undo());
        assertTrue(r.moves().isEmpty());
        assertNull(r.spawned());
        assertEquals(1, countNonZero(e));
        assertGrid(e, before);
    }

    // ===== 8. 生成块概率 =====

    @Test
    void 生成方块概率约九成二() {
        GameEngine e = new GameEngine(4);
        int samples = 20000;
        int twos = 0;
        int[][] empty = new int[4][4];
        for (int i = 0; i < samples; i++) {
            e.setGrid(tiles(empty));
            List<TileSpawn> spawned = e.spawnRandomTile(1);
            assertEquals(1, spawned.size());
            if (spawned.get(0).value() == 2) {
                twos++;
            }
        }
        double ratio = (double) twos / samples;
        assertTrue(ratio >= 0.88 && ratio <= 0.92, "2 占比应为 90%±2%，实际 " + ratio);
    }

    @Test
    void 空格不足时只生成可放数量() {
        GameEngine e = new GameEngine(4);
        e.setGrid(tiles(new int[][]{
                {2, 4, 2, 4}, {4, 2, 4, 2}, {2, 4, 2, 4}, {4, 2, 4, 0}}));
        List<TileSpawn> spawned = e.spawnRandomTile(5);
        assertEquals(1, spawned.size());
    }

    // ===== 9. game over 判定 =====

    @Test
    void 满盘无合并判定游戏结束() {
        GameEngine e = new GameEngine(4);
        e.setGrid(tiles(new int[][]{
                {2, 4, 2, 4}, {4, 2, 4, 2}, {2, 4, 2, 4}, {4, 2, 4, 2}}));
        assertTrue(e.isGameOver());
        MoveResult r = e.move(Direction.LEFT);
        assertFalse(r.moved());
        assertTrue(r.gameOver());
    }

    @Test
    void 存在合并可能则未结束() {
        GameEngine e = new GameEngine(4);
        e.setGrid(tiles(new int[][]{
                {2, 2, 2, 4}, {4, 2, 4, 2}, {2, 4, 2, 4}, {4, 2, 4, 2}}));
        assertFalse(e.isGameOver());
    }

    @Test
    void 有空格则未结束() {
        GameEngine e = new GameEngine(4);
        e.setGrid(tiles(new int[][]{
                {2, 4, 2, 4}, {4, 2, 4, 2}, {2, 4, 2, 4}, {4, 2, 4, 0}}));
        assertFalse(e.isGameOver());
    }

    @Test
    void 三乘三满盘判定() {
        GameEngine e = new GameEngine(3);
        e.setGrid(tiles(new int[][]{{2, 4, 2}, {4, 2, 4}, {2, 4, 2}}));
        assertTrue(e.isGameOver());
    }

    @Test
    void 五乘五满盘判定() {
        GameEngine e = new GameEngine(5);
        e.setGrid(tiles(new int[][]{
                {2, 4, 2, 4, 2}, {4, 2, 4, 2, 4}, {2, 4, 2, 4, 2},
                {4, 2, 4, 2, 4}, {2, 4, 2, 4, 2}}));
        assertTrue(e.isGameOver());
    }

    // ===== 10. win 判定 =====

    @Test
    void 合成2048触发胜利并可继续() {
        GameEngine e = new GameEngine(4);
        e.setGrid(tiles(new int[][]{
                {1024, 1024, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}}));
        MoveResult r = e.move(Direction.LEFT);
        assertTrue(r.winReached());
        assertTrue(e.isWon());
        assertGrid(e, new int[][]{
                {2048, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}}, r.spawned());

        MoveResult blocked = e.move(Direction.RIGHT);
        assertFalse(blocked.moved());

        e.continueAfterWin();
        MoveResult resumed = e.move(Direction.RIGHT);
        assertTrue(resumed.moved());
        assertFalse(e.isGameOver());
    }

    @Test
    void 未达2048不触发胜利() {
        GameEngine e = new GameEngine(4);
        e.setGrid(tiles(new int[][]{
                {1024, 4, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}}));
        MoveResult r = e.move(Direction.LEFT);
        assertFalse(r.winReached());
        assertFalse(e.isWon());
    }

    // ===== 11. 撤销 =====

    @Test
    void 有效移动后撤销精确还原() {
        GameEngine e = new GameEngine(4);
        int[][] before = {{2, 2, 0, 0}, {0, 4, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}};
        e.setGrid(tiles(before));
        e.move(Direction.LEFT);
        assertEquals(4, e.getScore());

        UndoResult u = e.undo();
        assertNotNull(u);
        assertFalse(u.revived());
        assertEquals(0, e.getScore());
        assertGrid(e, before);
        assertFalse(e.canUndo());
    }

    @Test
    void 无效移动不入撤销栈() {
        GameEngine e = new GameEngine(4);
        e.setGrid(tiles(new int[][]{
                {2, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}}));
        e.move(Direction.LEFT);
        assertFalse(e.canUndo());
        assertNull(e.undo());
    }

    @Test
    void 新开局清空撤销栈() {
        GameEngine e = new GameEngine(4);
        e.setGrid(tiles(new int[][]{
                {2, 2, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}}));
        e.move(Direction.LEFT);
        assertTrue(e.canUndo());
        e.startNewGame();
        assertFalse(e.canUndo());
    }

    @Test
    void 撤销栈深度上限20() {
        GameEngine e = new GameEngine(8);
        e.setGrid(tiles(new int[8][8]));
        e.spawnRandomTile(2);
        int moved = 0;
        while (moved < 30) {
            for (Direction d : Direction.values()) {
                if (e.move(d).moved()) {
                    moved++;
                    break;
                }
            }
        }
        assertEquals(30, e.getSteps());
        int undone = 0;
        while (e.undo() != null) {
            undone++;
        }
        assertEquals(20, undone);
    }

    @Test
    void 游戏结束后撤销可复活() {
        GameEngine e = new GameEngine(4);
        e.setGrid(tiles(new int[][]{
                {1024, 1024, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}}));
        e.move(Direction.LEFT);
        assertTrue(e.isWon());

        UndoResult u = e.undo();
        assertNotNull(u);
        assertTrue(u.revived());
        assertFalse(e.isWon());
        assertFalse(e.isGameOver());
    }

    // ===== 12. 步骤统计 =====

    @Test
    void 步数统计规则() {
        GameEngine e = new GameEngine(4);
        e.setGrid(tiles(new int[][]{
                {2, 2, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}}));
        e.move(Direction.LEFT);
        assertEquals(1, e.getSteps());

        e.move(Direction.RIGHT);
        assertEquals(2, e.getSteps());

        e.move(Direction.LEFT);
        assertEquals(3, e.getSteps());

        e.undo();
        assertEquals(4, e.getSteps(), "撤销视为一次操作，步数 +1（spec §4.7）");

        e.setGrid(tiles(new int[][]{
                {2, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}}));
        e.move(Direction.LEFT);
        assertEquals(4, e.getSteps(), "无效移动步数不变");
    }

    // ===== 13. 防御性拷贝 =====

    @Test
    void 棋盘快照为防御拷贝() {
        GameEngine e = new GameEngine(4);
        e.setGrid(tiles(new int[4][4]));
        Tile[][] g = e.getGrid();
        g[0][0] = new Tile(1024, false);
        assertTrue(e.getGrid()[0][0].isEmpty());
    }

    // ===== 边界情况（spec §5） =====

    @Test
    void 空棋盘移动返回未移动() {
        GameEngine e = new GameEngine(4);
        e.setGrid(tiles(new int[4][4]));
        MoveResult r = e.move(Direction.UP);
        assertFalse(r.moved());
        assertFalse(e.isGameOver());
        assertNull(r.spawned());
    }

    // ===== 工具方法 =====

    private static int countNonZero(GameEngine engine) {
        int count = 0;
        for (Tile[] row : engine.getGrid()) {
            for (Tile t : row) {
                if (!t.isEmpty()) {
                    count++;
                }
            }
        }
        return count;
    }

    private static Tile[][] tiles(int[][] values) {
        int n = values.length;
        Tile[][] g = new Tile[n][n];
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                g[r][c] = new Tile(values[r][c], false);
            }
        }
        return g;
    }

    private static void assertGrid(GameEngine engine, int[][] expected) {
        assertGrid(engine, expected, null);
    }

    /** 断言棋盘；spawn 为本次移动随机生成的新块位置（该格允许不在 expected 中）。 */
    private static void assertGrid(GameEngine engine, int[][] expected, TileSpawn spawn) {
        Tile[][] grid = engine.getGrid();
        int n = grid.length;
        assertEquals(n, expected.length);
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                if (spawn != null && spawn.row() == r && spawn.col() == c) {
                    continue;
                }
                assertEquals(expected[r][c], grid[r][c].value(),
                        "位置 (" + r + "," + c + ") 的值不符");
            }
        }
    }
}
