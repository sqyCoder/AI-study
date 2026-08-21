# 五子棋 · 视听全面升级规格书（spec v2）

> 版本 v2 · 编写日期 2026-08-21
> 前置文档：spec1.md（功能规格，全部功能保持不回退）
> 目标：从「粗糙小游戏」升级为「精致大作」，视觉、动效、听觉三线全面精修。

> **⚠ 修订记录（v2.1，2026-08-21）**：应用户反馈恢复森林世界观。
> §3.1 背景由「暗调实木棋室」改为「晨雾暖阳森林」（三层远山+雾带+斜射光柱+
> 林线+草地+林间木桌平台承托棋盘+前景框景树影）；§4.7 粒子引擎恢复精修落叶
> （双二次叶形+中脉叶脉，黄/绿/棕）为常驻主飘落物，花粉光尘减量至 12 颗，
> 终局在金色喷泉外追加金瓣撒落；顶底栏与面板底色微调为森林绿棕玻璃拟态。
> 其余章节（材质/动效/音频/字体/几何）全部维持不变。

---

## 0. 已确认的方向决策（问答记录）

| 决策点 | 结论 |
|---|---|
| 视觉风格 | 高级实木棋室风：深色木纹 + 暖金光效 + 玻璃拟态面板 |
| 音效方案 | 升级程序合成：立体声 + 混响 + Karplus-Strong 物理拨弦，不依赖下载素材 |
| 特效强度 | 极致炫酷：粒子、辉光、震屏、大片式胜利演出全都要 |
| 窗口布局 | 保持 800×900 固定窗口不变 |
| 第三方依赖 | 允许少量新增资源文件（开源字体），不引入代码库依赖 |
| BGM | 升级现有双轨（中国风拨弦曲 + 森林自然音），三层混音 |
| 字体 | 内置开源字体：标题书法感字体 + 正文现代黑体 |
| 落子动效 | 全套物理感：下落回弹 + 涟漪 + 微尘粒子 + 微震 + 阴影渐现 |
| 胜利演出 | 全阶段大片式：压暗→逐子点亮→光带→喷泉→光晕→面板弹入 |

## 0.1 硬约束

- Java 17 + JavaFX 21.0.2 + Maven，主类 `org.example.gobang.Main` 不变。
- 窗口固定 800×900、不可缩放、全中文、「制作：林森lsjs」标注保留在每一页。
- **零功能回退**：spec1 §8 验收清单全部继续成立；`mvn test` 保持全绿。
- 所有 UI 仍在 FX 线程；AI 计算仍在 Task 后台线程；生成号防竞态机制原样保留。
- 素材缺失/合成失败时游戏必须照常可玩（沿用 spec1 §9 回退原则）。

---

## 1. 设计系统（新增 `fx/Theme.java`）

现状问题：样式以字符串散落在 Ui/MenuView/GameView/GuessDialog/SettingsPanel 五处，颜色不成体系。改造后所有视觉 token 收敛到 `Theme` 一个类。

### 1.1 色彩 token（常量定义，全部 `Color.web()` 可直接用）

```text
// 背景（暗调棋室）
BG_DEEP      #17100a   最深底色（墙板缝隙/暗角）
BG_MID       #241812   墙板基色
// 木材质
WOOD_LIGHT   #d4a768   棋盘受光面
WOOD_MID     #b98a4e   棋盘中间调
WOOD_DARK    #a1723c   棋盘背光面
FRAME_DARK   #6e4a26   棋盘外框描边
GRID_LINE    #52351a   内部网格线
GRID_BOLD    #4a3018   外圈网格线/星位
// 金色强调
GOLD         #e8c47a   描边、分隔线
GOLD_BRIGHT  #ffd54a   高亮、特效主色
CREAM        #f5e9cf   主文字
// 文字层级
TEXT_MAIN    #efe2c2   正文
TEXT_SUB     #c9b58f   次要信息
TEXT_DIM     #968266   弱化信息/制作标注
// 面板（玻璃拟态）
PANEL_BG     rgba(30,21,13,0.86)
PANEL_STROKE rgba(232,196,122,0.45)   外描边
PANEL_INNER  rgba(255,230,180,0.08)   内侧高光线
// 按钮
BTN_P_GOLD_TOP #f5d78a  BTN_P_GOLD_BOT #d9a94e  BTN_P_TEXT #3c2608   主按钮（开始对局）
BTN_S_TOP  #3a2a1a     BTN_S_BOT #241811     BTN_S_TEXT #f0e3c8          次按钮（深木）
SEL_GREEN_TOP #7fae57  SEL_GREEN_BOT #5d9440  SEL_BORDER #3f6b2a           选中态（沿用绿）
// 特效专用
FX_BLUE      #7ea8ff   败北冷蓝光带
FX_GHOST_A   0.45      悬停 ghost 子透明度
```

### 1.2 字体 token

```java
Theme.FONT_TITLE  // "霞鹜文楷 Lite"（内置），加载失败回退 "Microsoft YaHei"
Theme.FONT_BODY   // "Microsoft YaHei"（系统自带现代黑体）
// 字号阶梯：84 / 36 / 28 / 22 / 18 / 16 / 14 / 12，禁止阶梯外字号
```

### 1.3 按钮工厂（Ui 重构为调用 Theme）

四态完整定义，替换现有三态内联字符串：

| 态 | 视觉 |
|---|---|
| normal | 主按钮=金渐变+深字；次按钮=深木渐变+奶油字；统一圆角 14、1.2px GOLD 描边、padding 10×24 |
| hover | 背景提亮 8%，`translateY(-1)`，DropShadow(10, rgba(255,213,74,0.35)) |
| pressed | 背景加深 10%，`translateY(1)`，阴影收缩为 (4, rgba(0,0,0,0.4)) |
| disabled | opacity 0.45 + 去饱和灰渐变，cursor 默认 |

实现要点：
- hover 抬升用 `TranslateTransition(80ms)` 而非瞬时 setStyle，消除生硬跳变。
- 点击音效仍走 `addEventHandler(ActionEvent.ACTION)`（不覆盖调用方 setOnAction）。
- 新增 `Theme.primaryButton(text,size)` / `Theme.darkButton(text,size)` 两档；原 `styledButton` 保留签名内部转发，减少调用方改动。

### 1.4 面板工厂

`Theme.panel(width)` 返回配置好的 VBox：
- 背景 PANEL_BG、圆角 20、外描边 1px PANEL_STROKE；
- 内衬 4px 处再画一圈 1px PANEL_INNER 高光（用嵌套 StackPane + 内 Border stroke 实现）；
- DropShadow(24, rgba(0,0,0,0.6))；
- 顶部装饰：1px 金渐变分隔线（LinearGradient 左透明→GOLD→右透明）+ 中央小菱形点缀。

---

## 2. 几何重构（棋盘坐标系，唯一改动点，务必一次到位）

### 2.1 新几何常量（GameView 顶部集中定义）

```text
窗口 800×900 不变；顶栏 100 / boardPane 700 / 底栏 100 不变；
shakeGroup 在 boardPane 中 layoutX=50, layoutY=0 不变；Canvas 700×700 不变。

新增/修改：
INSET    = 28            // 棋盘四周木框宽（用于坐标标注）
CELL     = 46            // 格距（原 50）
GRID     = 28            // 网格起点 = (700 − 14×46)/2 = 28
STONE_R  = 20            // 棋子半径（原 21）
映射公式：col = clamp(round((x − GRID)/CELL))，row 同理，clamp [0,14]
星位中心 = GRID + i×CELL
```

### 2.2 必须同步修改的引用点（逐一核对）

- `GameView.rowAt/colAt`（改用 GRID/CELL）
- `renderStones/drawStone` 的坐标计算
- `WinEffect.redraw` 中硬编码的 `50.0` → 改传 CELL/GRID 常量
- `previewAndPlace/drawRing` 的 cx/cy
- 悬停十字参考线、四角括号的几何
验证方式：四角 (0,0)/(0,14)/(14,0)/(14,14)、边缘中点各点击落子一次，吸附目视正确；`BoardSnapshotProbeTest` 回归。

### 2.3 坐标标注

- 列标 A~O（跳过 I，围棋惯例）绘制在上、下木框中央（y = INSET/2 与 700−INSET/2）；行号 15~1 绘制在左、右木框中央。
- 字体 FONT_BODY 13px，颜色 rgba(74,48,16,0.75)，水平居中对齐每个交叉点 x。

---

## 3. 视觉精修明细

### 3.1 背景：森林 → 暗调实木棋室（重写 `ForestBackground.draw()`，类名保留）

绘制层次（自底向上，静态 Canvas 一次绘成）：

1. **基底**：全屏垂直渐变 BG_MID → BG_DEEP。
2. **木墙板**：12 条垂直木板（每条宽 ≈66px），每条内部线性渐变明度 ±5% 制造板材差异；板间 1px 缝 rgba(0,0,0,0.40)；每条板叠 6~8 条微木纹曲线（正弦扰动，alpha 0.04~0.07）。
3. **吊灯暖光**：顶部中央 (400,−60) 大径向光斑，#ffdf9e alpha 0.30 → 0，半径 420，营造「灯下对弈」氛围。
4. **桌面聚光**：棋盘区域中心 (400,430) 径向提亮 rgba(255,220,160,0.10)，半径 380——让棋盘成为绝对视觉焦点。
5. **桌面暗示**：y>720 区域深色渐变 #1c1209 → #120b06，顶缘 1px rgba(255,220,160,0.12) 高光勾出桌沿。
6. **四角 vignette**：四角径向暗化 rgba(0,0,0,0.35)（恢复 spec1 移除的暗角，但只压四角不压中心）。

### 3.2 棋盘精修（重写 `drawBoard()`）

绘制顺序：

1. 底色：对角线性渐变 WOOD_LIGHT → WOOD_MID → WOOD_DARK。
2. **程序木纹**：26 条水平长贝塞尔曲线。每条：基线 y 随机、控制点振幅 2~6px、颜色在基色上叠加深/浅交替（#8a5f33 或 #e0b87a）alpha 0.05~0.12、线宽 1~2.5。种子固定（`new Random(46)`）保证每次启动纹理一致。
3. 细噪点：1200 个 1px 随机点，黑/白交替 alpha 0.03，模拟木材毛孔。
4. 网格：外圈线（i=0 与 i=14）宽 2.5 色 GRID_BOLD；内部线宽 1.1 色 GRID_LINE alpha 0.85；开启抗锯齿。
5. 星位 5 点：r=4 实心 GRID_BOLD，右上叠 1px 白色 alpha 0.25 微高光点。
6. 坐标标注（§2.3）。
7. **外框倒角**：最外 3px FRAME_DARK 描边 → 内退 2px 再画 1px rgba(255,230,180,0.35) 亮线，模拟斜面受光。
8. **投影**：`shakeGroup.setEffect(new DropShadow(25, 8, 0, rgba(0,0,0,0.55)))`——棋盘像实体一样浮在桌面上，震动时投影同步移动更真实。

### 3.3 棋子精修（重写 `drawStone()`，抽出 `paintStone(g,cx,cy,color,alpha)` 供动画复用）

**黑子（云子质感）**：
1. 软阴影：径向渐变椭圆，中心 (cx+2.5, cy+3.5)，半径 R×1.05，黑 alpha 0.32 → 0。
2. 主体径向渐变：焦点在左上 (−R×0.38, −R×0.42)，半径 R×1.5；stops：#787882(0) → #2e2e34(0.42) → #101014(0.75) → #000000(1)。
3. 顶部柔高光：白色椭圆 (cx−R×0.32, cy−R×0.40)，尺寸 R×0.62 × R×0.40，径向白 alpha 0.5 → 0。
4. 底部环境反光：沿下缘画弧线，#c89b5f alpha 0.10（木桌反光，质感关键细节）。
5. 描边 rgba(140,140,150,0.5) 宽 0.8。

**白子（象牙质感）**：
1. 阴影同上，alpha 0.26。
2. 主体：#fffdf6(0) → #f3ecda(0.5) → #d8cfb8(0.82) → #bdb298(1)。
3. 高光更小更亮：白 alpha 0.85。
4. 下缘暖反光 #e8c47a alpha 0.15。
5. 描边 rgba(160,150,130,0.6) 宽 0.8。

**最后一手标记**：金色细环 r=R+3、线宽 2，alpha 以 1.6s 周期在 0.35~0.70 正弦呼吸；由全局 AnimationTimer（§4.7 粒子引擎同一 timer）驱动重绘 fxCanvas 标记层。

### 3.4 顶栏 / 底栏卡片化

- 顶栏：背景 rgba(23,16,10,0.72)，底部 1px 分隔改为金渐变线（左透明→GOLD alpha .5→右透明）；标题「五子棋」改 FONT_TITLE 26px CREAM。
- **回合指示卡**：新小组件——圆角胶囊（PANEL_BG + 1px PANEL_STROKE），内含迷你光泽棋子图标（复用 paintStone 材质函数画到 22px Canvas）+ 「黑方回合」文字；回合切换时胶囊内图标做 200ms 滑动交换动画（旧图标滑出、新图标滑入）。
- AI 思考指示：虚线圆环旋转保留，但改金色 GLOW_BRIGHT 且加 DropShadow 辉光；旁边文字改「AI 落子推演中…」。
- 底栏按钮加图标前缀：「↩ 悔棋」「⟳ 重新开局」（Unicode 符号即可，不引图标库）。
- 手数/模式文字降为 TEXT_SUB 层级，弱化衬托主体。

### 3.5 弹窗系统升级（GameView.basePanel/overlayPanel + GuessDialog + SettingsPanel 共用）

- 遮罩：纯色 0.55 → 径向渐变（中心 rgba(0,0,0,0.50) → 边缘 0.68），视线聚焦面板。
- 面板：统一走 Theme.panel()。
- **入场动画**：overlay fade 0→1（120ms）；panel scale 0.82→1 + fade，插值 easeOutBack（自定义 Interpolator：`c1=1.70158; f(t)=1+(c1+1)*(t-1)^3+c1*(t-1)^2`），320ms，带轻微过冲的 spring 手感。
- **关闭动画**：整体 fade out 150ms 后再 remove 节点；关闭期间遮罩消费点击防连点。
- SettingsPanel 控件精修：Slider 自定义样式（track=凹槽木色 4px 圆角，thumb=14px 金色圆钮带描边）；CheckBox 改为自绘开关胶囊（选中=SEL_GREEN，未选=深木），替代默认勾选框。

---

## 4. 动效系统（极致炫酷）

### 4.1 落子全套物理感（新 `fx/StoneAnimator.java`）

总时长约 360ms，时间轴：

| 时刻 | 事件 |
|---|---|
| t=0 | stonesCanvas 全量重绘但**排除新子**；fxCanvas 开始逐帧绘制新子精灵（复用 paintStone） |
| t=0~70ms | 下落：offsetY 从 −14 → 0（easeOutQuad）；scale 1.18 → 0.94（挤压预备）；alpha 0→1（前 30ms 完成） |
| t=70ms | **接触帧**（所有反馈在此刻同步触发）：① 黑/白落子音效 ② 双涟漪：r 从 R→R+13、线宽 3→1、alpha 0.55→0、时长 320ms，第二圈延迟 70ms ③ SPARK 微尘粒子 8±2 颗（§4.7）④ shakeGroup 微震 amp 1.5px / 110ms ⑤ 该子阴影参数从 (offset 6, alpha 0.45) 过渡到常态 (offset 3, alpha 0.32) |
| t=70~200ms | 回弹：scale 0.94 → 1.05 → 1（easeOutBack） |
| t=200~360ms | 微沉降稳定；结束后 `renderStones()` 并入静态层并清 fxCanvas |

实现约束：
- 动画期间用户输入照常（悬停预览不受影响）；悔棋/重开触发 `StoneAnimator.cancel()` → 立即 renderStones() 还原一致状态。
- AI 落子与人类落子共用此动画（AI 在瞄准环结束后进入）。

### 4.2 悬停预览升级

- ghost 子：以 alpha 0.45 调用 paintStone 画出**真实材质**半透子（替代现在的纯色圆）。
- 十字参考线：从 ghost 圆心向上下左右延伸至木框内缘，金色 alpha 0.14、线宽 1、虚线 [6,6]。
- 吸附点四角括号：距中心 R+6 的四个 L 形角标（边长 8px），金色 alpha 0.8——精确指示落点。

### 4.3 AI 瞄准环（替换现有 previewAndPlace 呼吸圈，500ms）

1. 旋转虚线环（dash [10,7]），r 从 30 收缩到 R+4，同时旋转 180°；AI 执黑用 GOLD_BRIGHT、执白用 #7ee0ff。
2. 收缩到位闪白一帧（整环白色 alpha 1，60ms）+ 锁定音（复用 GUESS_PICK，音量 0.6）。
3. 停顿 80ms 后进入 §4.1 落子动画。

### 4.4 胜利大片式演出（新 `fx/VictorySequence.java`，从 GameView.endGame 抽出编排逻辑）

**胜局（人类胜/PvP 任一方胜），总 ~2.8s：**

| 时刻 | 演出 |
|---|---|
| t=0 | 其余非五连棋子压暗：stonesCanvas 以 globalAlpha 0.38 重绘非胜子（200ms 渐入）；胜子保持全亮 |
| t=90×i (i=0..4) | 第 i 子逐个点亮：白闪圆（r=R，白 alpha 0.85→0，180ms）+ 金环绽放（r R→R+11，alpha 0.9→0，260ms）+ 3 颗上升 SPARK |
| t=600~1100 | 光带扫描：粗线段（宽 6，头部白色亮点 r=4，尾部 30px 渐隐拖影）从首子流向末子；到位后整条常亮，同时 `fxCanvas.setEffect(new Bloom(0.62))` 开启辉光 |
| t=1100 | FOUNTAIN 金色粒子喷泉 70 颗 @五连线中点（§4.7）+ 全屏径向光晕脉冲（以中点为中心 r≈500，金 alpha 0.30→0，750ms）+ rootSway.shake(8,600) + WIN 音效（音画同步在视觉峰值） |
| t=2200 | 胜利面板 spring 弹入（§3.5 入场动画） |

**败局（PvE 人输）**：光带改 FX_BLUE 冷蓝；无喷泉无光晕；根节点 translateY 0→5（400ms easeOut，「画面下沉」感）；LOSE 音效；t=1600 面板弹入。
**平局**：中性灰白 SOFT_FALL 光尘 20 颗缓落；DRAW 音效；t=1200 面板弹入。
清理：`cleanupEffects()` 必须 `fxCanvas.setEffect(null)` 并复位根节点 translate/scale。

### 4.5 猜先仪式动效增强（GuessDialog 内改造，流程逻辑不动）

- 数字揭晓：翻转改为「数字滚动定格」——0.45s 内随机数字快速跳动（每 45ms 一帧）后定格真实值 + scale 弹跳，配合鼓点更有开奖感。
- 结果判定：猜中时头像金环绽放升级为双环扩散 + 6 颗上升 SPARK；结论文字入场加 scale 0.9→1 微弹。
- 面板本身套用 §3.5 弹窗动画。

### 4.6 页面切换过渡（Main 改造）

- 菜单→对局 / 对局→菜单：当前根 fade out 140ms → setRoot → 新根 fade in 200ms + scale 0.985→1。
- 切换瞬间播放 PAGE_SWITCH whoosh 音（音量 0.5）。
- 注意保留现有「共享背景节点 reparent」逻辑（Main.openMenu 中的 add(0, ...)），过渡动画包裹在其外层。

### 4.7 统一粒子引擎（新 `fx/Particles.java`，替代 LeafAnimation）

- 单实例 AnimationTimer（60fps），顶层透明 Canvas 800×900（mouseTransparent），置于 GameView/MenuView root 最上层。
- **对象池**：预分配 Particle[300]，字段 x,y,vx,vy,g,life,maxLife,size,sizeEnd,color,alpha,type；死亡回收复用，杜绝 GC 抖动。dt clamp ≤0.05s。
- 类型参数表：

| 类型 | 数量 | 物理参数 | 用途 |
|---|---|---|---|
| AMBIENT_DUST | 常驻 22 | vx ±6px/s，vy −4~−10 上飘，x 加 sin 摆动（幅 10~18px，周期 3~6s），size 1~2.5px，色 #ffe9c0，alpha 0.06~0.22 正弦脉动；飘出顶部从底部重生 | 环境光尘（替代落叶） |
| SPARK | 8±2/次 | 初速 60~140px/s 径向散射（向上偏置），g=300px/s²，life 0.35~0.5s，size 3→0，色 GOLD_BRIGHT/#fff2cc | 落子接触、点亮子 |
| FOUNTAIN | 70/次 | 初速 180~320px/s，角度 75°~105°（上抛扇形），g=420，life 0.9~1.5s，size 2~4→0，色 GOLD_BRIGHT/#ffffff/#ffe9c0 混合 | 胜利喷泉 |
| SOFT_FALL | 20/次 | vy 15~35 下落，sin 摆动幅 8px，life 至落出屏，灰白 alpha 0.25 | 平局 |

- 失焦暂停：复用 Main 现有 focusedProperty 监听，同时暂停落叶 timer 与本 timer。
- `burstPetals()` 对外接口改名 `burstGold()`（GameView 调用点同步改）；LeafAnimation 类删除，其 rustle 音效改为 AMBIENT_WIND（§5.4）由 Particles 随机触发（8~14s 间隔）。

---

## 5. 音频引擎 2.0（SynthWav 全面重写 + 新 `audio/Dsp.java`）

### 5.1 引擎基础

- **立体声 WAV**：`wavBytesStereo(double[] L, double[] R)`，44100Hz/16bit/双声道；每音效定义 pan 常量（如黑子 −0.08、白子 +0.08，制造方位微差）。
- **Schroeder 混响**（Dsp 实现）：4 组 comb（延迟样本数 {1557,1617,1491,1422}，feedback {0.80,0.79,0.81,0.78}）→ 2 组 allpass（{347,113}，gain 0.7）；输出 = dry + wet×verb(in)；wet 按音效配置（见配方表）。右声道混响延迟 +13 样本去相关，避免双声道完全相同。
- **软限幅**：tanh 饱和 `y = tanh(1.5x)/tanh(1.5)`，杜绝多音叠加削顶爆音。
- 缓存目录版本 `gobang_synth_v3` → **v4**（旧缓存作废强制重生成）。
- **后台预热**：Main.start 中起守护线程调用 `SynthWav.preloadAll()`，首局开始前全部生成完毕，首次播放零卡顿。

### 5.2 Karplus-Strong 物理拨弦（古筝/古琴音色核心）

```text
ksPluck(freq, durSec, damp):
  N = round(RATE / freq)
  buf[N] = 白噪声经 onePoleLP(g=0.6) 预滤（软化击弦瞬态）
  for i in 0..durSec*RATE:
     out[i] = buf[p]
     buf[p] = damp * (buf[p] + buf[(p+1) % N]) * 0.5   // 环形缓冲+低通平均=自然衰减
  古筝: damp≈0.998，再叠一条 detune ±0.15% 的弦各 0.5 混合（合唱效应）
  古琴: damp≈0.992 且预滤 g=0.35（更闷更沉）
```

### 5.3 音效配方表（全部重做；「变体」通过频率 ±4% 随机与 3ms 内时差实现）

| SoundType | 配方要点 | wet | pan |
|---|---|---|---|
| STONE_BLACK ×4 | 三层：① 2ms 噪声 burst 经 bandpass(1900±300Hz,Q=1) ② sine 175Hz(±4%)+二次谐波×0.4，τ=55ms ③ 82Hz thump τ=30ms ×0.7 | 0.16 | −0.08 |
| STONE_WHITE ×4 | bandpass 2900±400；共鸣 235Hz τ=38ms；thump 95Hz τ=22ms ×0.5；加 4.2kHz/1ms tick 提脆度 | 0.14 | +0.08 |
| WIN | 五声上行琶音 D5 F#5 A5 B5 D6（KS damp 0.9975，间隔 110ms）+ 尾和弦 D 大三和弦 KS 长音 1.8s + 高频泛音簇 shimmer ×0.1 | 0.34 | 0 |
| LOSE | 古琴低音 A2 F2 D2（KS damp 0.992），间隔 260ms | 0.30 | 0 |
| DRAW | 中性 KS 双音 A4 E4 轻拨 | 0.20 | 0 |
| CLICK | 木琴双音 sine 880/1320（两组随机取一）τ=28ms + 1ms 噪声瞬态 | 0.06 | 0 |
| HOVER | 3ms highpass 噪声 tick（SoundManager scale 0.12 不变） | 0 | 0 |
| UNDO | 下滑 sweep 520→330Hz τ=90ms + 轻噪声尾 | 0.12 | 0 |
| INVALID | 150/118Hz 双低音（sine+三次谐波），间隔 70ms | 0.10 | 0 |
| GUESS_HOLD | 太鼓：92Hz sine τ=90ms + 6ms 噪声 punch + 46Hz 体感低频 | 0.18 | 0 |
| GUESS_REVEAL | 三连鼓 96/84/72Hz 间隔 90ms + 末鼓长尾 | 0.22 | 0 |
| GUESS_PICK | 木质 clack：bandpass 1400 噪声 3ms + 620Hz τ=25ms | 0.10 | 0 |
| GUESS_RESULT_WIN | KS 上行 G4 A4 D5 | 0.25 | 0 |
| GUESS_RESULT_LOSE | KS 下行两音 B3 G3 | 0.25 | 0 |
| PAGE_SWITCH（新增枚举） | 噪声 sweep lowpass 900→250Hz，180ms，attack 20ms | 0.12 | 0 |
| LEAF_RUSTLE（语义改 AMBIENT_WIND） | brown noise 1.2s lowpass 600Hz，极轻 | 0.30 | ±0.3 随机 |

### 5.4 BGM v2（文件名不变，内容重写）

**bgm_chinese.wav（约 26s 无缝循环）**：
- BPM 64、4/4、16 小节（每拍 937.5ms）、D 宫五声（D E F# A B）。
- 曲式 AABA：A 段主题句 4 小节 ×2 + B 段对比句 4 小节 + A' 再现；音符表以 [音名, 拍位, 时值] 数组硬编码。
- 三层混音：① 古筝主旋律（KS damp 0.998 + detune 双弦）② 分解和弦伴奏（每小节第 1 拍 D3-A3-D4 琶音，音量 0.18）③ 笛声副旋律（正弦 + vibrato 5.5Hz depth 0.004，高八度，第 9 小节起进 3 个长音）。
- 底层铺 brown noise 溪流 vol 0.05；结尾留 1 拍静音 + 首尾 crossfade 100ms 保证循环无缝。

**bgm_forest.wav（约 12s 无缝循环）**：
- brown noise 溪流（leak 系数 0.986）+ 0.18Hz LFO 起伏。
- FM 鸟鸣 3 组：carrier 2400~3100Hz、modulator 38Hz index 3~5、chirp 包络（频率滑移 ±20%），每组 2~3 声——比现有 addSweep 更接近真鸟。
- 风铃 1 处：KS 泛音簇 2093/2349/2637/3136Hz vol 0.06 decay 2s。

### 5.5 SoundManager 小改

- 播放池 8 → 12/类型；新增重载 `play(SoundType t, double volumeScale, double rate)`。
- 变调策略保留（黑白子 rate 0.92~1.08 随机）。
- MusicManager 逻辑不动（双轨混音 + 500ms 淡入淡出已达标）。

---

## 6. 字体系统

- 内置 `resources/fonts/LXGWWenKaiLite-Bold.ttf`（霞鹜文楷 Lite 粗体，约 5MB，OFL 开源许可可随包分发，附 `fonts/OFL-LICENSE.txt`）。仅用于标题类书法字：菜单大标题、胜利标题、猜先标题、顶栏标题。
- 正文维持 Microsoft YaHei（已是现代黑体，避免额外打包 16MB 思源黑体；若后续需要再补）。
- `Theme.loadFonts()`：`Font.loadFont(url, size)` 注册 family；失败时 FONT_TITLE 回退 YaHei 并 log 一行警告，游戏不受影响。
- 应用位置：MenuView 标题 84px、GameView 顶栏 26px、各面板标题 34~36px、胜利面板 40px。

---

## 7. 工程结构变更清单

**新增文件**
```text
src/main/java/org/example/gobang/fx/Theme.java             设计 token + 按钮/面板工厂 + 字体加载
src/main/java/org/example/gobang/fx/Particles.java         统一粒子引擎
src/main/java/org/example/gobang/fx/StoneAnimator.java     落子物理动画编排
src/main/java/org/example/gobang/fx/VictorySequence.java   胜负平终局演出编排
src/main/java/org/example/gobang/audio/Dsp.java            DSP 工具（混响/滤波/KS/限幅/立体声写出）
src/main/resources/fonts/LXGWWenKaiLite-Bold.ttf
src/main/resources/fonts/OFL-LICENSE.txt
```

**修改文件**
```text
Ui.java                样式工厂迁入 Theme，保留旧签名转发
MenuView.java          套 Theme + 标题字体 + 按钮分档 + 入场微动效
GameView.java          几何常量重构 / drawBoard·drawStone 重写 / 悬停升级 /
                       StoneAnimator·VictorySequence·Particles 接入 / 弹窗动画
GuessDialog.java       弹窗动画 + 数字滚动 + 金环升级（流程逻辑不动）
SettingsPanel.java     套 Theme.panel + Slider/开关自绘样式
ForestBackground.java  draw() 重写为棋室背景（类名/接口不变）
LeafAnimation.java     删除（职责并入 Particles）
WinEffect.java         坐标常量化 + Bloom 支持（或职责并入 VictorySequence，二选一以实现简洁为准）
ShakeEffect.java       新增 shakeDamped(amp,dur)：指数衰减阻尼震动供落子微震用
SynthWav.java          立体声 + Dsp 接入 + §5.3/5.4 全部新配方 + preloadAll()
SoundManager.java      池扩容 + play(t,volume,rate) 重载
SoundType.java         新增 PAGE_SWITCH；LEAF_RUSTLE 注释更新为环境风
Main.java               页面切换过渡 + SynthWav 预热线程 + 失焦暂停接入 Particles
pom.xml                 无新增依赖（字体是资源不是依赖）
```

---

## 8. 性能与稳定性防护

| 风险 | 防护 |
|---|---|
| 粒子拖慢帧率 | 池上限 300、单 AnimationTimer、每帧仅清 particles/fx/hover 三层 Canvas |
| Bloom 低端机掉帧 | 简易 fps 采样：<45 连续 60 帧 → fxCanvas.setEffect(null) 自动降级 |
| 合成阻塞启动 | 后台守护线程预热 + v4 缓存目录；合成失败静默跳过该音效 |
| 动画残留 | 所有 Timeline/PauseTransition 注册进 cleanupEffects；悔棋/重开/返回菜单三条路径全覆盖；StoneAnimator.cancel() 保证棋盘状态一致 |
| 几何重构回归 | rowAt/colAt 单点修改；四角+边缘手工点击验证；BoardSnapshotProbeTest 回归 |
| 弹窗连点 | 关闭动画期间遮罩消费点击；单实例守卫保留 |
| 音频爆音 | tanh 软限幅 + 播放池轮换 + 音量钳制（原有机制保留） |

---

## 9. 分阶段实施计划（每阶段独立可验证）

| 阶段 | 内容 | 验收标准 |
|---|---|---|
| A | Theme + 字体加载 + 按钮四态/面板工厂迁移（Ui/MenuView/SettingsPanel 先行） | 全项目无旧内联按钮样式残留；四态手测正确；标题显示文楷字体 |
| B | 背景棋室化 + Particles 环境光尘 | 目视高级感成立；失焦暂停；空场景挂机 10min 无内存增长 |
| C | 几何重构 + 棋盘/棋子精修 + 坐标标注 + 最后一手呼吸标记 | 四角/边缘吸附正确；截图对比质感明显提升；mvn test 绿 |
| D | StoneAnimator + 悬停升级 + AI 瞄准环 | 连续快速落子 20 次无错位无残留；AI 落子走完 瞄准→锁定→落下 全链路 |
| E | VictorySequence（胜/负/平三演出）+ 弹窗 spring 动画 | 三种终局演出各触发一次符合 §4.4 时间轴；弹窗开关 20 次无泄漏 |
| F | Dsp + SynthWav 2.0 + 全音效/BGM 重做 + 预热 | 删缓存冷启动首播不卡；连响 50 次无爆音；音量/静音持久化不变 |
| G | 页面切换过渡 + 全局打磨 + 回归 | spec1 §8 清单逐项过 + 本 spec §10 全过 + mvn clean test 全绿 |

依赖关系：A→B→C 为串行前置；D/E 可并行；F 独立可随时插入；G 收尾。

---

## 10. 最终验收清单

**视觉**
- [ ] 暗调棋室背景：木板/灯光/聚光/暗角层次分明，无卡通感
- [ ] 棋盘木纹自然、倒角外框、悬浮投影、坐标标注清晰
- [ ] 黑白云子质感（双层高光+环境反光+软阴影）达到「想摸一下」程度
- [ ] 面板玻璃拟态 + 金色描边统一；按钮四态手感顺滑
- [ ] 全页字体层级分明，标题书法感成立

**动效**
- [ ] 落子：下落→挤压→回弹→涟漪→火星→微震一气呵成且 ≤360ms 不拖沓
- [ ] 悬停 ghost 子+参考线+四角括号精准
- [ ] AI 瞄准环收缩锁定有「锁定感」
- [ ] 胜利演出按时间轴完整呈现，Bloom 辉光生效；败北冷蓝下沉；平局中性
- [ ] 弹窗 spring 弹入、页面切换淡入淡出

**听觉**
- [ ] 落子声有「实木棋盘敲击」实感，黑白子音色区分明显，4 变体不重复感
- [ ] 胜利琶音+混响尾音有「获奖感」；BGM 古筝拨弦可信、循环无缝
- [ ] 全部音效经过混响但不浑浊；长时间游玩无疲劳刺耳感

**工程**
- [ ] mvn clean test 全绿；spec1 §8 功能验收清单零回退
- [ ] 冷启动（无缓存）到可玩 <5s；对局全程 60fps
- [ ] 「制作：林森lsjs」标注全页保留

---

## 11. 风险与回退

| 风险 | 回退方案 |
|---|---|
| 文楷字体加载失败 | 自动回退微软雅黑，仅损失书法感 |
| Bloom/粒子掉帧 | §8 自动降级开关；粒子数减半常量一键调整 |
| KS/混响合成效果不佳 | 保留 v3 单声道配方作为 per-sound 回退开关 |
| 几何重构引入回归 | 阶段 C 独立提交，出问题单独 revert 不牵连其他阶段 |
