package org.example.game;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 核心游戏引擎（spec §4.1.3）：N×N 棋盘（3~8）的移动/合并/计分/胜负/撤销。
 * <p>
 * 纯逻辑层，不依赖任何 JavaFX 类，可无 GUI 单测（NFR-1）。
 * 移动流程：坐标变换使问题等价为"每行向左" → 逐行 slideRow → 逆变换还原 →
 * 有效则入撤销栈、计分、生成新块、判定胜负。
 */
public class GameEngine {

    /** 最小棋盘 3×3。 */
    public static final int MIN_SIZE = 3;
    /** 最大棋盘 8×8。 */
    public static final int MAX_SIZE = 8;
    /** 胜利目标恒定为 2048（不随棋盘大小变化）。 */
    public static final int WIN_TARGET = 2048;
    /** 新块为 2 的概率（其余 10% 为 4）。 */
    private static final double SPAWN_TWO_PROBABILITY = 0.9;

    private final Random random = new Random();
    private int size;
    private Tile[][] grid;
    private final GameStats stats = new GameStats();
    private final UndoManager undo = new UndoManager();
    private boolean gameOver;
    private boolean won;
    private boolean continueAfterWin;

    /** 指定尺寸开局（3~8，越界抛 IllegalArgumentException）。 */
    public GameEngine(int size) {
        startNewGame(size);
    }

    /** 重置为指定尺寸的新一局：清空撤销栈、统计归零、随机落 2 块。 */
    public void startNewGame(int size) {
        if (size < MIN_SIZE || size > MAX_SIZE) {
            throw new IllegalArgumentException("棋盘尺寸须在 " + MIN_SIZE + "~" + MAX_SIZE + " 之间，实际为 " + size);
        }
        this.size = size;
        this.grid = emptyGrid(size);
        this.stats.reset();
        this.undo.clear();
        this.gameOver = false;
        this.won = false;
        this.continueAfterWin = false;
        spawnRandomTile(2);
    }

    /** 以当前尺寸重新开局。 */
    public void startNewGame() {
        startNewGame(size);
    }

    /** 棋盘快照（防御性拷贝，外部修改不影响内部状态）。 */
    public Tile[][] getGrid() {
        return deepCopy(grid);
    }

    /**
     * 测试辅助：用指定棋盘整体替换当前局面（防御拷贝），
     * 并重算 gameOver / won 标记（merged 标记统一复位）。
     */
    public void setGrid(Tile[][] newGrid) {
        if (newGrid == null || newGrid.length != size || newGrid[0].length != size) {
            throw new IllegalArgumentException("棋盘尺寸须为 " + size + "×" + size);
        }
        grid = deepCopy(newGrid);
        resetMergedFlags();
        gameOver = hasNoMoves();
        won = maxTile() >= WIN_TARGET;
        continueAfterWin = false;
    }

    /**
     * 在随机空格上生成方块（2 占 90%、4 占 10%），返回生成的块列表。
     * 空格不足时只生成可放的数量。
     */
    public List<TileSpawn> spawnRandomTile(int count) {
        List<int[]> empties = new ArrayList<>();
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (grid[r][c].isEmpty()) {
                    empties.add(new int[]{r, c});
                }
            }
        }
        Collections.shuffle(empties, random);
        List<TileSpawn> spawned = new ArrayList<>();
        int n = Math.min(count, empties.size());
        for (int i = 0; i < n; i++) {
            int[] p = empties.get(i);
            int value = random.nextDouble() < SPAWN_TWO_PROBABILITY ? 2 : 4;
            grid[p[0]][p[1]] = new Tile(value, false);
            spawned.add(new TileSpawn(p[0], p[1], value));
        }
        return spawned;
    }

    /**
     * 核心移动：滑动 + 合并 + 计分 + 派发新块 + 胜负判定。
     * <p>
     * 无效移动：moved=false，不生成块、不加分、不入撤销栈；
     * 有效移动：先快照入撤销栈（先算后提交），再更新棋盘与统计。
     */
    public MoveResult move(Direction dir) {
        if (gameOver || (won && !continueAfterWin)) {
            return MoveResult.noMove(gameOver);
        }
        Tile[][] before = deepCopy(grid);
        int scoreBefore = stats.getScore();

        Tile[][] t = dir.transform(grid);
        List<TileMoveT> tMoves = new ArrayList<>();
        int delta = 0;
        boolean changed = false;
        for (int r = 0; r < size; r++) {
            RowSlide rs = slideRow(t[r], r);
            t[r] = rs.row();
            tMoves.addAll(rs.moves());
            delta += rs.delta();
            changed |= rs.changed();
        }
        if (!changed) {
            return MoveResult.noMove(gameOver);
        }

        grid = dir.inverseTransform(t);
        resetMergedFlags();
        undo.push(before, scoreBefore);
        stats.addScore(delta);
        stats.incrementSteps();

        List<TileSpawn> spawned = spawnRandomTile(1);
        TileSpawn spawn = spawned.isEmpty() ? null : spawned.get(0);

        List<TileMove> moves = new ArrayList<>();
        for (TileMoveT m : tMoves) {
            moves.add(new TileMove(
                    dir.originRow(m.fromRow(), m.fromCol(), size),
                    dir.originCol(m.fromRow(), m.fromCol(), size),
                    dir.originRow(m.toRow(), m.toCol(), size),
                    dir.originCol(m.toRow(), m.toCol(), size),
                    m.value(), m.isMerge()));
        }

        boolean winReached = false;
        if (maxTile() >= WIN_TARGET && !won) {
            won = true;
            winReached = true;
        }
        gameOver = hasNoMoves();
        return new MoveResult(true, delta, moves, spawn, gameOver, winReached);
    }

    /**
     * 撤销一步（spec §4.1.6）：
     * 栈空返回 null；成功则恢复棋盘与分数，清除 gameOver/won 标记（复活），
     * continueAfterWin 复位；步数不回退。
     */
    public UndoResult undo() {
        if (!undo.canUndo()) {
            return null;
        }
        UndoEntry entry = undo.pop();
        boolean revived = gameOver || won;
        grid = entry.grid();
        stats.setScore(entry.score());
        gameOver = false;
        won = false;
        continueAfterWin = false;
        return new UndoResult(entry.grid(), entry.score(), revived);
    }

    /** 是否可撤销。 */
    public boolean canUndo() {
        return undo.canUndo();
    }

    /** 胜利后选择继续游戏（此后可继续移动直至无法移动）。 */
    public void continueAfterWin() {
        continueAfterWin = true;
    }

    public int getSize() {
        return size;
    }

    public int getScore() {
        return stats.getScore();
    }

    public int getSteps() {
        return stats.getSteps();
    }

    /** 统计快照（防御性拷贝）。 */
    public GameStats getStatsSnapshot() {
        return new GameStats(stats.getScore(), stats.getSteps());
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public boolean isWon() {
        return won;
    }

    /**
     * 单行向左滑动（spec §4.1.4）：
     * 取出非零块 → 相邻等值两两合并（每块每步最多一次）→ 左侧紧凑排布，右侧补空。
     * 返回新行、T 坐标系的移动记录、加分、是否发生变化。
     */
    private RowSlide slideRow(Tile[] row, int rowIdx) {
        int n = row.length;
        List<Tile> nz = new ArrayList<>();
        List<Integer> nzCols = new ArrayList<>();
        for (int c = 0; c < n; c++) {
            if (!row[c].isEmpty()) {
                nz.add(row[c]);
                nzCols.add(c);
            }
        }
        List<Tile> out = new ArrayList<>();
        List<int[]> sources = new ArrayList<>();
        int delta = 0;
        for (int i = 0; i < nz.size(); i++) {
            Tile cur = nz.get(i);
            if (i + 1 < nz.size() && cur.value() == nz.get(i + 1).value()) {
                int v = cur.value() * 2;
                out.add(new Tile(v, true));
                sources.add(new int[]{nzCols.get(i), nzCols.get(i + 1)});
                delta += v;
                i++;
            } else {
                out.add(cur);
                sources.add(new int[]{nzCols.get(i), -1});
            }
        }
        Tile[] newRow = new Tile[n];
        Arrays.fill(newRow, Tile.EMPTY);
        for (int j = 0; j < out.size(); j++) {
            newRow[j] = out.get(j);
        }

        List<TileMoveT> moves = new ArrayList<>();
        boolean changed = false;
        for (int j = 0; j < out.size(); j++) {
            int[] src = sources.get(j);
            if (src[1] >= 0) {
                moves.add(new TileMoveT(rowIdx, src[0], rowIdx, j, out.get(j).value(), true));
                moves.add(new TileMoveT(rowIdx, src[1], rowIdx, j, out.get(j).value(), true));
                changed = true;
            } else if (src[0] != j) {
                moves.add(new TileMoveT(rowIdx, src[0], rowIdx, j, out.get(j).value(), false));
                changed = true;
            }
        }
        return new RowSlide(newRow, moves, delta, changed);
    }

    /** 游戏结束判定：无空位 且 无相邻相等（左右/上下）。 */
    private boolean hasNoMoves() {
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (grid[r][c].isEmpty()) {
                    return false;
                }
                if (r + 1 < size && grid[r][c].value() == grid[r + 1][c].value()) {
                    return false;
                }
                if (c + 1 < size && grid[r][c].value() == grid[r][c + 1].value()) {
                    return false;
                }
            }
        }
        return true;
    }

    /** 当前棋盘最大方块值。 */
    private int maxTile() {
        int max = 0;
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                max = Math.max(max, grid[r][c].value());
            }
        }
        return max;
    }

    /** 复位所有块的 merged 标记（一次移动结束后调用）。 */
    private void resetMergedFlags() {
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (grid[r][c].merged()) {
                    grid[r][c] = new Tile(grid[r][c].value(), false);
                }
            }
        }
    }

    private static Tile[][] emptyGrid(int size) {
        Tile[][] g = new Tile[size][size];
        for (int r = 0; r < size; r++) {
            Arrays.fill(g[r], Tile.EMPTY);
        }
        return g;
    }

    /** 棋盘深拷贝。 */
    private static Tile[][] deepCopy(Tile[][] g) {
        Tile[][] copy = new Tile[g.length][g.length];
        for (int r = 0; r < g.length; r++) {
            System.arraycopy(g[r], 0, copy[r], 0, g.length);
        }
        return copy;
    }

    /** 单行滑动结果（内部数据，坐标为变换棋盘坐标系）。 */
    private record RowSlide(Tile[] row, List<TileMoveT> moves, int delta, boolean changed) {
    }

    /** 变换坐标系下的单块移动记录（外部不可见，move() 中换算为原坐标系）。 */
    private record TileMoveT(int fromRow, int fromCol, int toRow, int toCol, int value, boolean isMerge) {
    }
}
