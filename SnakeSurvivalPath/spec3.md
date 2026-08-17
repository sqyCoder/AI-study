# 「蛇途求生」V3 打磨与上线开发计划（spec3 唯一依据）

基于 spec1 / spec2 功能基线，针对 spec3 的 15 条改进需求进行打磨与上线。本文档为 V3 开发唯一依据，开发必须严格遵守；所有数值修改必须落 `js/config.js`，禁止散落硬编码。

> 原始需求清单（来自用户 spec3 消息）：
> 1. 顶部提示下移避让摄像头 2. 提示延长到 4s 再慢慢淡出 3. 护盾图标显示在道具栏下方 4. 护盾抵挡后进入 3 秒无敌、蛇穿身不回溯 5. 道具栏倒计时环错位修复 6. 刷新时间改为道具 15s / 事件 30s / 赌博 60s 7. 删除被动道具双倍得分 8. 去除横屏，仅竖屏 9. 游戏规则界面规范化美化 10. 进入动画（黑幕→标题→制作人→主界面）11. 加强所有音效 + 多处震动 + 特效加大（最重要）12. 全面加大音量 13. 开局超速 bug 排查修复 14. 在线排行榜部署 LeanCloud（最重要）15. 每吃一个食物蛇身 +2、分数为之前两倍

---

## 〇、需求确认记录（开发必须遵守）

| # | 决策项 | 结论 |
|---|---|---|
| 1 | 护盾无敌免疫范围 | **免疫一切致命碰撞**：3 秒内撞自己身体、撞临时墙体、陨石砸头全部无视；蛇头可穿过蛇身不回溯 |
| 2 | 护盾图标形式 | 道具栏下方显示 **1 个盾牌图标 + 层数角标**（x1/x2），0 层隐藏；删除 HUD 原有"护盾 N"角标 |
| 3 | 提速曲线 | **保持每 100 分 +5% 不变**（分数 ×2 后约 1500 分即达 10 格/秒满速，接受节奏加快） |
| 4 | 赌博三倍得分 | **保留**（×3、5 个食物、12 秒超时）；倍率只剩 ×1/×3，×2/×6 不再存在 |
| 5 | 刷新调度 | 道具 **15s** / 事件 **30s** / 赌博 **60s**；开局保护保持全部为 0（首道具立即全随机、首事件 30s、首赌博 60s 自然触发） |
| 6 | 竖屏策略 | **删除横屏布局**：横过来也是竖屏样式，不做横屏遮罩/旋转提示；删除全屏按钮等横屏元素 |
| 7 | 进入动画 | 每次页面加载显示一次，约 **2.5s**（黑幕→标题→制作人→主界面），可点击跳过；回主菜单不重播 |
| 8 | 音量方案 | 主音量加大 + **DynamicsCompressor 压缩器防爆音**；iOS 无手势前无声时静默降级 |
| 9 | 新增震动点 | 拾取道具 / 点击道具栏使用 / 护盾抵挡 / 事件预警 / 赌博弹窗 / 开始游戏与按钮点击 / 破纪录（吃食物已有，加大） |
| 10 | 顶部提示下移 | safe-area + 固定下移；**横幅放到 HUD 下方**；事件条在棋盘内下移 |
| 11 | 规则界面 | **分类卡片式 6 块**：核心规则 / 道具系统 / 随机事件 / 博弈抉择 / 排行榜 / 操作与提示 |
| 12 | LeanCloud | 未定，**先规划**：凭证后补，代码已就绪，按第六章步骤执行 |
| 13 | 开局超速 bug | 已定位根因（`renderAcc` 在主菜单累积、开局未清零），**确认修复** |

---

## 一、改动总览（15 项需求 → 模块映射）

| 需求 | 模块 | 主要文件 |
|---|---|---|
| R1 提示下移 | UI/CSS | css/style.css, js/render/renderer.js |
| R2 提示 4s 渐隐 | UI/CSS | js/main.js, css/style.css, js/config.js |
| R3 护盾图标 | UI | index.html, js/ui/main.js, css/style.css |
| R4 护盾→无敌 | 逻辑+渲染+反馈 | js/main.js, js/render/renderer.js, js/audio.js, js/feedback.js, js/config.js |
| R5 倒计时环错位 | UI/CSS | js/ui/main.js, css/style.css |
| R6 刷新 15/30/60 | 配置+文案+测试 | js/config.js, index.html, tests/probability.js, tests/run.js |
| R7 删双倍得分 | 逻辑+渲染+UI+测试 | js/logic/items.js, js/main.js, js/logic/scoring.js, js/ui/main.js, js/render/renderer.js, js/config.js, tests/run.js |
| R8 仅竖屏 | CSS+HTML+控件 | css/style.css, index.html, js/ui/controls.js, js/ui/screens.js |
| R9 规则界面 | HTML/CSS | index.html, css/style.css |
| R10 进入动画 | HTML/CSS+音频 | index.html, css/style.css, js/main.js, js/audio.js, js/feedback.js |
| R11 音效/震动/特效 | 音频+反馈+粒子 | js/audio.js, js/feedback.js, js/render/particles.js, js/main.js, js/config.js |
| R12 音量加大 | 音频 | js/audio.js, js/config.js |
| R13 超速 bug | 主循环 | js/main.js |
| R14 排行榜部署 | 部署（规划） | js/app-config.js（填凭证）, js/leaderboard.js（已就绪，不改） |
| R15 +2 节/×2 分 | 逻辑+测试 | js/config.js, js/logic/scoring.js, js/logic/snake.js, js/main.js, tests/run.js |

---

## 二、逐项需求分析与详细方案

### R1 顶部提示下移（避让摄像头/状态栏/刘海）

**现状（含代码定位）**
- 顶部横幅 `.banner`：`css/style.css:674` `top:12px`，fixed 定位在屏幕最顶端。
- 事件条 `.event-bar`：`css/style.css:94` `top:6px`（在 `.board-wrap` 内绝对定位；竖屏下棋盘 top:60px，事件条屏幕坐标 ≈66px，仍可能被刘海/挖孔遮挡）。
- 倍率徽章：`js/render/renderer.js:426` `drawMultiplier`，`pad = 10 * DPR` 画在棋盘左上角（竖屏时棋盘顶部在 60px 之下，基本安全，微调即可）。

**方案**
1. `css/style.css` `:root` 新增变量 `--top-offset: max(env(safe-area-inset-top), 16px)`。
2. `.banner`：`top:12px` → `top: calc(var(--top-offset) + 56px)`（放到 HUD 顶栏下方，HUD 高 46px，56px 保证不重叠、不遮挡）。
3. `.event-bar`：`top:6px` → `top: calc(var(--top-offset) + 18px)`（棋盘内再下移约 18~24px，避开刘海）。
4. `renderer.js drawMultiplier`：`pad = 10 * DPR` → `pad = 14 * DPR`（微下移，可选）。
5. 核查其余"顶部"元素：反向操控 pill（竖屏 `top:52px`，已在 HUD 下）不动；`#pillReverse` 无遮挡。

**验收**：刘海屏真机（iPhone 刘海模拟 / 安卓挖孔屏）上横幅、事件条完整可见，不被遮挡。

---

### R2 提示显示 4 秒后慢慢淡出

**现状**
- `js/main.js:340-348` `showBanner(text, cls, ms)`：`bannerTimer = setTimeout(..., ms || 2600)`；各调用点显式传 1600/2000/3200ms（main.js:347, 388, 651, 653, 660, 663, 665, 693, 713）。
- `css/style.css:674-696` `.banner` 仅 transform 过渡（`transition: transform 0.35s`），无 opacity 渐隐，消失是瞬间的。

**方案**
1. `js/config.js` 新增 `feedback.bannerMs: 4000`、`feedback.bannerFadeMs: 600`。
2. `main.js showBanner`：默认时长改 `C.feedback.bannerMs`；所有显式 ms 调用点统一去掉（或改为 4000），金色新纪录横幅 3200 也统一 4000。
3. 渐隐实现：显示 → 4000ms 后给 `.banner` 加 `fade` class → 600ms（`bannerFadeMs`）后移除 `show`、清空文本与 class。CSS：`.banner` 增加 `opacity:1; transition: transform .35s cubic-bezier(.2,1.4,.4,1), opacity .6s ease;`；`.banner.fade { opacity: 0; }`。

**验收**：所有"获得/中招/预警/护盾抵挡/破纪录"横幅均显示 4s 后平滑淡出；连发横幅（如快速拾取）自动覆盖不残留。

---

### R3 护盾图标显示在道具栏下方（单盾 + 层数角标）

**现状**
- 护盾层数仅显示在 HUD `pillShield`：`index.html:42`、`js/ui/main.js:108-112 setShield`、`css/style.css:220-221`；竖屏布局下 `.hud-extra` 是 `display:none`（style.css:763），玩家竖屏根本看不到护盾 → 与用户诉求一致。

**方案**
1. `index.html`：`#itemBar` 之后新增护盾栏：
   ```html
   <div class="shield-bar hidden" id="shieldBar">
     <span class="shield-icon" id="shieldIcon"></span>
     <span class="shield-count" id="shieldCount">x1</span>
   </div>
   ```
   盾图标 SVG 用 `UI.SVG.shield('#4d7dff')`（ui/main.js:64-66 已有，JS 注入 innerHTML）。
2. `js/ui/main.js setShield(n)` 重写：更新 shieldBar 显隐（n>0 显示）+ `x{n}` 角标文本 + 层数变化时 pop 动画；**删除**对 `pillShield` 的更新。
3. `css/style.css`：新增 `.shield-bar`（居中、蓝色荧光描边、`pop` 拾取动画）；`.item-bar` 下方间距调整。
4. `index.html:42` 删除 `pillShield` 元素；HUD 顶栏第二行改为显示"蛇长"（配合 R8 的竖屏布局，见 R8 说明）。
5. main.js 中所有 `UI.setShield(effects.shield)` 调用点（main.js:421, 464, 693, 695, 780, 820）不改，内部实现自动生效；拾取护盾时的反馈增强见 R11。

**验收**：0 层隐藏；1 层显示盾 + x1；2 层显示盾 + x2；拾取/消耗护盾有 pop 动画与音效震动。

---

### R4 护盾抵挡 → 3 秒无敌（免疫一切致命碰撞、不回溯、穿身）

**现状（问题根因）**
- `js/main.js:417-429` `tick()` 碰撞段：fatal 时若 `effects.shield > 0` 则 `shield--`、横幅、**`return`（本 tick 不提交移动）** → 蛇停在原地，视觉上"回溯/卡顿"；随后若不立即转向，下个 tick 头仍撞身体 → 护盾已耗，立即死亡。
- `js/main.js:690-700` `meteorImpact()` 砸头分支同样消耗护盾并"挡下"（不产生无敌）。
- 幽灵药剂结束判死逻辑已存在：`js/main.js:524-532`（`isInsideBody`）。

**方案**
1. `js/config.js` 新增 `shieldInvincibleDuration: 3`。
2. `effects` 新增 `invincible: { until }`（main.js:65-77 初始化；startGame 重置段 main.js:185-188 清零；cleanupEffects main.js:514-548 到期清理）。
3. `tick()` 碰撞段重写：
   - `const invincibleOn = effects.invincible && effects.invincible.until > game.gameClock;`
   - `invincibleOn` 时**跳过 self/wall/meteor 全部致命判定**，蛇照常 commit 前进（蛇头穿过身体、穿墙格、穿陨石格）。
   - 否则 fatal 时：`effects.shield > 0` → `shield--`；`effects.invincible = { until: game.gameClock + C.shieldInvincibleDuration }`；**继续 commit 前进（不 return）**；横幅"护盾抵挡！3 秒无敌"；`Feedback.scenarios.invincible()`（强震动 + 金环特效，见 R11）；否则 `die(fatal)`。
4. `meteorImpact()` 砸头分支（main.js:690-700）：先判 invincibleOn → 无视砸头；否则护盾 → 同上置无敌并**不再 return**；蛇身被陨石覆盖仍执行缩短（非致命碰撞，护盾不护身，规则不变）。
5. 无敌结束瞬间：cleanupEffects 中 invincible 到期时复用幽灵判死逻辑——若蛇头卡在蛇身内部 → `die('self')`（与 main.js:524-532 同款）。
6. 渲染：`renderer.js render()` 接收 `invincible` 字段（main.js:955-970 传入 `{ active, remain }`）；无敌期间蛇头/蛇身外圈绘制金色脉动光环 + 棋盘内徽章"无敌 3.0s"倒计时（复用 drawMultiplier 风格的小徽章或独立绘制函数）。
7. 音频：`audio.js` 新增 `invincible` 音效（金属反弹 + 上升三音），`shieldBlock` 升级（见 R11）。

**验收**
- 有盾撞自己 → 护盾消失、蛇**穿身而过不回溯**、3 秒内再撞身体/墙/陨石头均不死、无敌倒计时可见。
- 无敌结束瞬间若头仍在体内 → 判死；无盾时行为与旧版一致。
- 陨石落地瞬间无敌中 → 砸头无视、蛇身仍可被压碎。

---

### R5 道具栏倒计时环错位/覆盖修复

**现状（错位根因分析）**
- `js/ui/main.js:126-163` `updateItemBar`：环 SVG **硬编码 52×52**（`r=23`，stroke 3，`circ=2π×23`）；`.ring-wrap{position:absolute; inset:-3px}`（style.css:353）；`.ring-count` 右下 `bottom:-2px; right:-2px`（style.css:356-371）；`.item-slot-label` 底部 `bottom:-15px` 溢出槽外（style.css:379-385）。
- **竖屏下 `.item-slot` 只有 44×44**（style.css:823-831），但环仍是 52px → 环相对槽位错位；label 与 ring-count 区域重叠；`.item-bar` 高度 62px 内环溢出到边框处。

**方案（重构 slot 结构，尺寸随槽自适应）**
1. 槽内统一两层：`.slot-icon`（居中 26px，z-index:2）+ `.slot-ring`（`position:absolute; inset:0`，SVG `viewBox="0 0 100 100"`、`width/height:100%`，`cx/cy=50`、`r=44`，`circ=2π×44≈276.46` 常量），半径按百分比自适应任意槽尺寸。
2. `.ring-count` 移到**右上角**（`top:-4px; right:-4px`，z-index:3），与 label 不再重叠。
3. `.item-slot-label` 改为槽内底部内嵌（R8 后仅竖屏布局，label 维持隐藏规则，防止与环冲突）。
4. `.item-slot` 竖屏 44px → **48px** 容纳环；`.item-bar` 高度微调（min-height 62px 保持）；`overflow: visible`。
5. 删除旧 `.ring-wrap` 相关 CSS，新增 `.slot-ring` 样式；JS 中移除硬编码 52/23 尺寸。

**验收**：使用减速时钟/幽灵药剂后，环完整贴合槽位四周、无错位、无覆盖、数字角标清晰；320/375/414px 宽度与不同 DPR 下一致。

---

### R6 刷新时间：道具 15s / 事件 30s / 赌博 60s

**现状**
- `js/config.js:35` `itemRefreshInterval: 30`；`js/config.js:54` `eventInterval: 50`；`js/config.js:71` `gambleInterval: 90`；开局保护字段（:37, :55, :72）已为 0，保持不变。

**方案**
1. `js/config.js`：`itemRefreshInterval 30→15`、`eventInterval 50→30`、`gambleInterval 90→60`。
2. 联动文案：
   - `index.html:158` `gamble-sub` "每 90 秒一次" → "每 60 秒一次"。
   - 规则面板文案（与 R9 一并刷新）。
   - `tests/probability.js:131` 硬编码 `if (C.itemRefreshInterval === 30 && C.eventInterval === 50 && C.gambleInterval === 90)` → 改为 15/30/60。
3. 逻辑无需改：`items.tickSchedule`（items.js:44-47）、`events.pickEvent`（events.js:52-54）、`gamble.due` 均读 config；`itemFirstImmediate=true`（开局立即刷 1 个，全随机含毒苹果）保留。
4. 注意：道具 15s + 主动栏 3 格满时拾取失败（items.js:87-91），"道具栏已满！"横幅出现频率变高（可接受，文案已有）。

**验收**：开局 15s 内刷出第 2 个道具、30s 首事件、60s 首赌博；概率回归通过。

---

### R7 删除被动道具「双倍得分」

**现状（double 全部引用点）**
- `js/logic/items.js:21` TYPES、`:24` PASSIVE_TYPES、`:25` GAIN_TYPES、`:104-105` apply 分支。
- `js/main.js:31` ITEM_NAMES；`:65-77` effects 声明；`:160-165` effectiveMultiplier；`:185` startGame 重置；`:482-489` eatFoodAt 计数；`:518` cleanupEffects；`:550-564` updateMultiplierUI。
- `js/ui/main.js:67-70` SVG.double。
- `js/render/renderer.js:29` ITEM_COLORS、`:34` ITEM_LABELS、`:292-299` itemShape double 分支、`:426-480` drawMultiplier（含 ×2/×6）。
- `js/logic/scoring.js:16-20` calcMultiplier(doubleActive, tripleActive)。
- `tests/run.js:78-90`、`:277-279`。

**方案**
1. `items.js`：TYPES/PASSIVE_TYPES/GAIN_TYPES/ALL_TYPES 移除 `double`；`apply` 删 double 分支；头注释更新（增益池 8→7 种：golden/foodRain/magnet/shield/slow/shrink/ghost；负面池仍 poison）。
2. `main.js`：删除 effects.double 全部引用；`effectiveMultiplier` 只反映 triple（m ∈ {1,3}）；`eatFoodAt` 删 double 计数；`updateMultiplierUI` 简化（去掉 ×2/×6 分支，dots 只显示 triple 的 5 个）；`ITEM_NAMES` 去 double；startGame/cleanupEffects 同步。
3. `ui/main.js`：删 SVG.double。
4. `renderer.js`：删 ITEM_COLORS/ITEM_LABELS/itemShape 的 double 分支；`drawMultiplier` 只支持 m=3（label "×3" + 5 个剩余圆点）。
5. `scoring.js`：`calcMultiplier` 简化为仅按 tripleActive 计算（删除 doubleItem 依赖）；`js/config.js` 删除 `doubleItem` 配置（保留 `tripleGamble`、`maxMul` 删除或废弃——倍率不再可能 ×6，建议删除 maxMul 或注释）。
6. `tests/run.js:78-90`：改为纯 triple 用例（m=3 断言 `(10+len)*3`）；`:277-279` double 拾取用例删除。

**验收**：不再刷出双倍得分；吃食物倍率仅 ×1/×3；单测全绿；概率回归增益池 7 种正常。

---

### R8 去除横屏，仅竖屏

**现状**
- `css/style.css:41-53` 默认横屏 grid 布局（board + side 两列、items 行）；`:722-862` `@media (orientation: portrait)` 竖屏布局；`:865-867` `@media (orientation: landscape)`。
- 全屏按钮 `#fsBtn`：`index.html:49`、`js/ui/controls.js:68-86 bindFullscreen`、`js/ui/screens.js:64-66 enterFullscreen 调用`、style.css 相关。
- 规则文案 `index.html:92` "手机请横屏游玩"。

**方案（用户已确认：横过来也没用，还是竖屏）**
1. `css/style.css` 重构：把 `@media (orientation: portrait)` 内全部样式**提升为默认**（去掉 media 包裹），删除横屏 grid 布局（`.app` grid-template-areas、`.side` 横排 flex、`.board-area` grid-area 等）与 `@media (orientation: landscape)` 块；`@keyframes` 与通用样式保留。
2. 删除 `#fsBtn`：HTML 元素、`controls.js bindFullscreen`、`screens.js` 中 `enterFullscreen` 调用（`controls.js enterFullscreen` 一并删）。
3. `index.html:92` 文案改"手机请竖屏游玩"；`manifest.json` 已 `orientation: portrait`（:9）✓ 无需改。
4. HUD 顶栏（竖屏 46px 高，hud-row 3 格）内容调整：得分 / 时长 / **蛇长**（`.hud-extra` 原隐藏两 pill 删除，蛇长并入第三格；护盾由 R3 的 shield-bar 承担）。
5. 横屏设备下：布局保持竖屏样式，棋盘 18×27 居中，两侧留白（`sizeCanvas` main.js:1007-1021 已用 min(availW/COLS, availH/ROWS) 自适应，无需改）。

**验收**：横屏与竖屏手机均显示同一竖屏布局；无 fsBtn；无横屏残留样式；PC 浏览器（宽窗口）棋盘居中可玩（键盘方向键仍支持）。

---

### R9 游戏规则界面（分类卡片式 6 块）

**现状**：`index.html:82-98` `#rulesPanel` 单列表折叠 `<ul>`，文案为旧数值。

**方案**
1. HTML 重构：6 块 `.rule-card`：
   - **核心规则**：撞墙穿梭不死、唯一死亡=撞自己（护盾触发 3 秒无敌）；吃食物蛇身 +2；得分 = 2 × (10 + 蛇身长度) × 倍率；每 100 分提速 5%、最高 10 格/秒；蛇身最短 3 节。
   - **道具系统**：每 15 秒刷 1 个；主动 3 格点击即用（减速时钟 -30%/5s、收缩药剂 -3 节、幽灵药剂 5s 穿身，结束卡体内判死）；被动即时（黄金苹果 +50 分 +3 节、毒苹果 -1 节、食物雨、磁力吸盘、能量护盾）；护盾图标在道具栏下方显示层数。
   - **随机事件**：每 30 秒 1 次，6 种（障碍涌现/食物瞬移/节奏波动/陨石降落/视野收缩/反向操控），均有预警（陨石逐个 1s 预警、障碍残影预告）。
   - **博弈抉择**：每 60 秒 1 次，1 稳健 + 2 激进三选一；激进项 55% 触发负面 / 45% 纯收益；A5 100% 触发陨石。
   - **排行榜**：全服前 10 + 你的名次，任何人破纪录全服实时横幅。
   - **操作与提示**：竖屏畅玩、方向键/键盘、暂停冻结一切、切后台自动暂停。
2. 每卡片：小图标（SVG/符号）+ 标题 + 条目列表；霓虹风格（描边、光晕、hover）。
3. `css/style.css` 新增 `.rule-card`（2 列网格、窄屏 1 列）、`.rule-grid` 容器。
4. 保留"游戏规则"按钮折叠交互（`screens.js:72-75` 不变）。

**验收**：竖屏一屏内可滚动完整查看；6 块分类清晰、无任何旧数值残留。

---

### R10 进入动画（黑幕→「蛇途求生」→「制作：林森lsjs」→主界面）

**现状**：无动画；`js/main.js:1024-1037` `boot()` 直接 `Screens.show('menu')`。

**方案**
1. `index.html`：`<body>` 内新增（置于最前）：
   ```html
   <div class="splash" id="splash">
     <div class="splash-title">蛇途求生</div>
     <div class="splash-sub">制作：林森lsjs</div>
   </div>
   ```
2. `css/style.css`：`.splash`（黑幕 + 粒子装饰光点 CSS 动画）；`.splash-title`（霓虹大字 0.3s 淡入缩放 + glow 扩散 keyframes）；`.splash-sub`（1.2s 延迟淡入）；`.splash.out`（2.2s 后整体 0.3s 淡出，总 2.5s）。
3. `js/audio.js`：新增 `splash` 音效（低频鼓 + 上升滑音三连）。
4. `js/feedback.js`：新增 `scenarios.splash()`（音效 + 30ms 轻震；iOS 无手势前静默降级，`AudioFX.unlock` 已绑定首次 pointerdown：main.js:1026-1028）。
5. `js/main.js` `boot()`：先显示 splash → 2.5s 后（`setTimeout`）淡出 splash 并 `Screens.show('menu')`；splash 上 `pointerdown` → 立即进入主菜单（跳过，同时天然触发 iOS 音频解锁）。
6. 每次页面加载均显示（不做 sessionStorage 缓存）；游戏状态机不受影响（state 仍为 menu，splash 为独立 DOM 层）。

**验收**：打开页面：黑幕→标题→制作人→主菜单，音效与粒子特效流畅；点击跳过有效；iOS 无音效但不报错；PWA 独立窗口同样播放。

---

### R11 加强所有音效 + 多处震动 + 特效加大（最高优先级）

**现状**：音量小、音效单薄（audio.js）；震动只有 8 个场景（feedback.js）；粒子偏弱（particles.js）。

**方案（分三层）**

**A. 音效重做（`js/audio.js`）**
- master gain `0.5 → 0.9`，串联 `DynamicsCompressor`（threshold -12dB / ratio 4 / attack 0.003s / release 0.25s）再连 destination（防爆音，见 R12）。
- 各 sfx 加强（vol 普遍 ×1.6~2、加时长加层）：
  | 音效 | 旧 | 新 |
  |---|---|---|
  | eat | 320→480 双音 vol .2 | 320/480/640 三音上升 vol .35/.3/.25，时长 0.09→0.12 |
  | gain | 523/659/784 vol .22 | 加 1046 高音，vol .35，加长 |
  | bad | 220/160 sawtooth vol .2 | 加方波失真层，vol .35 |
  | meteor | noise .5s vol .5 + 60Hz | noise .7s vol .75、lp 300；滑音 vol .6 |
  | death | 440→70 vol .2 | 滑音 vol .4 + 噪声底 |
  | record | 三音 vol .22 | 四音号角 392/523/659/784 vol .35 |
  | announce | 880/1174 vol .15 | 加 1568，vol .28 |
  | gamble | 160/120 vol .25 | 加 90Hz，vol .4 |
  | multiplier | 660/880 vol .2 | 880/1320 vol .35 |
  | warning | 500 双短音 vol .12 | 500/750 三短音 vol .25 |
  | shieldBlock | 300/450 vol .25 | 300/450/600 三音脆响 vol .4 |
  | click | 600 square vol .12 | vol .2 |
- 新增音效：`itemUse`（点击道具栏使用）、`invincible`（护盾触发金属反弹 + 上升三音）、`splash`（开场，见 R10）、`start`（开始游戏，短上升音）。

**B. 震动增强（`js/feedback.js` + `js/config.js` feedback 表）**
| 场景 | 旧 | 新 |
|---|---|---|
| eat | 15ms | 30ms |
| gainItem | 30ms | 60ms |
| badItem | 100ms | 150ms |
| shieldBlock / invincible | 无 | 120ms + 连震 `[80,40,80]` |
| warning（事件预警） | 无 | 20ms 轻震 |
| gamble | 30ms | 60ms |
| click（按钮） | 无 | 8ms |
| selfRecord | [60,40,60] | [80,40,80,40,80] |
| 新增 itemUse | - | 40ms |
| 新增 start | - | 50ms |
- 新增场景函数：`itemUse`、`invincible`、`start`、`splash`。

**C. 特效加大（`js/render/particles.js` + 调用点 `js/main.js`）**
- eat：burst 12→18；floatText `size 0.9→1.2`；新增轻震屏 `particles.shake(80, 3)`。
- gainItem：burst 14→22 + `goldPulse()`。
- 护盾抵挡/invincible：金色 burst 20 + `flashWhite(0.5)` + `shake(200, 5)`。
- 点击道具栏使用（main.js:263-267）：burst 10 + `shake(120, 4)` + `Feedback.scenarios.itemUse()`。
- 陨石：现有 300ms 震屏 + shards 数量加大；死亡：碎尸粒子 ×2 + 400ms 震屏（保留）。
- 破纪录：金色横幅 + 连续短震（已有，增强震动序列见上表）。

**验收**：手机中等音量即有明显声音；吃食物/拾取/护盾/预警/赌博/按钮均有震动与视觉反馈；特效明显但低端机保持 60fps（粒子数量上限 ~300）。

---

### R12 全面加大音量

**现状**：`js/audio.js:15` `master.gain.value = 0.5`；各 sfx vol 0.08~0.5 偏低。

**方案**
1. master gain `0.5 → 0.9`；新增 `DynamicsCompressor` 防爆音（实现见 R11-A）。
2. `js/config.js` 新增 `audio: { masterVol: 0.9, compThreshold: -12, compRatio: 4 }`（audio.js 读取，测试可注入）。
3. 所有 sfx vol 上调（R11 表格），峰值限幅（`tone()` 内 vol 上限 0.6，防 clip）。

**验收**：手机音量 50% 左右即可清晰听到；100% 音量无明显破音（压缩器生效）；iOS/安卓一致。

---

### R13 开局超速 bug 修复

**根因（已定位）**
- `js/main.js` `loop()`（:837-870）：每帧无条件 `renderAcc += dt`（:842）；**`menu` 状态只 `return`（:867），不清 `renderAcc`**（`else-if` 分支 :861-864 只覆盖 paused/dying/gameover）。
- `startGame()`（:168-210）**未重置 `renderAcc` / `lastFrame`**。
- 后果：主菜单（或结算停留前）累积 N 秒 backlog；开局后 `while (renderAcc >= 1/60 && ticks < maxTicks)`（:854-860）每帧最多追 30 tick（=0.5s 游戏时间），蛇以最高 30× 速度冲刺直到 backlog 耗尽 → 与现象"开始游戏看到蛇超快跑"完全吻合；停留越久越明显。

**方案（双保险）**
1. `startGame()` 末尾：`renderAcc = 0; lastFrame = performance.now();`。
2. `loop()`：dt 累加改为仅 `if (game.state === 'playing') renderAcc += dt;`（menu/paused/dying/gameover 不累计）。
3. `resumeGame()`（:246-252）：同样重置 `lastFrame = performance.now()`，防暂停恢复瞬间 dt 跳变。

**验收**：主菜单停留 10s/30s/60s 后开局、结算页"再来一局"连续 10 次，蛇始终以 4 格/秒起步、无冲刺；暂停恢复无跳速。

---

### R14 在线排行榜部署 LeanCloud（规划，最重要）

**现状（代码已就绪）**
- `js/leaderboard.js`：上传+重试（指数退避 1/2/4s）、离线队列补传、防刷（scoreMax 100 万、单设备日 100 条）、LiveQuery 订阅 + 断线降级 30s 轮询、个人最高分本地+云端双写 —— **全部已实现**。
- `js/app-config.js:20-24`：凭证为空 → 自动本地模式。
- 唯一缺：**真实凭证 + Class + 权限 + LiveQuery 开关 + 云引擎部署**。

**完整步骤（详见第六章）**：注册→建应用→建 Class Score→权限→LiveQuery→填凭证→lean-cli 部署→20 局真机联测→概率回归→分享链接验证。

**验收**：网址可玩、榜单正常、多人可见、双手机实时横幅、断网补传、微信内可打开。

---

### R15 每吃一个食物蛇身 +2、分数为之前两倍

**现状**
- 生长：`js/logic/snake.js:88-98` `commit(head, growNow)` 布尔，一次最多 +1 节；调用点 `js/main.js:437` `snake.commit(prevHead, ateIdx >= 0)`。
- 分数：`js/logic/scoring.js:10-12` `foodScore = (10 + len) × mul`；调用点 `js/main.js:479`。

**方案**
1. `js/config.js` 新增：`growPerFood: 2`、`foodScoreMul: 2`。
2. `scoring.js`：`foodScore` 改为 `(C.scoreBase + snakeLen) * C.foodScoreMul * multiplier`（scoreBase 保持 10，"两倍"语义清晰，禁止写成 `scoreBase=20`，那会使公式变为 (20+len) 而非 2×(10+len)）。
3. `snake.js`：`commit(head, growCount)` —— 布尔参数改数量参数（0 = 不增长；n = 复制尾节 n 次，受棋盘容量防御约束）；`main.js:437` 改为 `snake.commit(prevHead, ateIdx >= 0 ? C.growPerFood : 0)`。
4. 联动（不变项）：磁力吸盘进嘴走 `eatFoodAt`（main.js:604-607）自动 +2；黄金苹果 `growBody(3)` 不变；赌博 grow10/毒苹果 -1/收缩 -3/陨石压碎规则不变；倍率计数按"食物个数"计（triple 5 个）不变。
5. 提速曲线**保持每 100 分 +5%**（用户确认）→ 分数翻倍后约 1500 分即满速 10 格/秒；规则文案如实写明（R9）。
6. `tests/run.js:83, 88-90` 分数断言 ×2；新增 commit(head, 2) 后 `getLength()` +2 用例。

**验收**：吃 1 个食物蛇身 +2、得分 = 2 × (10 + 原蛇身长度) × 倍率；单测全绿。

---

## 三、配置文件变更表（js/config.js）

| 字段 | 现值 | 新值 | 说明 |
|---|---|---|---|
| `itemRefreshInterval` | 30 | **15** | R6 |
| `eventInterval` | 50 | **30** | R6 |
| `gambleInterval` | 90 | **60** | R6 |
| `eventFirstDelay` / `gambleFirstDelay` / `firstBenignWindow` | 0 | 0（不动） | 开局保护保持移除 |
| `doubleItem` | {mul:2,count:3,timeout:10} | **删除** | R7 |
| `maxMul` | 6 | **删除/废弃** | 倍率只剩 ×3（R7） |
| `shieldInvincibleDuration` | - | **新增 3** | R4 |
| `growPerFood` | - | **新增 2** | R15 |
| `foodScoreMul` | - | **新增 2** | R15 |
| `feedback.bannerMs` / `bannerFadeMs` | - | **新增 4000 / 600** | R2 |
| `feedback.eatShakeMs` | 15 | **30** | R11 |
| `feedback.gainShakeMs` | 30 | **60** | R11 |
| `feedback.badShakeMs` | 100 | **150** | R11 |
| `feedback.gambleShakeMs` | 30 | **60** | R11 |
| `audio` | - | **新增 { masterVol:0.9, compThreshold:-12, compRatio:4 }** | R12 |

---

## 四、文件改动清单（逐文件）

| 文件 | 改动点 |
|---|---|
| `index.html` | splash 层、shield-bar、rulesPanel 6 卡片重构、gamble-sub 文案 60s、删 #fsBtn、删 #pillShield、HUD 顶栏加蛇长、规则文案全量刷新、全部资源 `?v=7→8` |
| `css/style.css` | 竖屏布局提升为默认（删横屏 grid 与 landscape media）、banner 下移+渐隐、event-bar 下移、shield-bar、rule-card、splash、slot 环重构、删 fsBtn/横屏残留、`--top-offset` 变量 |
| `js/config.js` | 见第三章变更表 |
| `js/logic/scoring.js` | foodScore ×foodScoreMul；calcMultiplier 去 double |
| `js/logic/snake.js` | `commit(head, growCount)` 数量参数 |
| `js/logic/items.js` | 删 double（TYPES/PASSIVE/GAIN/ALL/apply）、头注释 |
| `js/logic/events.js` | 无（间隔读 config 自动生效） |
| `js/logic/gamble.js` | 无（triple 保留） |
| `js/main.js` | R4 无敌逻辑（effects.invincible、tick 重写、meteorImpact 改、无敌结束判死、renderer 传参）；R7 删 double；R10 splash 流程；R13 renderAcc 修复；R15 commit 调用；showBanner 4s+渐隐；反馈/粒子增强调用；ITEM_NAMES 去 double |
| `js/audio.js` | master 0.9 + Compressor、sfx 全部加强、新增 itemUse/invincible/splash/start |
| `js/feedback.js` | 震动表加强、新增 itemUse/invincible/start/splash 场景 |
| `js/render/particles.js` | eat/gain 粒子加大、floatText 加大、shake 幅度参数化 |
| `js/render/renderer.js` | invincible 光环+倒计时徽章、drawMultiplier 仅 ×3、pad 下移、删 double 图标 |
| `js/ui/main.js` | setShield→shield-bar、slot 环自适应重构、删 SVG.double |
| `js/ui/controls.js` | 删 bindFullscreen/enterFullscreen |
| `js/ui/screens.js` | 删 enterFullscreen 调用 |
| `js/leaderboard.js` | 不改（部署用） |
| `js/app-config.js` | 部署时填凭证（规划中，见第六章） |
| `sw.js` | **CACHE v6→v7、ASSETS 全部 `?v=6→8`**（防 PWA 旧缓存，必须与 HTML 同步） |
| `tests/run.js` | 分数 ×2 断言、+2 节用例、删 double 用例、间隔断言 |
| `tests/probability.js` | 间隔 15/30/60 断言（:131）、增益池 7 种 |

---

## 五、测试与验收

### 5.1 单元测试更新（`node tests/run.js`）
- 分数：`foodScore(3,1)=26`、`foodScore(20,1)=60`、`foodScore(3,3)=78`（2×(10+len)×mul）。
- 生长：`commit(head, 2)` 后长度 +2；growBody 容量防御保留。
- 倍率：calcMultiplier 仅 triple；无 double 分支。
- 道具池：增益 7 / 负面 1；55/45；连续负面不出现；double 不再出现。
- 间隔：道具 15 / 事件 30 / 赌博 60（若 run.js 有断言）。
- 护盾/无敌（逻辑层可测部分）：fatal 时 shield>0 → 消耗并置 invincible；invincible 期间碰撞判定全部跳过；到期清理。
- 其余（速度曲线/穿梭/队列/陨石/blockade/ghost）回归不动。

### 5.2 概率回归（`node tests/probability.js`）
- 间隔断言更新 15/30/60（:131 硬编码）；道具池 7+1；10 万次模拟 55/45、事件 75/25、赌博 55/45、各保底统计符合配置。

### 5.3 真机验收清单（含 R14 的 20 局联测）
设备：安卓 Chrome / iOS Safari / 微信内置浏览器（至少 2 台手机）。
1. 刘海/挖孔屏：横幅（HUD 下方）、事件条不被遮挡（R1）。
2. 所有提示 4s 淡出（R2）；护盾图标层数正确（R3）。
3. 护盾撞自己穿身+3s 无敌+倒计时可见；无敌结束卡体内判死（R4）。
4. 道具栏环贴合无错位；竖屏 320/375/414px 各测一次（R5）。
5. 道具 15s / 事件 30s / 赌博 60s 节奏（R6）；不再出现双倍得分（R7）。
6. 横竖屏都显示竖屏布局；无全屏按钮（R8）。
7. 规则 6 卡片完整、文案无旧数值（R9）。
8. 进入动画 2.5s + 音效 + 可跳过（R10）。
9. 音量 50% 清晰、100% 不破音；震动覆盖：吃食/拾取/点道具/护盾/预警/赌博/按钮/破纪录（R11/R12）。
10. 主菜单停留 30s 后开局无超速；暂停恢复无跳速（R13）。
11. 20 局联测（R14）：每局结束上传成功、榜单正常、A 破纪录 B 3~5s 内见横幅、断网一局自动补传、微信内打开分享链接可玩可上榜、双手机各自名次正确。

### 5.4 性能
- 低端安卓：开启所有特效后保持 60fps；粒子上限保护（>300 时丢弃新粒子）。

---

## 六、LeanCloud 在线排行榜部署详细步骤（R14）

> 代码已就绪（leaderboard.js），本阶段只做：注册→配置→部署→验证。凭证由用户提供后执行。

1. **注册**：LeanCloud 官网注册（国内版），创建新应用（开发版，免费）。
2. **建 Class**：控制台 → 数据存储 → 创建 Class `Score`（名称必须一致）：
   | 字段 | 类型 |
   |---|---|
   | nickname | String |
   | score | Number |
   | duration | Number |
   | deviceId | String |
   | createdAt | Date（系统自动） |
3. **权限**：Class 权限设为**公开读 + 开放写**（无登录体系，配合客户端防刷兜底：`scoreMax=1000000`、单设备日 100 条、昵称过滤 —— leaderboard.js 已实现）。
4. **LiveQuery**：控制台开启 LiveQuery（免费开发版额度：100 订阅/天 + 5000 消息/天）。
5. **填凭证**：`js/app-config.js:20-24` 填入 appId / appKey / serverURL（国内版数据存储 URL 形如 `https://你的ID.api.lncldglobal.com`）；**提交前确认不把真实密钥提交到公开仓库**（生产建议经云引擎环境变量注入，见 app-config.js 注释）。
6. **部署（lean-cli）**：
   - `npm i -g leancloud-cli`（或官方 lean-cli）；
   - 项目根 `lean login` → `lean init`（选择"静态网站/H5"类型）→ `lean deploy`；
   - 获得云引擎体验实例默认子域名 URL，即分享链接。
7. **验证清单**（对应验收）：
   - 新设备打开 URL 可玩；
   - 单局结束榜单出现记录；
   - 双手机同时在线：LiveQuery 新纪录横幅 3~5s 内互见；
   - 断网上传 → 本地离线队列 → 联网后自动补传；
   - 微信内打开分享链接可玩、可上榜；
   - 20 局真机联测无上传失败；
   - `node tests/probability.js` 概率回归通过。
8. **额度监控**：免费版 3 万 API/天 + 100 订阅/天，个人规模够用；超额后再考虑商用版。

---

## 七、里程碑（每阶段可独立验收）

| 阶段 | 内容 | 验收 |
|---|---|---|
| M1 数值与核心规则 | R6（15/30/60）、R7（删双倍）、R15（+2 节/×2 分）、R13（超速修复） | 单测全绿、概率回归过、真机开局无超速 |
| M2 护盾改造 | R3（护盾图标）、R4（3s 无敌穿身） | 穿身无敌 3s 生效、图标层数正确、结束判死 |
| M3 竖屏与 UI | R1/R2（提示下移与 4s 渐隐）、R5（环错位）、R8（仅竖屏）、R9（规则卡片） | 刘海屏不遮挡、环无错位、横屏也竖屏布局 |
| M4 视听体验 | R10（进入动画）、R11（音效/震动/特效）、R12（音量） | 2.5s 开场、中等音量清晰、震动全覆盖、不破音 |
| M5 排行榜上线 | R14 全部步骤 | 网址可玩、榜单正常、多人可见、20 局联测通过 |

建议执行顺序：M1 → M2 → M3 → M4 → M5（M5 需用户提供 LeanCloud 凭证，可并行准备）。

---

## 八、风险与注意事项

1. **PWA 缓存版本**（高风险）：`sw.js` CACHE 名与所有资源 `?v=` 必须**同步 +1**（v7→v8，当前 index.html 为 v7、sw.js 为 v6），否则玩家拿到旧版代码，改动全部"无效"。每完成一批改动后最后统一升版。
2. **iOS 音频解锁**：首次触摸前 Web Audio 静默（splash/开场音在 iOS 可能无声）——已按静默降级设计，不得报错、不得白屏；splash 点击跳过机制天然提供解锁手势。
3. **音量加大 → 爆音**：必须带 DynamicsCompressor；真机 100% 音量验收。
4. **分数 ×2 后排行榜数值变大**：防刷阈值 1,000,000 仍充裕，无需改；历史榜单分数不迁移。
5. **15s 道具 + 3 槽满**：拾取失败提示频率变高（已有"道具栏已满"横幅，可接受）。
6. **删横屏后 PC 浏览器**：宽窗口棋盘居中、键盘可玩；如后续要回退需 git 保留横屏 CSS。
7. **LeanCloud 免费额度**：3 万 API/天、LiveQuery 100 订阅/天；活跃后监控用量。
8. **真机联测设备**：20 局联测与 LiveQuery 推送验证需要至少 2 台手机 + 1 台电脑（概率回归）。

---

## 附：spec3 需求 → 章节映射

| 用户条目 | 章节 |
|---|---|
| 1 提示下移 | R1 |
| 2 提示 4s 淡出 | R2 |
| 3 护盾图标 | R3 |
| 4 护盾 3s 无敌 | R4 |
| 5 道具栏环错位 | R5 |
| 6 刷新 15/30/60 | R6 |
| 7 删双倍得分 | R7 |
| 8 仅竖屏 | R8 |
| 9 规则界面 | R9 |
| 10 进入动画 | R10 |
| 11 音效/震动/特效 | R11 |
| 12 音量加大 | R12 |
| 13 超速 bug | R13 |
| 14 排行榜部署 | R14 / 第六章 |
| 15 +2 节/×2 分 | R15 |
