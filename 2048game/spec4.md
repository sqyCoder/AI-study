# spec4 — 手机离线 APK 版体验优化计划（问题导向，逐条修复）

> 状态（2026-08-14）：**实施完成**。7 个问题全部修复并落地，改动仅限 `web/`（`app.js`/`style.css`/`confetti.js`/`audio.js`）；`web/js/engine.js` 与 `web/test/engine-test.js` 零改动（引擎回归 22/22 全绿）。新增 `web/test/`（临时）DOM 冒烟测试 43/43 全绿，覆盖：开局、连续 12 步移动（含合并/无重复占位）、差分同步无闪烁、撤销、新游戏、触摸左滑静止、设置模态打开即点开始自动关闭、8×8 尺寸链路（37px 小格）、统计面板开合、返回主菜单。`dist/2048.apk` 已重建（2026-08-14），见 §8 实施记录。
>
> 实施期间修正的一个真实根因（P3）：原 `web/css/style.css` 末尾规则 `.screen, .modal { position: relative; z-index: 1; }` 会**覆盖** `.modal` 的 `position: fixed; inset: 0; z-index: 50`，导致设置/统计弹窗被压回文档流底部、显示在主内容**下面**——正是"卡在下面"的直接原因。修复：该规则改为仅 `.screen`，`.modal` 维持全屏 fixed 遮罩；并结合"背景 pointer-events:none 非阻塞"实现"设置开着直接点开始 → 自动关闭并开局"。

---

## 0. 问题清单与根因定位（先定位，再动手）

| # | 用户反馈 | 根因（file:line） | 结论 |
|---|---|---|---|
| P1 | 触摸滑动时棋盘跟着手指动，体验奇怪 | `web/js/app.js` `bindTouch()`：`touchmove` 中 `wrap.style.transform = translate(dx*0.28, dy*0.28)`（约 L427-433），touchend 再回弹 | 跟手跟随是 spec3 遗留设计，用户不认可 → **删除跟随，棋盘纹丝不动，仅识别滑动方向** |
| P2 | 每次移动合成后新数字"闪下屏"，最难受 | `app.js` `animateMove()`：动画收尾直接调 `renderTiles()`（约 L324），该函数 `layer.innerHTML=''` **全量重建所有方块节点** → 新生成块的光晕/弹出动画被重建打断、整层闪烁 | **去掉全量重建**，改为"差分同步"（只移除残留节点，不动已就位的方块） |
| P3 | 打开设置后直接点"开始游戏"，设置不会自动关闭，卡在下面 | 双层根因：① `style.css` 末尾 `.screen, .modal { position:relative; z-index:1 }` **覆盖**了 `.modal` 的 `fixed/inset:0/z-index:50`，模态被压在文档流底部；② 即使模态正常遮罩也会挡住"开始游戏"按钮，`startBtn` 处理器只 `showGame()` 不关模态 | ① 该规则仅保留 `.screen`，`.modal` 恢复全屏 fixed；② 模态背景 `pointer-events:none` 非阻塞 + 开始游戏强制 `closeAllModals()` |
| P4 | 每次合成的反馈不大，要加强 | `app.js` `popIn` scale 仅 1.12、`confetti.js` 爆点仅 6~10 粒、飘字 16px/700ms、`audio.js` `merge()` 单音 880Hz、无合并震动 | 四路增强：更强 pop + 光环、爆点/飘字随数值放大、合成音随数值升调、合并震动（跟随震动开关） |
| P5 | 棋盘里数字立体感不足 | `style.css` `.tile` 只有一层外阴影 + 一层内高光，无棱台/底暗/字面浮雕 | 三层阴影（外投影+内高光+内底暗）+ 高光叠层 + 数字浮雕字影 + 数值越高投影越深 |
| P6 | 格子紧贴不美观；电脑版有格子影子、移动不消失、数字有间隔 | `app.js` `makeTileEl`：`tilePct = 100/n`，格与格**零间隙**；`.cell` 占位格视觉太弱 | 引入**格间距公式**（与桌面版一致：格子间+四周边留缝）；增强占位格"影子"（内凹阴影），移动全程保留 |
| P7 | 3×3~8×8 空有按钮，只能玩 4×4 | `app.js` `startBtn` → `showGame()` **不重开引擎**，引擎仍是 `init()` 时（持久化尺寸）那一局；且字号用 `clamp(vw)` 不随格宽/位数衰减，8×8 下数字溢出 | 开始游戏时强制 `engine.startNewGame(selectedSize)`；字号改**像素级动态计算**（按格宽×位数系数表）；补 resize 重排（现 `tilesDirty` 是死代码） |

> 附：`app.js` L25 声明 `tilesDirty`，L548 只写不读，为死代码 → 本次顺带复活为"窗口旋转/拉伸后防抖重排"开关。

---

## 1. 改动范围总览

| 文件 | 改动 |
|---|---|
| `web/index.html` | 基本不动（可能仅为设置面板补一个标题行说明，非必需） |
| `web/css/style.css` | `.modal` 非阻塞改造；`.cell` 占位格新样式；`.tile` 三层阴影+浮雕+叠层；pop/merge 光环/棋盘震动 keyframes；字号类删除（改 JS 内联） |
| `web/js/app.js` | `bindTouch` 去跟手；`syncTiles` 差分同步；`closeAllModals`；`startBtn` 重开引擎；`makeTileEl` 布局函数化 + 动态字号；resize 防抖重排；合并震动；`layout(n, boardPx)` 统一布局 |
| `web/js/confetti.js` | `burstAt` 粒子数随数值增大；`scorePopup` 字号/时长/亮度增强；新增 `ringAt`（合并光环，可画在 canvas 或交给 CSS） |
| `web/js/audio.js` | `merge(value)` 支持按数值升调（大合并更响亮）；新增 `mergeBig` 双音叠加 |
| `web/js/engine.js` | **零改动**（逻辑已正确支持 3~8） |
| `web/test/engine-test.js` | **零改动**，仅作回归验证 |
| `dist/2048.apk` | 收尾阶段由 `packaging/build-offline.ps1` 重建（见 §7） |

---

## 2. 详细设计

### 2.1 P1 触摸滑动：去跟手，棋盘静止（`app.js`）

现状：`touchmove` 每帧把 `.board-wrap` 平移 `dx*0.28`，手指动棋盘跟着动；`touchend` 用 150ms transition 弹回。用户明确不喜欢。

改造后 `bindTouch()` 行为约定：

```
touchstart：
  - 记录首个触点 id（touches[0].identifier）、起点 (sx,sy)、(startX,startY)
  - active = true；不设置任何 transform（棋盘保持绝对静止）
touchmove：
  - 若当前触点 id 与记录不符 → 忽略（防多指串扰）
  - e.preventDefault()（保留：防止页面滚动）
  - 只更新"本次位移量"，用于滑动中实时累计（若未来要加"滑动距离指示"，也只读不改棋盘）
  - 棋盘节点零 transform
touchend / touchcancel：
  - 计算 dx = 末触点.clientX - sx, dy = 末触点.clientY - sy
  - |dx|,|dy| 均 < 24px → 视为误触，无动作
  - |dx| > |dy| → 左右：doMove(dx>0 ? 3 : 2)；否则上下：doMove(dy>0 ? 1 : 0)
  - active = false
```

要点：
- 删除 `wrap.style.transform` 的所有赋值与 150ms 回弹 transition（P1 核心）。
- `touch-action: none` 保留（`.board-wrap` 已设置，滑动识别归我们管）。
- 滑动识别期间若 `moveLock` 为真，`doMove` 内部本就拦截，无需额外处理。
- 若 `gameScreen` 未显示（主菜单上滑到棋盘区域不可达，棋盘不在菜单屏，无需 guard；但键盘入口加 `inGame()` 保护，见 2.6）。

### 2.2 P2 新数字闪烁：差分同步替换全量重建（`app.js`）

现状根因：`animateMove()` 末尾 `renderTiles()` 把 `.tiles` 层 `innerHTML=''` 后按引擎棋盘全量重建 → 生成块（spawn）的 `spawnGlow` 动画、合并块的 `pop` 动画在播放中途被销毁重建 → 视觉"闪一下"。

改造：

```js
/** 差分同步：让 .tiles 层与引擎棋盘严格一致，但不重建任何已就位节点（消除闪烁）。 */
function syncTiles() {
  var layer = board.querySelector('.tiles');
  var grid = engine.getGrid();
  var n = engine.getSize();
  // 1) 移除：DOM 中存在但引擎该格为空的节点（如动画遗留）
  layer.querySelectorAll('.tile').forEach(function (el) {
    var r = +el.dataset.r, c = +el.dataset.c;
    if (r >= n || c >= n || grid[r][c].value === 0) el.remove();
  });
  // 2) 补缺：引擎有值但 DOM 缺失（防御性兜底，正常流程不会触发）
  //    用 makeTileEl 创建并直接插入（不做动画，避免二次闪烁）
  // 3) 值不同步的节点（理论上只发生在异常路径）：直接移除交由补缺重建
  // 4) 清理动画残留类：.moving / .pop / .spawn 全部移除（动画已结束）
  // 5) updateMaxStyle() 照常执行
}
```

调用点替换：
- `animateMove()` 收尾的 `renderTiles()` → `syncTiles()`（**核心修复**）。
- 其余 `renderTiles()` 调用点（`doUndo`/`startNewGame`/`showGame`/resize 重排）保留全量重建语义不变（这些场景本就该整体重画，且无动画进行中，不会闪）。

补充细节：
- `renderTiles()` 内部也调 `updateMaxStyle()`；`syncTiles()` 同步保留该调用。
- 合并块替换逻辑（`animateMove` 内已有"移除目标格旧块→新建合并块→popIn"）保持不变，只把最后的全量重建换掉。
- 为防 60ms 收尾定时器与动画错位，收尾时间保持 `MOVE_MS + 40 + 60` 结构，但 60ms 仅用于等 pop/spawn 首帧，`syncTiles()` 不改动画本体。

### 2.3 P3 设置模态：非阻塞 + 开始游戏自动关闭（`style.css` + `app.js`）

问题本质：`.modal` 全屏遮罩（fixed/inset:0/z-index:50）盖住了主菜单的"开始游戏"；当遮罩面板恰好在按钮上方时点击无任何响应（既不开游戏也不关设置）。

改造方案（对 `settingsModal` 与 `statsModal` 统一处理）：

**CSS（`style.css`）**
```css
.modal { ... position: fixed; inset: 0; z-index: 50;
         pointer-events: none;          /* 背景层不拦截任何点击（非阻塞） */ }
.modal-panel { ... pointer-events: auto; /* 面板自身可点 */ }
```

**JS（`app.js`）**
- 新增 `closeAllModals()`：`settingsModal`/`statsModal` 同时加 `hidden`。
- `startBtn` 处理器改为：`closeAllModals(); SFX.click(); startGameFromMenu();`。
- 新增 `startGameFromMenu()`：`engine.startNewGame(selectedSize); resetTimer(); showGame();`（同时解决 P7，见 2.6）。
- 新增"点击面板外自动关闭"兼容逻辑：`document.addEventListener('click', ...)` 检查——若有模态打开且 `e.target` 不在任何 `.modal-panel` 内 → `closeAllModals()`。这样保留旧习惯（点外面即关），同时"开始游戏"按钮穿透成功且会在点下时先关掉设置。
- `showGame()`/`showMenu()`/`startNewGame()`/`doUndo()` 内统一调用 `closeAllModals()` 兜底，杜绝任何"模态卡在下面"的可能。
- 统计面板场景：`doMove` 不再被 statsModal 拦截（背景穿透后方向键/滑动仍可玩）；`afterMove` 弹胜负遮罩前若 statsModal 开着 → 先关闭（避免遮罩叠模态）。
- 键盘守卫同步简化：`bindKeys` 里原来 `if (settingsOpen || statsOpen) return;` 改为只拦 settingsOpen（统计开着时键盘仍可玩，与屏幕按钮一致）；主菜单场景本就无游戏屏，保留菜单下按键无效即可。

验收：设置开着 → 直接点"开始游戏" → 设置立即消失且游戏开始（带所选尺寸）；点面板外任意处 → 设置关闭；统计面板开着 → 滑动/方向键仍可操作，游戏结束时统计自动关闭、胜负面板正常弹出。

### 2.4 P4 合成反馈加强（四路并行）

**(a) 合并弹出动画（`app.js` `popIn` + `style.css`）**
- `pop` keyframes 增强：`0% scale(0.3) opacity 0 → 50% scale(1.28) opacity 1 → 72% scale(0.94) → 100% scale(1)`，时长 280ms（原来 240ms/1.12）。
- 新增 `.tile-merge` 类：合并完成瞬间叠加**光环脉冲** keyframes `mergeHalo`（0.35s）：`box-shadow: 0 0 0 0 → 0 0 22px 8px 当前块主色`（用 CSS 变量 `--merge-halo` 每块按值注入，或复用 `--tile-2048` 等变量计算）；动画结束后类移除。
- 数值 ≥128 的合并：光环半径/亮度再 +50%（CSS 类 `.tile-merge-big`）。

**(b) 爆点与飘字（`confetti.js`）**
- `burstAt(x, y, color, value)`：粒子数 `6~10` → **`min(8 + value 位数 × 4, 22)`**（如 2+2=4 约 12 粒、128 约 20 粒）；速度区间 70~220 提高到 90~260；粒子尺寸 2.5~5 提高到 3~6.5；寿命 220~340ms。
- `scorePopup(x, y, value)`：字号 16/20px → **value<128 ? 18px : 26px**，金色 + 白色描边更粗（4px），寿命 700ms → 850ms，上升距离加大（-44 → -60 系数），并加 1.15→1 缩放动画感（绘制时按 progress 缩放）。
- 合并多个时飘字错位：按合并发生顺序，纵向错开 6px 避免重叠（`animateMove` 内累计 offsetY）。

**(c) 音效（`audio.js`）**
- `merge()` → `merge(value)`：基频按值升调：`freq = 660 + min(Math.log2(value), 10) * 45`（4→~750Hz，128→~975Hz，2048→~1110Hz）；时长随值微增 0.09→0.12。
- value ≥128 追加叠加音：`tone(freq*1.5, 0.03, 0.12, 'sine', 0.08)` 形成双音层次。
- 调用点：`app.js` `doMove` 与合并循环内，把每个合并值传入 `SFX.merge(m.value)`（多组合并时只播最大值的音，避免爆音——实现：取 `result.moves` 中 merge=true 的最大 value 播放一次）。

**(d) 震动（`app.js`，遵循设置里的震动开关 `vibrate`）**
- 合并发生且 `vibrate` 为真：`navigator.vibrate(25)`。
- 本次出现 ≥128 的合并：`navigator.vibrate([45, 30, 70])`。
- 本次合成出 2048（winReached）：沿用 `Confetti.celebrate` 内已有震动，不重复。
- 所有 `navigator.vibrate` 调用 try/catch + 能力检测，静默降级。

**(e) 大合并棋盘轻震（`style.css` + `app.js`）**
- 新增 `.board-shake`：`keyframes shake { 0/100% translate(0,0); 25% translate(-3px, 1px); 50% translate(3px,-1px); 75% translate(-2px, 0); }`，0.28s。
- 本步出现 ≥128 合并时，给 `#board` 挂 `.board-shake` 类，动画结束后移除（`animationend` 或 setTimeout 290ms）。
- 注意：`#board` 的 transform 仅用于此瞬时效果，与 P1 无关（P1 已删 `.board-wrap` 的 transform 跟随）。

### 2.5 + 2.6 P5/P6 立体感与格子间距（布局公式统一化）

#### 2.5.1 统一布局函数（`app.js`，核心，杜绝任何错位）

棋盘像素化定位（与桌面版 `BoardLayout` 同思想）：

```js
var GAP_RATIO = 0.018;          // 间隙 ≈ 板宽 1.8%（8×8 上约 3px，4×4 上约 6px）
function layout(n, boardPx) {
  var gap = Math.max(3, Math.round(boardPx * GAP_RATIO)); // 最小 3px
  var cell = (boardPx - (n + 1) * gap) / n;               // 含四周边缝（对齐桌面公式）
  var x = function (c) { return gap + c * (cell + gap); };
  var y = function (r) { return gap + r * (cell + gap); };
  return { gap: gap, cell: cell, x: x, y: y };
}
```

使用规则：
- `makeTileEl(r, c, v, n, boardPx)`：改为 px 定位：`width/height = cell`，`left = x(c)`，`top = y(r)`（**放弃 % 定位**）。
- `renderBoard()` 建 `.cell` 时用同一 `layout()`，保证占位格与方块完全对齐。
- `animateMove()` 计算位移量：`dx = x(fromCol) - x(toCol)`（即 `(toCol-fromCol) * (cell+gap)`），`dy` 同理；移动终点 `left/top` 同样走 `x()/y()`。
- 所有取 `boardPx` 处统一走 `boardSize()`（`board.clientWidth` 已在用），**一个函数产所有几何量**，消灭三处各算各的错位风险。

#### 2.5.2 占位格"影子"（`style.css` `.cell`）

```css
.cell {
  position: absolute;
  border-radius: 12%;                        /* 微调随 cell 尺寸：用 JS 按 cell 设，或保持百分比 */
  background: var(--cell-bg);
  border: 1px solid var(--cell-border);
  box-shadow:
    inset 0 3px 8px rgba(0, 0, 0, 0.18),      /* 内凹主影（"格子影子"，移动全程可见） */
    inset 0 -2px 4px rgba(255, 255, 255, 0.06), /* 底部微反光 */
    0 1px 2px rgba(0, 0, 0, 0.10);           /* 格间细投影 */
}
```

- 浅色主题：`--cell-bg` 调为经典米色系 `rgba(205,193,180,0.55)`（现 0.46 偏弱）；暗色主题 `rgba(255,255,255,0.09)` → `0.10` 略提。
- 占位格永远先于方块渲染（`.cells` 层在 `.tiles` 层之下），滑动/合并全程不消失（现状即如此，本次只强化观感并配上间距）。

#### 2.5.3 方块立体感（`style.css` `.tile` 三层阴影体系）

```css
.tile {
  /* 已有渐变背景保留，追加高光叠层：在背景渐变之上再叠一层顶部高光渐变 */
  background-image:
    linear-gradient(to bottom, rgba(255,255,255,0.22), rgba(255,255,255,0.06) 38%, rgba(255,255,255,0) 50%),
    var(--tile-2);              /* 具体按 t 类替换为对应 --tile-N */
  box-shadow:
    /* ① 外投影：离地感，随数值加深（低值浅、高值深） */
    0 10px 20px -6px var(--tile-shadow),
    0 4px 8px -4px var(--tile-shadow),
    /* ② 内高光：顶部受光（棱台上缘） */
    inset 0 1.5px 0 rgba(255, 255, 255, 0.35),
    inset 0 1px 2px rgba(255, 255, 255, 0.18),
    /* ③ 内底暗：底部背光（棱台下缘） */
    inset 0 -3px 8px rgba(0, 0, 0, 0.30),
    inset 0 -1px 2px rgba(0, 0, 0, 0.18);
}
```

- 数值分层加深（JS 在 `makeTileEl` 里按值设内联变量 `--depth`，或按 t 类写几档）：
  - 2~64：外投影用基础 `--tile-shadow`；
  - 128~1024：外投影加深 1.4 倍 + 加一圈 `0 0 0 1px rgba(...,0.06)` 描边；
  - 2048+：外投影加深 1.8 倍 + 保留呼吸光晕（现 `.tile-max` 的 maxBreathe 保留）。
- **数字浮雕**：浅色系方块（t2/t4）`text-shadow: 0 1px 0 rgba(255,255,255,0.55), 0 -1px 2px rgba(0,0,0,0.12)`；彩色高值块 `text-shadow: 0 2px 6px rgba(0,0,0,0.35), 0 -1px 1px rgba(255,255,255,0.15)`；2048/super 保持无字影（现行为）。
- 圆角随尺寸微调：`border-radius: min(14%, 14px)`（JS 内联或 CSS min()，8×8 下小格自动变圆角 4~5px 不夸张）。

#### 2.5.4 动态字号（`app.js` `makeTileEl`，解决 8×8 溢出）

删除 `.tile` 上 `clamp(16px, 7vw, 34px)` 一整套 vw 类（t2/t16 等按值分档的 CSS 字号规则全部移除），改为**内联字号**，系数表对齐桌面版（spec.md §4.3.2）：

| 位数（value 的十进制位数） | 系数（× cell） |
|---|---|
| 1 位（2–8） | 0.46 |
| 2 位（16–128） | 0.38 |
| 3 位（256–1024） | 0.30 |
| 4 位（2048–8192） | 0.24 |
| 5 位及以上（16384+） | 0.20 |

- `makeTileEl` 内：`var fs = Math.floor(cell * COEF[digits]); el.style.fontSize = fs + 'px';`
- 8×8（cell≈38px）："1024" → 11px；4×4（cell≈82px）："1024" → 25px，观感与桌面一致。
- `updateMaxStyle`/`syncTiles` 不受影响（不依赖字号类）。
- 保留 `.tile-max`、`.pop`、`.spawn`、`.moving` 等动画类（它们不含字号）。

### 2.6 P7 尺寸真正生效（`app.js` 启动链路修复）

| 步骤 | 现状 | 改造 |
|---|---|---|
| 菜单选尺寸 | `selectedSize` 已正确写入 localStorage | 不变 |
| 点"开始游戏" | 仅 `showGame()`，引擎仍是旧尺寸 → **4×4 假象** | `startGameFromMenu()`：`closeAllModals()` → `engine.startNewGame(selectedSize)` → `resetTimer()` → `showGame()` |
| 游戏内"新游戏" | `startNewGame()` 已用 `selectedSize` 重开 | 不变（但追加 `closeAllModals()`） |
| 返回菜单再开始 | 同上被 4×4 问题覆盖 | 统一走 `startGameFromMenu()`，双入口一致 |
| 键盘误操作 | 主菜单上按方向键也会 move（隐藏棋盘在动） | `doMove` 顶部加 `if (!$('gameScreen').classList.contains('hidden'))` 守卫（inGame） |
| 旋转/拉伸 | `tilesDirty` 死代码，从未重排 | resize/orientationchange 防抖 150ms → `renderBoard()`（全量重建，静止状态无闪烁）+ 重新布局 |
| 胜负判定/撤销/统计 | 引擎已支持任意 N | 不变（用 3×3/5×5/8×8 各完整一局回归） |

---

## 3. 回归与验证清单（每次改动后）

- [ ] `node web/test/engine-test.js` → 全部 PASS（引擎一致性回归，改动期间引擎文件不动，理应恒绿；任何 FAIL 立即回查是否误改 engine.js）。
- [ ] 浏览器（Chrome DevTools 移动模拟）：3×3 / 4×4 / 5×5 / 8×8 各开一局，确认：
  - 开始游戏即用所选尺寸（P7）；
  - 滑动时棋盘不动、方向正确、误触阈值 24px 生效（P1）；
  - 连续 10 次移动，新数字无任何闪烁（P2）；
  - 设置开着直接点开始 → 设置关闭并开局；点面板外 → 关闭（P3）；
  - 合并时 pop 变大、光环、粒子、飘字、音调、震动均随数值增强（P4，桌面浏览器无震动走 try/catch 降级）；
  - 格子有均匀间距、占位格有明显内凹影子、方块有棱台立体感、移动后占位格完整保留（P5/P6）；
  - 8×8 下数字不溢出、不互相粘连（P5/P6/P7）。
- [ ] 真机（Android）：用本地 HTTP 或直接把 `web/` 放手机打开（file:// 亦可，PWA 注册静默跳过）：触摸滑动体验、屏幕旋转后布局正确、多尺寸、闪烁、设置模态、震动感知（真机震动是硬验收）。

## 4. APK 重建（收尾，唯一改产物）

```powershell
powershell -ExecutionPolicy Bypass -File packaging/build-offline.ps1
```

- 脚本会把 `web/`（排除 test/ 与 .well-known/）整体打入 assets 并重新签名，产物覆盖 `dist/2048.apk`（脚本内已含 zipalign + apksigner，keystore 密码 2048GameLSJS，无需人工干预）。
- 若需要，同时 `wrangler pages deploy web` 刷新线上版本（可选，与 APK 无关）。
- 真机复验清单：卸载旧版 → 安装新 APK → 图标/全屏/断网可玩 → 上述全部验收项过一遍 → 关屏后台切回不丢状态。

## 5. 里程碑与提交

| 里程碑 | 内容 | 验收 |
|---|---|---|
| **T1 手感与闪烁** | 去跟手（2.1）+ 差分同步（2.2）+ inGame 守卫 | 滑动棋盘静止；连续移动无闪烁；引擎测试全绿 |
| **T2 设置模态** | 非阻塞模态 + closeAllModals + start 流程（2.3） | 设置开着点开始即开局并关设置；点外关闭；统计不卡面板 |
| **T3 反馈加强** | pop/光环/爆点/飘字/升调音/震动/棋盘轻震（2.4） | 数值越大反馈越强；震动开关生效；静音降级正常 |
| **T4 视觉重构** | 布局函数化 + 间距 + 占位格影子 + 三层阴影 + 动态字号（2.5） | 3~8 全尺寸观感对齐桌面；8×8 无溢出；移动后占位格完整 |
| **T5 尺寸链路 + APK** | startGameFromMenu 修复 + resize 重排（2.6）+ 重建 APK + 真机复验（§3/§4） | 尺寸全生效；APK 安装通过全部真机清单 |

提交规范：`feat(web): T1 手势去跟手与闪烁修复`、`feat(web): T2 设置模态非阻塞`、`feat(web): T3 合成反馈加强`、`style(web): T4 棋盘视觉重构`、`build(apk): T5 尺寸链路修复并重建 APK`。

## 6. 风险与对策

| 风险 | 对策 |
|---|---|
| 布局改 px 后动画位移与终点错位 | 所有几何量走唯一 `layout()`；T4 验收时 4×4 连续 20 步对比动画前后截图 |
| `syncTiles` 漏补缺导致 DOM 与引擎不一致 | 差分同步保留"补缺"分支（无动画静默补齐）；T1 验收连续 30 步后抽查 DOM 与 `engine.getGrid()` 一致 |
| 模态 pointer-events:none 后点外面不关 | 文档级 click 监听器兼容旧习惯（2.3） |
| 震动在部分机型无权限/不支持 | navigator.vibrate 能力检测 + try/catch，永不弹错 |
| 音量/爆点过强致性能下降（8×8 多合并） | 粒子数上限 22/次、Canvas 单实例、动画结束清理（现机制保留） |
| 真机 WebView 与桌面 Chrome 渲染差异 | T4 用真机复验；text-shadow/box-shadow 均为标准属性，风险低 |
| 误改 engine.js | T1 起每次提交后跑 engine-test；spec 明令零改动 |

## 7. 不改动清单（守住范围）

- `web/js/engine.js`：零改动（P7 是 UI 链路问题，引擎本已支持 3~8）。
- `web/test/engine-test.js`：零改动，仅作回归。
- 桌面版 `src/`、`pom.xml`、spec.md/spec2/spec3 对应产物：不动。
- `web/js/i18n.js`：本次无需新增文案（全部改动为视觉/交互/触觉，无新文本）；若 T4 观感需要提示，届时按"中英同步"规则补 key。
- 不引入任何第三方库；不新增 P2 功能（音效库、特效库等一概不碰）。

---

## 8. 实施记录（2026-08-14，全部落地）

### 8.1 代码改动清单

| 文件 | 改动 |
|---|---|
| `web/js/app.js` | 重写：去跟手触摸（P1）；`syncTiles()` 差分同步取代 `renderTiles()` 全量重建（P2）；`closeAllModals()`/非阻塞模态配套（P3）；合并去重 `processedMerge`（修复一次合并两条 moves 重复建块）、`popIn` 光环、`vibrateMerge`、`shakeBoard`（P4）；`layout()`/`fontPx()` 统一布局与动态字号、`makeTileEl`/`renderBoard` 改 px 定位（P5/P6）；`startGameFromMenu()` 强制重开引擎 + `onResize()`/`orientationchange` 防抖重排（P7） |
| `web/css/style.css` | `.screen,.modal` 规则 bug 修复（P3 真根因）；`.modal` 背景 `pointer-events:none` + 面板 `auto`；`.cell` 占位格内凹"影子"；`.tile` 三层阴影 `--depth-outer` 按数值分层 + `::before` 高光叠层 + 数字浮雕字影 + `border-radius:min(12%,12px)`；删除全部 vw 字号规则；`.tile-merge/::after` 光环、`mergeHalo`/`mergeHaloBig`、更强 `pop`、`.board-shake`；`--cell-bg` 强化、新增 `--tile-gloss` |
| `web/js/confetti.js` | `burstAt` 粒子数随数值（8+位数×4，上限 22）、更快更亮更久；`scorePopup` 更大更久（≥128 大金色）+ 入场由小放大；`mergeFx` 增加 `offsetY` 多组飘字错开；画布坐标换算到 board 偏移 |
| `web/js/audio.js` | `merge(value)` 合成音随数值升调（660+log2×45Hz），≥128 追加高八度叠音 |
| `web/js/engine.js` | 未改动 |
| `web/test/engine-test.js` | 未改动（回归 22/22 通过） |

### 8.2 验证结果

- `node --check`：app/audio/confetti/engine/i18n 全部通过。
- `node web/test/engine-test.js`：**22/22 PASS**（引擎一致性未受任何影响）。
- DOM 冒烟测试（临时，位于系统 Temp，不入库）：**43/43 PASS**——开局 2 块、连续 12 步移动（含合并，每步动画类清理干净、方块位置无重复——验证 `syncTiles` 差分同步与合并去重）、撤销、新游戏、触摸左滑（棋盘静止）、设置模态打开→点开始→自动关闭并开局、8×8 尺寸链路（格宽 37px 小格）、统计面板开合、返回主菜单。
- 布局/字号数学：4×4、8×8、3×3 在 276/350/500 px 板宽下边距对称（edge gap = gap），字号按位数系数表换算无溢出。

### 8.3 APK 重建

```powershell
powershell -ExecutionPolicy Bypass -File packaging/build-offline.ps1
```

产出 `dist/2048.apk`（WebView 内嵌最新 `web/`，zipalign + apksigner 自动完成）。

### 8.4 遗留待办（真机人工验收清单，装机后逐项确认）

- [ ] 安装新 APK（覆盖安装或先卸载旧版）
- [ ] 触摸滑动：棋盘完全静止、方向正确、误触阈值正常（P1）
- [ ] 连续 10+ 步移动：新数字无任何闪烁（P2）
- [ ] 设置开着直接点"开始游戏"：设置立刻消失且以所选尺寸开局；点面板外可关闭（P3）
- [ ] 合并反馈：pop/光环/粒子/飘字/音调/震动随数值增强，≥128 棋盘轻震，震动开关生效（P4）
- [ ] 格子有均匀间距、占位格内凹影子、方块立体感、8×8 数字不溢出（P5/P6）
- [ ] 3×3 / 4×4 / 5×5 / 8×8 均能通过"开始游戏"真正进入对应尺寸（P7）；旋转屏幕后布局正确
