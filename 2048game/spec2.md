# 2048 界面美化大改版计划（spec2 — 现代柔和渐变卡片风）

> 目标：在**已完成的 spec.md 项目**基础上，仅对**表示层（UI）**做一次"更好看、非传统"的大改版。
> 核心约束：
> - **逻辑层 `org.example.game` 与音效资源完全不动**，`mvn test`（引擎/存储/布局测试）仍须全绿。
> - 不引入任何第三方依赖，全部效果基于 JavaFX 17 原生能力（javafx-graphics 已含 GaussianBlur / SVGPath / Text 渐变 / DropShadow / 各类 Transition）。
> - 沿用既有规范：全中文注释、中英双语走资源文件、主题即时切换、动画期间 `animationLock` 屏蔽输入。
> - 每完成一个里程碑打一个 git 提交。

---

## 1. 范围说明

| 项 | 是否修改 |
|---|---|
| `org.example.game.*`（引擎/规则/存储） | 否 |
| 音效 wav、`SoundPlayer` | 否 |
| `org.example.App` | 仅启动装配微调（注册字体、挂载背景层） |
| `org.example.ui.GameController / TileViewFactory / ThemeManager` | 改造 |
| `game.fxml` | 大改（移动端式布局） |
| `game-light.css / game-dark.css` | 全量重写（新配色+新质感） |
| 新增类 | `FontKit`、`GlowBackground`、`Icons`、`effect.EffectManager`（合并爆点/飘字/彩带） |
| `BoardLayout` | `GAP` 10 → **12**（配合新风格更透气），同步更新 `BoardLayoutTest` 硬编码断言 |

---

## 2. 设计方向总览（依据用户问答）

| # | 决策点 | 结论 |
|---|---|---|
| D1 | 整体视觉风格 | **现代柔和渐变卡片风**：渐变背景 + 大圆角卡片 + 轻柔阴影 + 精致光效 |
| D2 | 主题机制 | 保留浅/暗两套切换，**两套全新配色与质感重设计** |
| D3 | 背景 | **缓慢流动的渐变光晕**（低性能开销） |
| D4 | 特效程度 | 中等：合并光晕+碎片粒子、生成微光、合计分飘字、胜利撒彩带 |
| D5 | 布局 | **大幅改版为移动端式**：底部圆钮菱形方向键 + 胶囊工具条 + 图标化设置 |
| D6 | 字体 | **打包 MiSans 开源中文字体**（单文件放 `resources/fonts/`），观感统一精致 |
| D7 | 棋盘间距 | `GAP` 10 → 12（配合新风格更透气） |
| D8 | 语言/主题切换 | 沿用 `I18n` / `ThemeManager` 机制，仅重写样式与控件外观 |
| D9 | 可访问设置 | 尺寸/主题/语言/音效收纳为图标圆钮 + pill 下拉，主界面更干净 |

---

## 3. 布局重构（`game.fxml` 大改，移动端式）

### 3.1 目标结构

```
StackPane root(id=root)
├── Pane (id=bgLayer)                    ← 新增：动态光晕背景（GlowBackground）
└── BorderPane (id=content)              ← 透明；承载主内容
    ├── top: VBox
    │   ├── header HBox
    │   │   ├── Text "2048"（渐变填充品牌字，id=titleText；Text.fill 支持渐变色）
    │   │   ├── Region(HBox.hgrow=ALWAYS)
    │   │   ├── 胶囊卡(score): 纵排 Label scoreCaption / scoreLabel
    │   │   └── 胶囊卡(best): 纵排 Label bestCaption / bestLabel
    │   └── stats 行 HBox
    │       ├── 胶囊(size): Label sizeHint + ComboBox sizeBox（pill 样式）
    │       ├── 小胶囊(steps): stepsCaption / stepsLabel
    │       ├── 小胶囊(time): timeCaption / timeLabel
    │       ├── Region(HBox.hgrow=ALWAYS)
    │       ├── 图标圆钮 themeButton（☀/🌙）
    │       ├── 图标圆钮 langButton（中/EN）
    │       ├── 图标圆钮 soundButton（🔊/🔇）
    │       └── 图标圆钮 statsButton（📊）
    ├── center: StackPane (id=boardArea)
    │   ├── VBox(id=glassCard)           ← 玻璃卡片容器：半透明底+渐变描边+柔和阴影，包住棋盘
    │   │   ├── GridPane boardGrid（底板 N×N，GAP=12）
    │   │   └── Pane tileLayer（方块层，动画容器）
    │   └── StackPane overlay（三态遮罩，新玻璃卡片样式）
    └── bottom: VBox
        └── 胶囊工具条 HBox（id=toolbar，圆角 R=999 半透明条）
            ├── Button undoButton（↶ 撤销，胶囊）
            ├── Region(HBox.hgrow=ALWAYS)
            ├── GridPane(菱形方向键 3×3):
            │       (0,1)=upButton  (1,0)=leftButton  (1,2)=rightButton  (2,1)=downButton
            │       （四键为圆形按钮，呈 X 形菱形布局）
            ├── Region(HBox.hgrow=ALWAYS)
            └── Button newGameButton（新游戏，强调渐变胶囊）
```

### 3.2 FXML 与控制器要点

- **fx:id 尽量沿用**：`root/boardArea/boardGrid/tileLayer/overlay/score*/best*/steps*/time*/sizeHintLabel/sizeBox/newGameButton/themeButton/langButton/soundButton/undoButton/statsButton/up/down/left/rightButton/gameOverBox/winBox/statsBox/*Label` 均保留，控制器事件绑定与文案刷新大多不变，降低回归风险。
- 新增：`bgLayer`、`glassCard`、`titleText`、`toolbar`；方向键改为 `GridPane` 菱形定位（原为一行四键）。
- `root` 由 `BorderPane` 改为 **`StackPane`**（背景层在下、内容在上）；内容仍用 `BorderPane`(id=content) 分 top/center/bottom。
- 方向键事件绑定不变，仍共用 `handleMove(Direction)`，点击后 `root.requestFocus()` 归还焦点。
- 图标圆钮用 `Icons` 的 SVGPath 图形 + Tooltip（走 i18n），不用 emoji 以免跨平台渲染不一致；品牌字用 `Text` 实现渐变填充。

---

## 4. 新增与改造类清单

### 4.1 新增 `ui/FontKit.java`
- 启动时一次性 `Font.loadFont(getClass().getResourceAsStream("/fonts/MiSans.ttf"), size)` 注册。
- 暴露 `family()` 与字号辅助；加载失败**静默回退** `"System"`，绝不影响启动。
- `TileViewFactory` 方块数字改用该 family，CSS 全局字体亦指向该 family。

### 4.2 新增 `ui/GlowBackground.java`
- 一个 `Pane`（挂载到 `bgLayer`），内含 3~4 个 `Circle`：
  - `fill = RadialGradient`（主题色、低透明度）+ `GaussianBlur`（大半径柔化）。
  - 各自 `TranslateTransition`（周期 6~10s、AUTO_REVERSE）缓慢漂移，形成"流动光晕"。
- 颜色取自当前主题（由 `ThemeManager`/CSS lookup 联动），主题切换时刷新。
- 拉伸不变形、无闪烁；仅用 Transition 不占用 AnimationTimer，省电。

### 4.3 新增 `ui/Icons.java`
- 静态方法返回 `SVGPath`（齿轮/音量/太阳/月亮/撤销/统计/星星），供图标按钮与工具条使用。
- 颜色继承按钮 CSS 文字色，hover 变亮。

### 4.4 新增 `ui/effect/EffectManager.java`（中等特效）
- `mergeBurst(x, y, color)`：目标格处 6~10 个同色小圆/碎片向四周飞散 + FadeOut（~150ms）。
- `spawnGlow(node)`：生成块淡入时附带短暂光晕。
- `scorePopup(x, y, "+N")`：合并处上浮飘字渐隐（~700ms），字用 `FontKit` family。
- `confetti(boardW, boardH)`：胜利时 20~30 条彩色小矩形自上旋转下落 + 淡出，衬在胜利遮罩之后。
- 全部纯 JavaFX Transition；粒子数量上限、`onFinished` 移除节点，杜绝泄漏；受 `animationLock` 时序约束。
- 调用点集中在 `GameController`：`finishMoveAnimation` 里合并后调 `mergeBurst`+`scorePopup`，生成块调 `spawnGlow`，`afterMove` 胜利分支调 `confetti`。

### 4.5 改造 `ui/TileViewFactory.java`
- 方块：渐变填充（竖向渐变模拟内高光）+ 大圆角（≈ cellSize×0.12）+ 柔和阴影 + 顶部细高光边。
- 保留"字号随格宽与位数衰减"逻辑，字体改为 `FontKit` family；`restyleTile` 同步更新渐变与尺寸。

### 4.6 改造 `ui/ThemeManager.java`
- 主题文件仍为 `game-light.css` / `game-dark.css`，切换逻辑不变（stylesheets 互切 + Preferences 持久化）。
- 增加可扩展点：主题名列表（`light/dark`，预留未来 >2 套），本次仍为两套。

### 4.7 改造 `ui/GameController.java`
- 挂载 `bgLayer` 的 `GlowBackground`；`titleText` 渐变填充（随主题刷新）。
- 在 `finishMoveAnimation` / `afterMove` 调用 `EffectManager` 特效。
- `attach()` 里先 `FontKit.load()`；其余既有逻辑（输入/动画/遮罩/计时/统计/撤销）不改动。

### 4.8 改造 `ui/BoardLayout.java` + 测试
- `GAP` 10 → 12；`PADDING` 10 → 14（配合卡片留白）。
- **同步更新 `BoardLayoutTest`** 依赖 GAP 的硬编码断言：
  - `boardSize(500,400) = 372`（400 − 2×14）；
  - `cellSize(500,400,4) = (372 − 5×12)/4 = 78.0`；
  - N=8：`(372 − 9×12)/8 = 33.0`；N=3：`(372 − 4×12)/3 = 108.0`；
  - `cellX/cellY` 间距断言用 `BoardLayout.GAP` 常量表达；
  - `boardSide` 与 `cellSize` 互逆关系保持不变（用新值复核）。
- 引擎测试 `GameEngineTest`、`ScoreStoreTest` 不受影响。

---

## 5. 双主题配色方案（全量重写 CSS）

> 两套 CSS **同构**：`.root` 定义 lookup 变量，布局/控件样式一致，仅色值与材质不同。

### 5.1 风格基调
- **背景**：整体渐变背景由 CSS 提供底色 + `GlowBackground` 光晕叠加；卡片为半透明 + 顶部高光边 + 阴影（伪磨砂，JavaFX 无 backdrop-filter）。
- **圆角**：卡片 R=14~18；胶囊按钮 R=999；棋盘格 R≈10；方向键圆形。

### 5.2 浅色主题 `game-light.css`

| 元素 | 值 |
|---|---|
| 窗体背景 | 奶油→浅灰蓝渐变（`#f7f8fc → #edf0f7`） |
| 卡片底 | `rgba(255,255,255,0.62)` + 顶部 `rgba(255,255,255,0.85)` 高光边 |
| 卡片描边/阴影 | `rgba(120,130,160,0.18)` 描边；`rgba(90,100,130,0.15)` 柔和阴影 |
| 文字主/次 | `#4a4f5c` / `#9aa0ab` |
| 强调色 | 橙→珊瑚渐变 `#ff9f43 → #ff6b6b` |
| 方块 2 | `#f5efe0 → #efe4cf`（柔和渐变，深字） |
| 方块 4 | `#f3e6c8 → #ecdcb4`（深字） |
| 方块 8~64 | 暖橙→珊瑚渐变逐级加深（`#ffd0a8→#f98f5f` … `#f65e3b`），浅字 |
| 方块 128~1024 | 金黄渐变 + 深字（`#5a4a1a`） |
| 方块 2048 | 亮金渐变 `#ffd775→#f7c522` + 发光投影，浅字 |
| super(>2048) | 深炭渐变 `#3a3d46→#22252c`，浅字 |
| max 呼吸光晕 | Timeline 脉冲 dropshadow `#ffd27a` |
| 遮罩 | 半透明白 `rgba(247,248,252,0.72)` |

### 5.3 暗黑主题 `game-dark.css`

| 元素 | 值 |
|---|---|
| 窗体背景 | 深蓝夜渐变 `#141923 → #20263a`（叠加靛蓝/青光晕） |
| 卡片底 | `rgba(255,255,255,0.06)` + 顶部 `rgba(255,255,255,0.12)` 高光边 |
| 卡片描边/阴影 | `rgba(120,160,255,0.15)` 描边；`rgba(0,0,0,0.45)` 阴影 |
| 文字主/次 | `#e6e9f2` / `#98a0b3` |
| 强调色 | 青→紫渐变 `#22d3ee → #7c6cf0` |
| 方块 2 | `#2c3040 → #34384a`，浅字 |
| 方块 4 | `#3a3f54 → #43495f`，浅字 |
| 方块 8~64 | 深橙/珊瑚暗渐变逐级点亮（`#8a4d2e→#a3492f` …），浅字 |
| 方块 128~1024 | 金黄暗渐变（`#b09a2e→#c2a81f` 等）+ 深字 |
| 方块 2048 | 亮金渐晕 `#ffcf4d→#e8b823` + 发光投影，深字 |
| super(>2048) | `#eef0f5→#d8dce8`，深字 |
| max 呼吸光晕 | Timeline 脉冲 dropshadow `#ffc14d` |
| 遮罩 | 半透明深蓝 `rgba(20,25,35,0.78)` |

> 渐变起止方向落地时按实际观感微调；两文件必须同构，新增控件样式两套都补。

---

## 6. 字体方案（MiSans）

- 字体文件：**MiSans**（开源、免费商用），单文件放入 `src/main/resources/fonts/MiSans.ttf`（约 5~10MB）。
- 获取：从 MiSans 官方开源仓库下载 TTF；若网络不可达则向用户索要文件。
- `FontKit` 启动注册一次；全局控件与方块数字统一使用；失败回退 `"System"`。
- 品牌 "2048" 用 `Text` + `LinearGradient` 填充。
- 新增 Tooltip/文案必须同步维护 `messages_zh/en.properties`（沿用 NFR-6 规则）。

---

## 7. 动画与常量微调

| 项 | 现值 | 新值 | 说明 |
|---|---|---|---|
| `GAP` | 10 | **12** | 更透气；同步改 `BoardLayoutTest` |
| `PADDING` | 10 | **14** | 配合卡片留白 |
| 移动动画 | 120ms | 140ms | 更舒缓典雅 |
| 合并弹出 | 100ms | 120ms | 配合爆点粒子 |
| 生成动画 | 160ms | 180ms | 配合微光 |
| max 块 | 静态投影 | **呼吸脉冲** 光晕 | Timeline 循环，柔和 |
| `animationLock` | — | 沿用 | 特效均在锁内/收尾触发，防连按错乱 |

---

## 8. 里程碑与验收（每步一提交）

| 里程碑 | 内容 | 验收标准 |
|---|---|---|
| **V1 布局重构** | 新 FXML（StackPane 背景层 + content 分区 + 胶囊工具条 + 菱形圆钮方向键 + glassCard）；控制器适配；`BoardLayout` GAP=12/PADDING=14 + 同步 **BoardLayoutTest** | 全部控件可用；键盘/方向钮/撤销/尺寸切换/新游戏/统计不回归；`mvn test` 全绿 |
| **V2 双主题+字体** | 重写两套 CSS；`FontKit` 接入 MiSans；`titleText` 渐变品牌字；`TileViewFactory` 渐变方块 | 两主题即时切换且质感一致；字体全局生效；中文/数字观感统一；字体缺失回退不崩 |
| **V3 动态背景** | `GlowBackground` 光晕挂载、主题联动 | 流动自然；拉伸不变形不闪烁；闲置时低 CPU |
| **V4 特效** | `EffectManager`：合并爆点、飘字、生成微光、胜利彩带、max 呼吸光晕 | 特效触发时机正确；与动画锁协同不错乱；粒子及时回收 |
| **V5 打磨** | 遮罩/统计面板新样式与过渡；阴影/字号微调；中英/两主题全量自查；code review | 浅/暗两主题 × 3×3/4×4/8×8 三尺寸冒烟完整；`mvn clean test` + `mvn javafx:run` 通过；无文案残留 |

提交规范：`docs: spec2 UI 美化计划`、`style: V1 布局重构`、`style: V2 双主题与字体`、`style: V3 动态背景`、`style: V4 特效`、`style: V5 打磨`。

---

## 9. 风险与对策

| 风险 | 对策 |
|---|---|
| JavaFX 无真正的 backdrop-filter 毛玻璃 | 用"半透明 + 渐变 + 顶部高光边 + 阴影"伪磨砂实现 |
| Label 不支持渐变文字 | 品牌 "2048" 改用 `Text`（fill 支持渐变色） |
| MiSans 文件下载失败/体积大 | 启动静默回退系统字体；文件为项目资产入库 |
| 布局大改引发控制器绑定回归 | 尽量沿用 fx:id 与事件绑定；分里程碑提交逐项回归 |
| `GAP` 变更破坏既有测试 | 同步更新 `BoardLayoutTest` 断言，保持公式互逆自洽 |
| 粒子/光晕性能与泄漏 | 数量上限 + `onFinished` 清理；GaussianBlur 半径适中 |
| emoji 图标跨平台不一致 | 图标改用 `Icons` 的 SVGPath，统一观感 |
| 主题切换后个别控件未跟色 | 两 CSS 保持同构 lookup 变量；V5 做逐一控件自查清单 |
| 特效与动画锁/计时错乱 | 特效在 `finishMoveAnimation`/`afterMove` 扩展点触发，锁内时序一致 |

---

## 10. 不改动清单（守住范围）

- `org.example.game.*`（GameEngine/Direction/Tile/MoveResult/Undo*/GameStats/ScoreStore）**零改动**。
- 音效资源与 `SoundPlayer` 逻辑不动。
- 测试：`GameEngineTest`、`ScoreStoreTest` 不动；仅 `BoardLayoutTest` 因 GAP/PADDING 变更同步更新。
- 不引入任何第三方依赖；不新增 P2 功能（触摸/打包等）。