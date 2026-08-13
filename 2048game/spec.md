# 2048 小游戏（JavaFX）详细开发计划

> 目标：从零搭建一个功能完整、细节考究、可运行、可测试、可继续迭代的 2048 小游戏。
> 技术栈：Java 17 + JavaFX 17 + Maven。
> 约定：本文档只做需求与实现规划，作为开发阶段的唯一依据；开发时按里程碑逐步落地，每完成一个里程碑打一个 git 提交。
> **本版为"定制化"版本**：依据用户对定制问题的逐一回答生成，定制决策汇总见 §2.3，全篇所有设计均围绕这些决策展开。

---

## 1. 项目概述

2048 是一款单人数字合并益智游戏：N×N 棋盘（本项目支持 **3×3 ~ 8×8** 可变），每次移动使所有方块朝同一方向滑动，相同数字的方块相撞时合并为二者之和；每次有效移动后随机生成一个新方块（90% 为 2，10% 为 4）；当棋盘无法再产生任何移动（无空位且相邻无相等）时游戏结束；当合成出 2048 时获胜（可继续游戏）。胜利目标恒定为 2048（不随棋盘大小变化）。

本项目使用 **JavaFX** 构建桌面 GUI，采用 **纯逻辑引擎与渲染层分离** 的架构，保证核心游戏逻辑不依赖 JavaFX 运行环境，便于单元测试与移植。玩家可自定义：棋盘大小、深浅两套主题、中英双语文案、音效开关；提供 **撤销（Undo，Z 键）**、完整统计面板（步数/用时/历史最佳榜）、键盘 + 屏幕方向按钮双操作方式。

### 1.1 项目坐标（当前 pom.xml 已就绪）

| 项 | 值 |
|---|---|
| groupId / artifactId | `org.example` / `2048game` |
| Java | 17 |
| JavaFX | 17.0.14（javafx-controls + javafx-fxml） |
| 运行插件 | org.openjfx:javafx-maven-plugin:0.0.8 |
| 主类（插件已配置） | `org.example.App` |

> 注意：主类路径必须与 javafx-maven-plugin 的 `<mainClass>org.example.App</mainClass>` 保持一致，实现时不可移动包名。运行时音频仅用 JDK 自带 `javafx.scene.media.AudioClip`，**无需**额外引入 media 模块之外的依赖。

---

## 2. 需求分析

### 2.1 功能需求（FR）

| 编号 | 需求 | 优先级 |
|---|---|---|
| FR-1 | N×N 棋盘（N ∈ 3~8，默认 4），支持上下左右滑动 | P0 |
| FR-2 | 滑动规则：整行/整列压缩、相邻相等合并（每块每步最多合并一次） | P0 |
| FR-3 | 有效移动后随机生成新方块（2 占 90%、4 占 10%） | P0 |
| FR-4 | 实时计分：合并值累加（如 2+2 得 4 分） | P0 |
| FR-5 | 最高分持久化（Preferences，跨会话保留） | P0 |
| FR-6 | 方块移动/合并/生成的平滑动画 | P1 |
| FR-7 | 游戏结束判定与 "Game Over" 遮罩 + 重开按钮 | P0 |
| FR-8 | 合成 2048 判定、胜利遮罩，支持"继续游戏" | P1 |
| FR-9 | 键盘操作：方向键 + WASD；R 键快速重开 | P0 |
| FR-10 | 新游戏按钮（重新开始） | P0 |
| FR-11 | 初始局面：开局随机生成 2 个方块 | P0 |
| FR-12 | 数字字号随格宽与位数自适应、配色随数值变化 | P1 |
| FR-13 | **可变棋盘尺寸**：设置中可在 3×3 ~ 8×8 间选择，切换尺寸即开始新一局 | P0 |
| FR-14 | **双主题**：浅色（经典）/ 暗黑，一键切换、即时生效 | P1 |
| FR-15 | **中英双语**：全部文案走资源文件，切换即时生效 | P1 |
| FR-16 | **撤销（Undo，Z 键）**：回退上一步的棋盘与分数，深度上限 20 | P0 |
| FR-17 | **简单音效**：合并/胜利/游戏结束/按钮点击四类提示音 + 总开关 | P2 |
| FR-18 | **屏幕方向按钮**：界面提供 ↑↓←→ 四键，鼠标亦可操作 | P1 |
| FR-19 | **完整统计面板**：当前分数、最佳、步数、用时（mm:ss）、历史最佳 Top5 榜单 | P1 |
| FR-20 | **可缩放窗口**：支持拖拽拉伸，棋盘随窗口自适应保持正方形 | P1 |

### 2.2 非功能需求（NFR）

| 编号 | 需求 |
|---|---|
| NFR-1 | 纯逻辑层不 import 任何 JavaFX 类，可用 `mvn test` 无 GUI 测试 |
| NFR-2 | 所有 UI 操作均在 JavaFX 应用线程（FX Thread）中执行，无并发问题 |
| NFR-3 | 动画执行期间屏蔽新输入，保证逻辑与画面状态一致（防快速连按错乱） |
| NFR-4 | 代码职责单一、包结构清晰；**全部注释/文档使用中文** |
| NFR-5 | .gitignore 已覆盖 `target/`；构建产物不入库 |
| NFR-6 | 语言资源文件 key 命名集中管理，新增文案必须同步中英两份 |

### 2.3 定制决策汇总（由用户问答得出的定制点）

| # | 定制问题 | 用户选择 | 对计划的影响 |
|---|---|---|---|
| C1 | 棋盘规模与胜利目标 | 3×3~8×8 可变大小 | 引擎全 N 通用化；设置栏加尺寸选择（FR-13） |
| C2 | 视觉主题 | 双主题可切换 | 两套 CSS + 即时切换（FR-14） |
| C3 | 界面语言 | 中英双语可切换 | ResourceBundle 资源拆分 + 即时切换（FR-15） |
| C4 | 撤销功能 | 本期加入 Undo（Z 键） | 引擎快照栈 + UI 语义（FR-16） |
| C5 | 音效 | 简单音效 | 4 类 AudioClip + 开关（FR-17） |
| C6 | 操作方式 | 键盘 + 屏幕方向按钮 | 底部方向键盘区，与键盘共用同一入口（FR-18） |
| C7 | 最高分持久化 | Preferences 注册表 | 沿用 Preferences（FR-5） |
| C8 | 统计 | 完整统计面板 | 步数/计时/历史 Top5 榜单（FR-19） |
| C9 | 打包 | 够用即可：mvn javafx:run | 不引入打包插件，发布留 P2 |
| C10 | 窗口尺寸 | 可缩放 | 布局绑定自适应（FR-20） |
| C11 | 测试深度 | 引擎单元测试即可 | UI 手动验证，不加 TestFX |
| C12 | 注释语言 | 中文注释 | 全部中文化（NFR-4） |

---

## 3. 整体架构与目录结构

分层架构：

```
┌─────────────────────────────────────────────┐
│  表示层 ui/  (FXML + Controller + CSS +      │  ← 只做界面与编排，回调引擎
│  i18n 资源 + audio)                         │
├─────────────────────────────────────────────┤
│  应用层 App.java (Application 入口)          │  ← 装配与启动
├─────────────────────────────────────────────┤
│  逻辑层 game/ (GameEngine, Direction,        │  ← 纯 Java，无 JavaFX 依赖
│  UndoStack, GameStats, MoveResult)          │
├─────────────────────────────────────────────┤
│  持久化 Preferences (最高分 / 历史榜单)       │
└─────────────────────────────────────────────┘
```

目录结构（规划，后续开发严格按此创建）：

```
2048game/
├── pom.xml
├── spec.md
└── src/
    ├── main/
    │   ├── java/org/example/
    │   │   ├── App.java                     # 主入口 extends Application
    │   │   ├── game/                        # 纯逻辑层（禁止依赖 javafx.*）
    │   │   │   ├── Direction.java           # 方向枚举（含坐标变换辅助）
    │   │   │   ├── Tile.java                # 方块值封装 record（value + 合并标记）
    │   │   │   ├── GameEngine.java          # 核心引擎：N×N 棋盘、移动、合并、胜负
    │   │   │   ├── GameStats.java           # 一局统计：得分/步数（引擎侧，用时由 UI 计）
    │   │   │   ├── MoveResult.java          # 一次移动的结果（变化记录，供动画消费）
    │   │   │   ├── UndoManager.java         # 撤销栈（棋盘+分数快照，深度 20）
    │   │   │   └── ScoreStore.java          # Preferences 最高分 + 历史 Top5 榜单
    │   │   └── ui/
    │   │       ├── GameController.java      # 主控制器：输入、动画、主题/语言/音效/统计
    │   │       ├── ThemeManager.java        # 主题切换（两套 CSS 互切）
    │   │       ├── I18n.java                # ResourceBundle 加载与文案刷新
    │   │       ├── SoundPlayer.java         # AudioClip 音效播放（失败静默降级）
    │   │       └── TileViewFactory.java     # 依据数值生成方块节点（颜色/字号）
    │   └── resources/
    │       ├── fxml/game.fxml               # 主界面布局
    │       ├── css/game-light.css           # 浅色主题（经典配色）
    │       ├── css/game-dark.css            # 暗黑主题
    │       ├── i18n/messages_zh.properties  # 中文文案
    │       ├── i18n/messages_en.properties  # 英文文案
    │       └── audio/
    │           ├── merge.wav                # 合并音
    │           ├── win.wav                  # 胜利音
    │           ├── gameover.wav             # 失败音
    │           └── click.wav                # 按钮点击音
    └── test/java/org/example/game/
        └── GameEngineTest.java              # 引擎单元测试（JUnit 5，参数化覆盖 N）
```

---

## 4. 核心模块详细设计

### 4.1 逻辑层 game（纯 Java，全部中文注释）

#### 4.1.1 `Direction`（枚举）

- 成员：`UP, DOWN, LEFT, RIGHT`。
- 提供坐标变换辅助，实现"任意方向滑动 → 等价于向左滑动"：
  - `int[][] transform(Tile[][] grid)` / `inverseTransform`：按方向转置/翻转。
  - `int indexInRow(int row, int col)`：垂直方向上把列当作行处理。

#### 4.1.2 `Tile`

- `record Tile(int value, boolean merged)`：
  - `value`：0 表示空格；否则必为 2 的幂。
  - `merged`：标记本块在本步内是否已参与合并（保证"每块每步最多合并一次"）。仅在一次移动过程中有效，移动结束后复位。

#### 4.1.3 `GameEngine`（核心引擎，N×N 通用）

常量与状态：

```java
public static final int MIN_SIZE = 3;   // 最小棋盘 3×3
public static final int MAX_SIZE = 8;   // 最大棋盘 8×8
public static final int WIN_TARGET = 2048;   // 胜利目标恒定
```

状态字段：

- `Tile[][] grid`（尺寸 N×N，N 由构造器/新开局决定）。
- `GameStats stats`：`score`、`steps`（每次有效移动 +1）。
- `UndoManager undo`：快照栈（深度 20）。
- `boolean gameOver`、`boolean won`、`boolean continueAfterWin`。

对外方法（签名固化，方便单元测试）：

```java
public GameEngine(int size)                   // 指定尺寸空棋盘（3~8，越界抛 IllegalArgumentException）
public void startNewGame(int size)            // 重置为新尺寸开局（清空撤销栈、统计归零）
public void startNewGame()                    // 以当前尺寸重开
public Tile[][] getGrid()                     // 棋盘快照（防御性拷贝）
public MoveResult move(Direction dir)         // 核心：滑动+合并+计分+派发新块+胜负判定
public UndoResult undo()                      // 回退一步（详见 4.1.6）
public boolean canUndo()
public int getSize()
public int getScore() / getSteps()
public GameStats getStatsSnapshot()
public boolean isGameOver() / isWon()
public void continueAfterWin()                // 胜利后选择继续
public void spawnRandomTile(int count)        // 测试辅助：指定数量随机落子
```

#### 4.1.4 核心算法：单行向左滑动 `slideRow`

```
输入：长度为 N 的行（含 value/merged）
步骤：
  1. 取出所有非零块（保持顺序），写入临时数组
  2. 从左到右两两判断：
     若 i 与 i+1 值相等 且 两者 merged 均为 false：
        合并为 value*2，score += value*2，标记 merged=true，跳过 i+1
     否则：原样保留
  3. 左侧紧凑排布，右侧补空
  4. 若最终序列与输入不同 → 该行"发生了变化"
```

关键规则（官方 2048 行为，用测试锁定；长度为 N 时同样适用）：

- `[2,2,2,2] → [4,4,0,0]`（每块只合并一次，不产生连锁）
- `[2,2,2,0] → [4,2,0,0]`
- `[4,0,4,8] → [8,8,0,0]`（跨空位合并）
- `[4,4,4,0] → [8,4,0,0]`
- 单块：`[2,0,0,0] → [2,0,0,0]`（无变化）

`move(dir)` 完整流程：

```
1. 前置检查：gameOver 或（won 且未继续）→ 返回 moved=false 的空结果
2. 快照当前（grid + score）入撤销栈（仅当最终产生有效移动时才保留，见 4.1.6 优化）
3. 按 dir 做坐标变换，使问题等价为"每行向左"
4. 逐行 slideRow，记录每块 from→to、合并后新值，统计是否发生任何变动
5. 逆变换回原棋盘
6. 若未变动 → 丢弃快照，返回 MoveResult(moved=false)，不生成新块、不加分
7. 若变动：
   a. score 累加、steps +1
   b. spawnRandomTile()
   c. 检查 maxTile >= 2048 → won 标记（continueAfterWin 时保持可玩）
   d. 检查游戏结束：棋盘无空位 && 无相邻相等（左右/上下）
8. 返回 MoveResult
```

游戏结束判定算法：

```
无空格 AND
任意 (r,c) 与 (r+1,c) 或 (r,c) 与 (r,c+1) 的 value 均不相等 → game over
```

#### 4.1.5 `MoveResult`（供动画层消费的数据契约）

```java
record MoveResult(
    boolean moved,              // 本次是否有效移动
    int scoreDelta,             // 加分
    List<TileMove> moves,       // 每块 from(r,c) → to(r,c) 及旧值/新值
    TileSpawn spawned,          // 生成块的位置与值
    boolean gameOver,
    boolean winReached          // 本次产生新的 2048（或以上）
)
record TileMove(int fromRow, int fromCol, int toRow, int toCol, int value, boolean isMerge)
record TileSpawn(int row, int col, int value)
```

> 设计意图：**UI 只消费 MoveResult，不自己推导动画**，彻底杜绝"动画与逻辑不一致"。

#### 4.1.6 撤销 `UndoManager` 与 `UndoResult`

```java
public class UndoManager {
    // 每次有效移动前把（棋盘快照, score）压栈；栈上限 UNDO_DEPTH=20，超出丢弃最旧
    public void push(Tile[][] gridSnapshot, int score)
    public boolean canUndo()
    public UndoEntry pop()      // 返回 (grid, score)，并清除对应快照
}
record UndoResult(Tile[][] grid, int score, boolean revived)
// revived = 撤销前处于 gameOver/won 状态，撤销后复活可继续
```

- 入栈时机优化：**在确认移动有效后才入栈**（先算出 MoveResult 再决定），避免无效移动污染栈；实现为"先模拟后提交"：`move()` 内部先计算，若变动则 push 旧状态。
- `undo()` 行为约定：
  1. 栈空 → 返回空结果，无任何变化。
  2. 出栈 → 恢复棋盘与分数；`gameOver`/`won` 标记清除（复活）；`continueAfterWin` 复位。
  3. **steps 不回退**（步数代表操作次数，含撤销操作本身+1——见统计约定 §4.7）。
  4. 撤销不消耗/不生成新块。
- 新开局 / 切换尺寸时清空撤销栈。

#### 4.1.7 `GameStats`

- 字段：`score`、`steps`；随引擎走（撤销回退分数、步数不回退，且撤销操作本身使步数 +1——见 §4.7 全量约定）。
- `getStatsSnapshot()` 返回防御拷贝。

#### 4.1.8 `ScoreStore`（Preferences：最高分 + 历史榜单）

- 根节点：`Preferences.userRoot().node("2048game")`。
- 键设计（带版本前缀便于扩展）：

| 键 | 含义 |
|---|---|
| `version` | 存储结构版本号（当前 1） |
| `best-score` | 历史最高分（≥ 任一榜单条目的分数） |
| `history.{i}.score` | Top5 榜单第 i 条分数（i=1..5，按分数降序） |
| `history.{i}.size` | 对应棋盘尺寸 |
| `history.{i}.date` | 达成时间（epochMillis） |

- `record NewRecord(int score, int size, long date)`；接口：
  - `int loadBestScore()`
  - `List<HistoryEntry> loadHistory()`（Top5，不存在则空）
  - `void reportGameOver(int finalScore, int size)`：把本局成绩写入榜单并维护排序淘汰；若 > 历史最佳则更新 `best-score`。
- 写入失败（受限环境）→ 静默降级、仅记日志，不影响游戏。

### 4.2 应用层 `App.java`

```java
public class App extends Application {
    // start:
    // 1. FXMLLoader 加载 fxml/game.fxml
    // 2. Scene 创建（默认 600×760，minWidth/minHeight 见 4.5），加载 css/game-light.css
    // 3. stage 标题 "2048"（首启按当前语言刷新），resizable=true
    // 4. scene.root.requestFocus() 保证按键事件可达
    // 5. stage.show()
}
```

- 入口不做任何业务逻辑，只做装配；语言/主题初值由 GameController 的 `initialize()` 依 Preferences 或默认读取。

### 4.3 表示层 ui

#### 4.3.1 `game.fxml` 布局（可缩放 + 自适应）

```
BorderPane (id: root, 内边距 12)
├── top:    VBox
│   ├── HBox(标题栏)
│   │   ├── VBox { Label "2048"(标题型), Label "分数 / 最佳"(scoreBar) }
│   │   └── Button "新游戏"
│   └── HBox(设置栏)
│       ├── ComboBox 棋盘尺寸(3×3 … 8×8)   ← 切换即重新开局
│       ├── Button 主题切换(☀/🌙)
│       ├── Button 语言切换(中/EN)
│       ├── Button 音效开关(🔊/🔇)
│       ├── Button 撤销(↶)
│       └── Button 统计(📊)
├── center: StackPane(棋盘容器，id: boardArea)
│   ├── GridPane(底板：空位色块  N×N，按尺寸重建)
│   └── Pane(方块层 id: tileLayer，绝对坐标，动画容器)
└── bottom: HBox(方向键盘区，居中)
        Button ↑ / Button ↓ / Button ← / Button →
    └── (覆盖层 overlay：GameOverBox / WinBox / StatsBox 同一 StackPane 轮换显示)
```

布局与自适应要点：

- 棋盘容器 `boardArea` 通过绑定保持正方形：`min(boardArea 宽, 高) − 2×PADDING`，格子尺寸
  `cellSize = (boardSize − (N+1)×GAP) / N`，随窗口拉伸实时重算（监听场景宽高或使用 `Bindings`/布局回调）。常量：`PADDING=10, GAP=10`。
- 新建/重绘棋盘的统一入口 `rebuildBoard()`：先清空底板与方块层，再按 N×N 重建，供"开局/切尺寸/撤销刷新/重开"复用。
- 键盘与方向按钮**共用** `handleMove(Direction d)`，杜绝双通路逻辑分叉；方向按钮点击后立即 `root.requestFocus()` 归还键盘焦点。
- 覆盖层三态合一：`showOverlay(OverlayType.{NONE, GAME_OVER, WIN, STATS})`，半透明背景沿用当前主题。
- 统计面板：独立小卡片（VBox 文案行 + 关闭按钮），显示分数/最佳/步数/用时/榜单。

#### 4.3.2 方块渲染 `TileViewFactory`

```java
public static StackPane createTile(int value, double cellSize)   // 按数值+格宽生成节点
public static void restyleTile(StackPane tile, int value, double cellSize)  // 合并后更新
```

- 节点＝`StackPane` 内嵌 `Label`，CSS 类 `tile-2 … tile-2048 / tile-super`。
- 字号规则（与格宽联动 + 位数衰减，适配 3×3~8×8 任意格宽）：

| 位数 | 字号系数（× cellSize） |
|---|---|
| 1 位（2–8） | 0.48 |
| 2 位（16–128） | 0.40 |
| 3 位（256–1024） | 0.32 |
| 4 位（2048） | 0.26 |
| 5 位及以上（super） | 0.22 |

- 字符方向注意：长数字（如 131072）可等比再收缩，保证不溢出格宽。

#### 4.3.3 主控制器 `GameController`

状态字段：

- `GameEngine engine`（尺寸跟随设置）、`ScoreStore scoreStore`
- `Pane tileLayer`、`GridPane boardGrid`
- `Label scoreLabel / bestLabel / stepsLabel / timeLabel`
- `StackPane overlay`（三态复用）、`ComboBox<Integer> sizeBox`、切换按钮引用集
- `boolean animationLock`、`LocalDateTime startTime`、`AnimationTimer timer`（用时刷新）
- `ThemeManager theme`、`I18n i18n`、`SoundPlayer sound`

按键处理（Scene `KEY_PRESSED` 过滤器）：

```
方向键 / WASD → handleMove(dir)
Z / Ctrl+Z   → handleUndo()
R            → handleNewGame()
其余按键不拦截
```

`handleMove(dir)` 流程：

```
1. animationLock 或 gameOver（且未撤销）→ 忽略
2. 若为第一步 → 启动计时（startTime 置位，AnimationTimer 每秒刷新 timeLabel）
3. engine.move(dir) → MoveResult：
   - moved=false → 无动作（可不发音效）
   - moved=true  → playMerge 依据（有合并才播合并音）；走动画流程（见 4.3.4）
   - winReached → 动画结束后弹胜利遮罩（含"继续游戏"按钮+胜利音效）
   - gameOver   → 动画结束后：ScoreStore.reportGameOver(...) 更新榜单与最佳；弹失败遮罩+音效；计时暂停
```

`handleUndo()` 流程：

```
1. animationLock → 忽略
2. engine.undo() → 空栈则忽略；成功：
   - 停止计时（若撤销前已 game over / 撤销后棋盘为空即首步未走则复位计时器）
   - rebuildBoard()（不播逆向动画，直接重建，简单可靠）
   - 刷新所有统计文案；隐藏遮罩；playClick
```

#### 4.3.4 动画编排（同 M5 锁定验收）

```
1. animationLock = true
2. 依 MoveResult.moves 批量播放：每块 TranslateTransition(120ms, EASE_IN_OUT)
3. 全部 move 完成后：
   - 合并块：原节点移除，在目标位新建合并值节点 + ScaleTransition(0.5→1.1→1.0, 100ms)
4. 生成块：目标位新建节点 + Fade/ScaleTransition(160ms)
5. 刷新 scoreLabel/bestLabel/stepsLabel；maxTile 样式更新
6. animationLock = false；若需要弹遮罩则显示
```

#### 4.3.5 主题切换 `ThemeManager`

- 两套样式表 `game-light.css` / `game-dark.css`，内容结构完全同构（同一组 lookup 色值变量名）。
- 切换实现：`scene.getStylesheets()` 移除旧主题、添加新主题，即时生效，不重建场景。
- 当前主题存 Preferences（键 `theme`，可选），下次启动沿用。

#### 4.3.6 双语切换 `I18n`

- 资源：`i18n/messages_zh.properties`、`i18n/messages_en.properties`。
- `I18n` 类封装：`String t(String key)`；维护"需要刷新的控件→key"注册表，切换语言时遍历执行 `label.setText(t(key))`，**按钮/遮罩/统计面板/标题栏全量刷新，不得遗漏**。
- 所有用户可见文案一律走 key，禁止硬编码中文/英文。
- 当前语言存 Preferences（键 `lang`），启动恢复。

#### 4.3.7 音效 `SoundPlayer`

- 使用 `javafx.scene.media.AudioClip` 加载 4 个 wav（资源打包于 classpath）：
  - `merge.wav`（有合并发生时）、`win.wav`、`gameover.wav`、`click.wav`（按钮/开关）。
- `toggleEnabled()` 全局开关（Preferences 键 `sound`，面板上 🔊/🔇）。
- 加载失败一律 catch 静默（音频属体验增强，绝不影响玩法）。

### 4.4 样式规范（双主题）

公共基础：方块圆角 6px；字体加粗（family "System"，不依赖第三方字体）。

**浅色主题（经典，`game-light.css`）**：

| 元素/数值 | 背景色 | 文字色 |
|---|---|---|
| 窗体背景 | #faf8ef | — |
| 棋盘底板 | #bbada0 | — |
| 空位 | #cdc1b4 | — |
| 2 | #eee4da | #776e65 |
| 4 | #ede0c8 | #776e65 |
| 8 | #f2b179 | #f9f6f2 |
| 16 | #f59563 | #f9f6f2 |
| 32 | #f67c5f | #f9f6f2 |
| 64 | #f65e3b | #f9f6f2 |
| 128 | #edcf72 | #f9f6f2 |
| 256 | #edcc61 | #f9f6f2 |
| 512 | #edc850 | #f9f6f2 |
| 1024 | #edc53f | #f9f6f2 |
| 2048 | #edc22e | #f9f6f2 |
| >2048 (super) | #3c3a32 | #f9f6f2 |
| 标题/次要文字 | — | #776e65 |
| 分数框 | #bbada0 | #f9f6f2 |

**暗黑主题（`game-dark.css`，配色经对比度自查，浅字在深底上均 ≥ 4.5:1）**：

| 元素/数值 | 背景色 | 文字色 |
|---|---|---|
| 窗体背景 | #1a1c22 | — |
| 棋盘底板 | #23262e | — |
| 空位 | #2e3138 | — |
| 2 | #41444f | #e8e8e8 |
| 4 | #565a66 | #f5f5f5 |
| 8 | #b07a3f | #f5f5f5 |
| 16 | #b0643a | #f5f5f5 |
| 32 | #b04936 | #f5f5f5 |
| 64 | #b03a2c | #f5f5f5 |
| 128 | #b09a2e | #1a1c22 |
| 256 | #c2a81f | #1a1c22 |
| 512 | #d4b612 | #1a1c22 |
| 1024 | #e6c400 | #1a1c22 |
| 2048 | #f2cc0a | #1a1c22 |
| >2048 (super) | #e8e8e8 | #1a1c22 |
| 标题/次要文字 | — | #e8e8e8 |
| 分数框 | #2e3138 | #e8e8e8 |

> 两套 CSS 必须同构：颜色一律用 lookup 变量（`.root { -board-bg: ...; }` 形式），Tile 类名与字号类一致，仅换色值。

### 4.5 窗口与自适应布局规格

- 默认 600×760；`minWidth=480`、`minHeight=620`；`resizable=true`。
- 棋盘保持正方形：`boardSize = min(boardArea可用宽, 可用高) - 2×PADDING`；
  `cellSize = (boardSize - (N+1)×GAP) / N`；窗口变化时监听并 `rebuildBoard()`（或仅重排，不重建节点以省性能——实现时选择：尺寸变化只重算布局，仅 N 变化才重建）。
- 布局公式封装进 `ui/BoardLayout.java`（或 GameController 私有方法），集中管理 PADDING/GAP 常量。

### 4.6 统计面板规格（FR-19）

游戏内实时区（标题栏）：分数、最佳、步数、用时（mm:ss）。

| 统计项 | 规则 |
|---|---|
| 分数 | 游戏内累加；undo 回退分数 |
| 步数 | 每次有效移动 +1；**undo 使步数 +1**（视为一次操作）；新开局清零 |
| 用时 | 首步有效移动开始计时；game over / win 遮罩弹出时暂停；undo 不回溯时间；新开局清零 |
| 历史 Top5 | `ScoreStore.reportGameOver(最终分, 棋盘尺寸)` 于 game over 时写入；榜单按分数降序，最多 5 条，新成绩插入、超出淘汰第 6 名；同分按时间新者优先 |

统计面板（📊 按钮弹出）：当前分数 / 最佳 / 步数 / 用时 / 历史 Top5 列表（"分数 × 尺寸 · 日期"格式），关闭按钮；非遮罩态，可随时开合。

### 4.7 i18n 文案 key 表（双语必全）

| key | 中文 | 英文 |
|---|---|---|
| app.title | 2048 | 2048 |
| score | 分数 | Score |
| best | 最佳 | Best |
| newGame | 新游戏 | New Game |
| undo | 撤销 | Undo |
| boardSize | 棋盘 | Board |
| theme.light / theme.dark | 浅色 / 暗黑 | Light / Dark |
| sound.on / sound.off | 音效开 / 音效关 | Sound On / Sound Off |
| stats | 统计 | Stats |
| steps | 步数 | Moves |
| time | 用时 | Time |
| history | 历史最佳 | Best Records |
| gameOver.title | 游戏结束 | Game Over |
| gameOver.tryAgain | 再来一局 | Try Again |
| win.title | 你赢了！ | You Win! |
| win.keepGoing | 继续游戏 | Keep Going |
| sizeFormat | {0}×{0} | {0}×{0} |

> 新增文案必须同时维护中英两份，缺失时 I18n 记录 warn 并回退中文。

---

## 5. 边界情况与错误处理

| 场景 | 行为约定 |
|---|---|
| 空棋盘 `move()`（测试构造） | moved=false，不生成块 |
| 方向滑不动（全被墙挡住） | moved=false，不加分/不生成块/不入撤销栈 |
| 快速连按方向键 | animationLock 期间丢弃输入（宁可"丢"不可"乱"） |
| 已 game over 后按方向键 | 忽略；允许 **Z 撤销复活**；遮罩按钮/R 键可用 |
| 已 won 且未继续时移动 | 屏蔽移动，仅响应"继续/重开/撤销" |
| 2048 后继续玩到 4096/8192 | super 分支样式，可继续到无法移动 |
| 撤销栈空时按 Z | 无任何反应 |
| 连续撤销耗尽历史后 | 回到首步前初始状态，此后 undo 无效果 |
| 切换棋盘尺寸 | 视为新开局：重置引擎、撤销栈、统计、计时、遮罩（不弹确认，尺寸栏旁文案提示"切换即重开"） |
| 窗口极速拉伸（拖动中） | 布局回调节流（AWT FP 或简单 throttle），避免频繁 rebuild |
| 音频文件缺失/格式不支持 | 静默失败，游戏不受影响 |
| Preferences 写入失败 | 静默降级 + 日志，榜单/最佳本轮内仍内存生效 |
| 合并产生的最终块恰好填满棋盘 | 仍按"无空位&&无相邻相等"判定，不特殊处理 |
| 尺寸构造函数越界（<3 或 >8） | 抛 IllegalArgumentException（测试锁定） |

---

## 6. 测试计划（JUnit 5，`mvn test` 无 GUI 运行）

引擎测试 `GameEngineTest`（TDD 优先，核心保障；**关键用例用参数化测试覆盖 N=3、4、8**）：

1. 构造校验：size=2 / 9 → 抛 IllegalArgumentException；size=3/4/8 → 正常。
2. 初始局面：startNewGame 后恰好 2 个非零块（各尺寸）。
3. 滑动基本：LEFT `[2,0,0,0] → [2,0,0,0]`（无变化）；`[0,2,0,2] → [4,0,0,0]`。
4. 一次合并规则（N=4）：
   - `[2,2,2,2] → [4,4,0,0]`（无连锁）
   - `[2,2,2,0] → [4,2,0,0]`
   - `[4,4,8,8] → [8,16,0,0]`
   - `[2,0,2,4] → [4,4,0,0]`
   - **N=3 版**：`[2,2,2] → [4,2,0]`；**N=8 版**：`[2,2,2,2,2,2,2,2] → [4,4,4,4,0,0,0,0]`
5. 方向正确性：同一局面分别 UP/DOWN/LEFT/RIGHT 与手推结果一致（各尺寸抽测）。
6. 计分：合并 2+2 得 4 分；8+8 得 16 分；多组合并累计正确。
7. 无效移动：无变化方向返回 moved=false 且不 spawn、不加分、**不入撤销栈**。
8. 生成块概率：大样本统计 2 约占 90%（容差 ±2%）。
9. game over 判定：满盘无合并 → true；有合并可能 → false（含 N=3、N=5 构造）。
10. win 判定：构造到达 2048 的合并 → winReached=true、isWon()=true；continue 后继续移动。
11. **撤销**：
    - 有效移动后可 undo，棋盘与分数精确还原；
    - 无效移动后 undo 无效果（栈未被污染）；
    - 新开局后栈清空；
    - 深度上限 20：连续 30 次有效移动后只能回退 20 次。
12. 步骤统计：有效移动 steps+1；**undo 使步数 +1（视为一次操作，见 §4.7 语义）**；无效移动 steps 不变。
13. 防御性拷贝：getGrid() 返回值修改不影响内部状态。

UI 层：按定制决策 **不做自动化**，全部手动验证（见 §7 各里程碑验收）。

---

## 7. 里程碑与验收标准（每完成一个打 git 提交）

| 里程碑 | 内容 | 验收标准 |
|---|---|---|
| **M1 骨架** | 目录/资源骨架；App 启动窗口；可缩放 + 棋盘容器正方形自适应基础；game 包空类 | `mvn javafx:run` 弹出 600×760 窗口，拉伸时中央占位正方形随之自适应；min 尺寸生效 |
| **M2 引擎** | Direction/Tile/GameEngine/MoveResult/UndoManager/GameStats/ScoreStore + 全量单测 | `mvn test` 全绿（§6 清单 1–13） |
| **M3 静态棋盘+设置栏** | FXML 全布局；底板+TileViewFactory+开局渲染；尺寸 ComboBox（3~8）、主题/语言/音效开关、undo、统计按钮；light/dark 两套 CSS；i18n 双资源 | 任意尺寸开局渲染正确；主题/语言/音效开关即时生效且不重启；按钮齐全 |
| **M4 交互** | 键盘（方向/WASD/R/Z） + 底部方向按钮共用 handleMove；纯逻辑驱动重绘（暂不做动画） | 每步与官方 2048 行为一致；方向按钮点击后键盘焦点不错乱；undo 即时重建局面 |
| **M5 动画** | TileMove 驱动的滑移/合并/生成动画 + animationLock；棋盘随尺寸变化的布局重算 | 动画流畅（120ms），连按不错乱，动画结束画面与引擎一致 |
| **M6 统计与遮罩** | 计分/最佳/步数/用时实时显示；历史 Top5 榜单；Stats 面板；Game Over/胜利+继续遮罩；定时器启停规则 | 手动验证：Metrics 全对、重启程序最佳与榜单还在、两遮罩与计时启停符合 §4.6 |
| **M7 音效与打磨** | SoundPlayer 四音效；焦点与按钮防吞键；字号自适应终版；super 配色；I18n 全量刷新自查；code review | FR-1~FR-20 全部验收；`mvn clean test` + `mvn javafx:run` 一次通过；中英切换后**每个控件**文案正确 |

提交规范：`docs: spec 定制计划`、`feat: M1 骨架`、`feat: M2 引擎+测试`、`feat: M3 静态棋盘与设置`、`feat: M4 交互`、`feat: M5 动画`、`feat: M6 统计与遮罩`、`feat: M7 音效与打磨`。

---

## 8. 构建与运行手册

```bash
# 编译 + 运行全部单元测试（无 GUI 环境亦可）
mvn clean test

# 启动游戏（开发调试）
mvn javafx:run
```

- 环境要求：JDK 17+；本机已具备 Maven wrapper（`.mvn/` 存在，可用 `./mvnw` 等价命令）。
- **音效资源**：4 个 wav 由开发期生成（可用在线合成工具或脚本生成短音），随后放入 `src/main/resources/audio/` 并随项目提交——属于项目资产，非构建产物。
- 打包发布：按定制决策本期不引入（候选 jpackage 留 P2）。

---

## 9. 后续增强（P2 队列，不在本期范围内）

1. **触摸/滑动支持**：JavaFX SwipeEvent / 移动端适配。
2. **发布安装包**：jpackage 生成 Windows 安装包 / 启动器。
3. **按棋盘尺寸缩放胜利目标**（如 3×3 目标 512、8×8 目标 8192）与"挑战模式"。
4. **连击/大数字特效**：粒子、背景色阶跃过渡。
5. **移动端（Gluon/JFXMobile）或 Web 移植**：得益于纯逻辑层，天然可移植。
6. **音效进阶**：移动音、随机变调、音量滑杆。

---

## 10. 风险与对策

| 风险 | 对策 |
|---|---|
| FXML 与 Controller 类名/包名不匹配导致加载失败 | M1 先跑通 FXML→Controller 链路；命名严格按 §3 |
| 动画期间输入错乱 | animationLock 全局开关 + 手测连按 |
| 方向按钮/ComboBox 持有焦点吞键盘事件 | 按键监听放 Scene 过滤器 + 点击后 `root.requestFocus()` |
| 双语切换遗漏个别文案 | I18n 注册表集中管理；M7 做逐一控件自查清单 |
| 两套主题切换后个别颜色未同步 | CSS 全部走 lookup 变量、两文件同构；M3 验收时比对清单 |
| 自适应布局随窗口抖动/频繁重建 | 尺寸变化只重排不重建；节流拉伸回调 |
| Windows 字体渲染差异 | 只用系统 family "System"；字号系数表含裕量 |
| 高 DPI 模糊 | JavaFX 自动 HiDPI 缩放；固定 min 尺寸防过度压缩 |
| Preferences 受限环境 | ScoreStore 静默降级 + 日志（历史榜单内存生效） |
| 撤销栈与画面不同步 | undo 后走 rebuildBoard 全量重建，杜绝残留节点 |
| javafx-maven-plugin 与主类不一致 | 主类钉死 `org.example.App`，M1 即验证 |

---

## 11. 验收总清单（对照 FR / 定制决策）

- [ ] FR-1~FR-20 逐项勾验（实现阶段每完成一项勾一项）
- [ ] 定制点 C1~C12 全部落地（对照 §2.3 表）
- [ ] `mvn clean test` 全绿
- [ ] `mvn javafx:run` 启动无警告异常
- [ ] 切换主题/语言/音效/尺寸全程不重启、即时生效
- [ ] 中英切换后无一处文案残留
- [ ] 3×3、4×4、8×8 各完整玩一局直至 Game Over（共 3 局冒烟）
- [ ] 连续游玩 20 分钟无状态错乱（手测冒烟）