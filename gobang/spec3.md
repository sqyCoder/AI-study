# 五子棋 · 远程联机对战与免安装分发规格书（spec v3）

> 版本 v3 · 编写日期 2026-08-21
> 前置文档：spec1.md（功能规格）、spec2.md（视听规格），全部既有能力保持不回退
> 目标：① 新增「联机对战」模式——P2P 直连 + 密码学猜先 + 悔棋/再来一局协商；
> ② Windows 免安装分发包——jpackage 捆绑精简 JRE 打成 zip，对方解压双击即玩。

---

## 0. 已确认的方向决策（问答记录）

| 决策点 | 结论 |
|---|---|
| 联机架构 | **P2P 直连**：一方建房监听端口，另一方输入 IP 直连；不引入任何服务器 |
| 公网可达性 | 局域网开箱即用；公网需房主端口映射，或双方挂 Tailscale/ZeroTier 虚拟局域网（文档指引，不做自动穿透） |
| 猜先方案 | **密码学承诺-揭示（SHA-256）**：房主持子、客人猜单双，先承诺后揭示，防抵赖防作弊 |
| 配套功能 | 悔棋（对方弹窗同意后同步撤 2 子）+ 再来一局（双方同意后交换黑白）；**不做**聊天、断线重连、观战 |
| 传输实现 | 纯 JDK `java.net` Socket + 行式文本协议，**零第三方依赖** |
| 分发形态 | **Windows 免安装包**：jpackage app-image（捆绑 JRE）→ zip；明确放弃手机版（GluonFX 工具链成本过高且 iPhone 无解） |

## 0.1 硬约束

- Java 17 + JavaFX 21.0.2 + Maven，主类 `org.example.gobang.Main` 不变。
- 窗口固定 800×900、不可缩放、全中文、「制作：林森lsjs」标注保留在每一页（含新页面 NetLobbyView 与全部联机弹窗）。
- **零功能回退**：spec1 §8 验收清单全部继续成立；`mvn test` 保持全绿。
- 所有 UI 在 FX 线程；网络读写全在守护线程，消息经 `Platform.runLater` 分发；生成号（generation）防竞态机制原样保留并扩展覆盖网络回调。
- **Fail-fast 原则**：协议外消息、字段非法、时序越序、承诺校验失败——一律断开连接，不留模糊中间态。

---

## 1. 总体架构

```
MenuView ──「联机对战」──> NetLobbyView（创建房间 / 加入房间）
                               │ TCP 连接建立 + HELLO 握手
                               ▼
     GameView(ONLINE) <──NetLink──> 对方 GameView(ONLINE)
```

分层原则：
- `net` 包纯 JDK、不 import 任何 JavaFX 类（线程切换由调用方负责）——可脱离 UI 独立单测；
- `fx` 层只消费 NetLink 回调，不直接触碰 Socket；
- 对局规则校验 100% 复用 `GameSession`：本地与远端各持一份对称实例，同一消息序列必然推导出同一状态（TCP 有序可靠 ⇒ 天然无分叉）。

### 1.1 工程结构变更清单

**新增文件**
```text
src/main/java/org/example/gobang/net/NetLink.java          连接生命周期 + 收发 + 心跳
src/main/java/org/example/gobang/net/Protocol.java         消息编解码 + 字段校验
src/main/java/org/example/gobang/net/GuessCrypto.java      SHA-256 承诺/揭示
src/main/java/org/example/gobang/net/LocalIp.java          本机局域网 IPv4 枚举
src/main/java/org/example/gobang/fx/NetLobbyView.java      房间页（创建/加入）
src/main/java/org/example/gobang/fx/RemoteGuessDialog.java 远程猜先对话框
src/test/java/org/example/gobang/net/ProtocolTest.java
src/test/java/org/example/gobang/net/GuessCryptoTest.java
src/test/java/org/example/gobang/net/LoopbackGameIT.java   本机回环集成测试
package.ps1                                                一键打包脚本（仓库根目录）
```

**修改文件**
```text
GameSession.java   Mode 枚举增加 ONLINE（session 逻辑本身不分支于 mode，仅加项）
GameView.java      ONLINE 模式适配（§5 逐条清单）
MenuView.java      第三个按钮「联机对战」+ 开始回调签名调整
Main.java          openGame 联机重载 + 关窗时 best-effort BYE
pom.xml            maven-dependency-plugin：package 阶段复制运行时依赖到 target/deps
```

---

## 2. 网络层设计（net/）

### 2.1 NetLink 连接封装

```java
public final class NetLink {
    interface Listener {
        void onMessage(String line);          // 已拆行的原始消息，保证 FX 线程回调
        void onDisconnected(String reason);   // FX 线程回调，幂等，仅触发一次
    }
    static void host(int port, Consumer<NetLink> onReady, Consumer<String> onError);
    static void join(String ip, int port, Consumer<NetLink> onReady, Consumer<String> onError);
    void start(Listener listener);   // 握手完成后启动读循环 + 心跳
    boolean send(String line);       // synchronized 写 + flush；IO 异常 → 触发断开流程
    void close(String reason);       // 幂等：best-effort 发 BYE → 关 socket → 注销心跳
    boolean isActive();
    String peerName();               // HELLO 交换的对方昵称
}
```

线程模型：
- `host`：守护线程内 `ServerSocket.accept()` 阻塞等待；`join`：`socket.connect(addr, 8000)` 8 秒连接超时。两者均不占 FX 线程。
- 读线程：`BufferedReader.readLine()` 阻塞循环，每行经 `Platform.runLater` → `listener.onMessage`。
- 心跳：共享 `ScheduledExecutorService`（daemon）。每 5s 发 `PING`；收到任意消息刷新 `lastRecv`（volatile）；巡检任务每 3s 判定 `now − lastRecv > 15s` → `close("心跳超时")`。15s ≈ 3 个发送周期，容忍弱网抖动。
- 写串行化：`send` 方法 `synchronized`，杜绝多回调并发写导致的消息交错。

Socket 参数：`tcpNoDelay=true`（落子延迟敏感）；不设 SO_TIMEOUT（判死交给应用层心跳）；编码固定 UTF-8。

安全护栏：
- 单行长度 > 256 字节 → 协议违规断开（防内存耗尽攻击）；
- 房主 accept 后若已存在活跃 link，新连接立即回 `BYE|reason=房间已满` 并关闭（本作仅支持 1v1）。

### 2.2 Protocol 行式文本协议

格式：`TYPE|k=v|k=v\n`。UTF-8、一行一消息、`\n` 结尾；字段按固定顺序排列（便于抓包 diff 调试）。

| 消息 | 方向 | 字段 | 时机 |
|---|---|---|---|
| HELLO | 双向 | v=1, name=昵称(≤12字符) | 连接建立后第一条；v 不符 → BYE 关闭 |
| GUESS_COMMIT | 房主→客人 | hash=64位hex | 远程猜先对话框打开时立即发送 |
| GUESS_CHOICE | 客人→房主 | odd=0/1 | 收到 COMMIT 后用户点选单/双 |
| GUESS_REVEAL | 房主→客人 | count=1/2, salt=32位hex | 收到 CHOICE 后揭示 |
| MOVE | 双向 | r=0~14, c=0~14, color=1/2 | 本地落子经 session 校验通过后 |
| UNDO_REQ / UNDO_OK / UNDO_DENY | 双向 | 无字段 | 悔棋协商（§6.1） |
| REMATCH_REQ / REMATCH_OK | 双向 | 无字段 | 终局后再来一局协商（§6.2） |
| BYE | 双向 | reason=短文本(≤40字符) | 主动离开/拒绝/违规 |
| PING / PONG | 双向 | 无字段 | 心跳 |

编解码规则（`decode` 返回强类型 Message，非法抛 `ProtocolException`）：
- TYPE 必须在白名单内；字段名、数量、取值域逐项校验：r/c ∈ [0,14]、color ∈ {1,2}、odd ∈ {0,1}、count ∈ {1,2}、hash 为 64 位 hex、salt 为 32 位 hex（16 字节）；
- name 过滤控制字符、超长截断到 12 字符；
- 未知 TYPE 或任何字段非法 → 抛异常 → 调用方发 `BYE|reason=协议违规` 并断开。

会话级时序守卫（GameView 内小状态机，非 Protocol 职责）：每个阶段只允许特定下一条消息（如未收 COMMIT 时 CHOICE 输入被 UI 禁用兜底、越序收到即违规断开）。

### 2.3 GuessCrypto 承诺-揭示

```java
public final class GuessCrypto {
    record Commit(String hashHex, int count, byte[] salt) {}  // count/salt 仅房主内存持有
    static Commit createCommit();                  // SecureRandom：salt=16B，count∈{1,2}
    static String hashOf(byte[] salt, int count);  // SHA-256(hex(salt) + ":" + count) → 64 hex
    record Reveal(int count, byte[] salt) {}
    static boolean verify(Reveal r, String expectedHashHex);   // MessageDigest.isEqual 常数时间比较
}
```

公平性论证（写入 javadoc）：
- 房主**先承诺后见猜**：看到对方猜单/双后再改 count 需伪造哈希原像，SHA-256 下不可行；
- 猜方**仅见哈希**：抗原像特性保证无法从 hash 反推 count；
- 角色固定「房主持子、客人猜」不影响公平——结果随机性由 count 与承诺密码学绑定；
- `verify` 使用 `MessageDigest.isEqual` 做常数时间比较，规避时序侧信道。

### 2.4 LocalIp 本机地址枚举

- 遍历 `NetworkInterfaces.getNetworkInterfaces()` → 过滤 `isUp && !isLoopback && Inet4Address`；
- 排序：私网段优先（10.x / 172.16–31.x / 192.168.x），其余殿后，去重；
- 结果为空 → UI 显示「未检测到局域网地址，请检查网络连接」。

---

## 3. 房间页 NetLobbyView（fx/）

视觉沿用 MenuView 体系：`Theme.applyCss` + 共享 ForestBackground + 金系按钮 + 页脚「制作：林森lsjs」。

```
┌──────────── 800×900 ────────────┐
│                        ⚙ 设置   │
│         联 机 对 战（56px 标题）│
│   ┌───────────┐ ┌───────────┐   │
│   │ 创建房间   │ │ 加入房间   │   │  ← 两张 Theme.panel 卡片并排
│   └───────────┘ └───────────┘   │
│          [← 返回菜单]            │
└─────────────────────────────────┘
```

**创建房间卡片**：
1. 点击「创建房间」→ 展开表单：本机 IP 下拉框（LocalIp 结果）+ 端口输入框（默认 9527，校验 1024–65535）；
2. 「生成房间」→ 监听成功切换等待态：大号展示 `IP:端口`（点击复制到剪贴板 + CLICK 音效反馈）+ 三点呼吸动画「等待对方接入…」+ 取消按钮（关 ServerSocket 回表单）；
3. 对方接入 → 经 Main 进入对局页；
4. 失败（端口占用等）→ 卡片内红字「端口被占用或被防火墙拦截，请更换端口或放行 Java」。

**加入房间卡片**：
1. 表单：IP 输入框（IPv4 正则校验）+ 端口 + 昵称（≤12 字符，默认「棋客」+ 2 位随机数）；
2. 「连接」→ 按钮变「连接中…」（8s 超时）；成功 → 进对局页；
3. 失败区分提示：超时 →「找不到对方，请确认 IP 与端口」；被拒 →「对方拒绝了连接（房间已满）」。

两侧昵称经 HELLO 交换；对局页顶栏显示「联机对战 · vs {对方昵称}」。

---

## 4. 远程猜先 RemoteGuessDialog（fx/）

视觉完全沿用 GuessDialog 体系（Theme.panel + spring 弹入 + GUESS_HOLD/PICK/REVEAL/RESULT_WIN/RESULT_LOSE 音效 + spec2 §4.5 数字滚动定格动画），交互载体改为网络消息：

**房主时序**：
1. 对话框打开即 `createCommit()` → 发 `GUESS_COMMIT` → 显示「已握子，等待对方猜单双…」（颗数仅存内存）；
2. 收 `GUESS_CHOICE` → 鼓点 + 数字滚动定格真实颗数 → 发 `GUESS_REVEAL(count,salt)`；
3. 本地 `verify` 自证 → 结果判定动画。

**客人时序**：
1. 打开显示「等待房主握子…」（COMMIT 未到前「单数」「双数」按钮禁用置灰）；
2. 收 `GUESS_COMMIT` → 启用两按钮 → 点选发 `GUESS_CHOICE` + GUESS_PICK 锁定音；
3. 收 `GUESS_REVEAL` → `verify(hash)`：
   - 通过 → 数字揭晓动画 → 结果判定；
   - **失败 → 红字「承诺校验失败，对方数据不一致」→ BYE 断开**（防作弊红线）。

**结果应用（两侧对称执行）**：
```java
session.applyGuess(false, false, count, guessOdd);  // holderIsAI=false, guesserIsAI=false
// 我执黑 → 顶栏提示「你执黑先行」；否则「对方执黑先行」；写入 myColor
```
对话框存续期间遮罩消费棋盘点击（复用现有弹窗单实例守卫）。

---

## 5. GameView 联机适配（修改点逐条）

构造：新增重载 `GameView(bg, mode, difficulty, settings, onExit, NetLink link)`，原五参构造委托之（link=null 即本地模式）；全部差异以 `mode == ONLINE` 分支收敛。

| # | 位置 | 改动 |
|---|---|---|
| 1 | handleHover / handleClick | 抽 `canLocalPlay()`：PVP→true；PVE→cur≠aiColor()；ONLINE→cur==myColor |
| 2 | handleClick 落子 | session.place 成功后 `link.send(MOVE...)` 再 afterMove（先本地后发送；发送失败走断线流程） |
| 3 | 新增 onRemoteMove | 校验 PLAYING && color==对方色 && ==currentColor && isEmpty → session.place（null 即违规断开）→ afterMove（动效音效全复用） |
| 4 | maybeAiTurn | 已有 mode==PVE 守卫，ONLINE 天然跳过，零改动 |
| 5 | startGuess | ONLINE → RemoteGuessDialog 替代 GuessDialog；完成回调写入 myColor |
| 6 | onUndo | ONLINE → 悔棋协商流程（§6.1），不走直接 undo |
| 7 | onRestart | ONLINE → 底栏隐藏「重新开局」（中途重开可被滥用，终局再来一局已覆盖）；PVE/PVP 不变 |
| 8 | endGame | 胜负演出视角：ONLINE → winnerColor==myColor 决定 WIN/LOSE（替代现 humanWins 的 PvP 分支） |
| 9 | showFinish | ONLINE → 「再来一局」改协商入口（§6.2）；「返回菜单」追加 link.close("返回菜单") |
| 10 | updateStatusUI | modeLabel「联机对战 · vs 名」；悔棋等待期 turnLabel「等待对方回应悔棋…」+ undoBtn 禁用 |
| 11 | cleanup | link != null → close("退出")；onDisconnected 弹窗（§7） |
| 12 | 顶栏 | 连接指示绿点（正常）/红点（断开），样式沿用 turnCapsule 语言 |

---

## 6. 悔棋 / 再来一局 协商流程

### 6.1 悔棋（UNDO_REQ → OK/DENY）

**发起方**：前置 `PLAYING && history 非空 && 无未决请求`；发 UNDO_REQ → undoBtn 文案「等待对方…」+ 禁用；10s PauseTransition 超时自动撤销并提示「对方未响应」。

**接收方**：收 UNDO_REQ → spring 弹窗「对方请求悔棋」[同意][拒绝]；**审批不阻塞对局**（等待期间对方仍可正常落子）；选择即回 UNDO_OK / UNDO_DENY。

**双侧执行（收 OK 后对称触发）**：
```java
session.undo();            // 撤最近 2 子（历史不足撤实际数）
renderStones(false); clearFxCanvas(); updateStatusUI();
SoundManager.play(SoundType.UNDO);
```

一致性论证：TCP 有序 + 双方 board 对称 → 各自独立 undo 必然一致；「撤最新 2 子」语义使「请求等待中对方又落子」的场景自然成立。竞态守卫：同一时刻至多一个未决请求（pendingUndo 标志）；后到的 REQ 回 DENY 语义提示「请求处理中」。

### 6.2 再来一局（REMATCH_REQ/OK）

- 终局面板任一方点「再来一局」→ 发 REMATCH_REQ → 己方按钮变「等待对方…」禁用；
- **任一方收到 REQ 时若自己也在等待 → 直接视为达成一致**（双方同时点击天然收敛）；
- 达成 → 双侧对称执行 `session.nextRound()`（交换黑白、清盘、PLAYING、generation++）→ cleanupEffects + renderStones + updateStatusUI；myColor 取反；
- 一方点「返回菜单」→ `BYE|reason=对方已离开` → 对方弹断线面板。

---

## 7. 断线与生命周期

- 触发源统一收敛：读 IO 异常 / 写 IO 异常 / 心跳 15s 超时 / 收 BYE / 协议违规；
- `onDisconnected(reason)`（FX 线程，AtomicBoolean 保证仅一次）→ 对局页 spring 弹窗「连接已断开：{reason}」[返回菜单]，棋盘输入即刻封锁；
- `Main.onCloseRequest` 追加：对局中 → best-effort 发 BYE 后 close（限时 100ms 不强求）；
- **明确不做**：断线重连、观战、掉线自动判负（对局就地作废，简单可靠）。

---

## 8. 打包分发（jpackage → zip）

### 8.1 pom.xml 变更

`maven-dependency-plugin` 增加 execution：phase=`package`、goal=`copy-dependencies`、`includeScope=runtime`、输出 `${project.build.directory}/deps`。

### 8.2 package.ps1 一键脚本（仓库根目录）

```powershell
# 步骤：
# 1. mvn -q clean package            （-SkipTests 开关可加速）
# 2. 暂存 target/jpkg/ ← 主 jar + target/deps/*
# 3. jpackage：
& jpackage --type app-image --name Gobang --app-version 1.0 `
  --input target/jpkg --main-jar gobang-1.0-SNAPSHOT.jar `
  --main-class org.example.gobang.Main `
  --add-modules java.base,java.sql,java.desktop,javafx.base,javafx.graphics,javafx.controls,javafx.media `
  --java-options "-Dfile.encoding=UTF-8" `
  --dest target/dist
# 4. Compress-Archive target/dist/Gobang → dist/Gobang-win64.zip
# 5. 输出产物路径与体积摘要
```

要点：
- 模块裁剪依据：javafx.graphics 传递依赖 java.desktop；javafx.media 传递依赖 java.sql；最终集合以 `jdeps` 实测复核为准（脚本注释注明复核命令）；
- 产物 `target/dist/Gobang/Gobang.exe` 捆绑精简 JRE，目标：目录 ≤90MB、zip ≤60MB；
- `--name` 取 ASCII（目录/exe 名稳妥），窗口标题仍显示中文「五子棋」；
- app-image **不需要 WiX**；将来升级 msi 安装包仅需 `--type msi`（需先装 WiX Toolset 3.x）；
- 图标暂用默认，后续可 `--icon gobang.ico`（可选打磨项）。

### 8.3 分发与运行要求

- 对方要求：Windows 10/11 x64；解压 → 双击 `Gobang.exe`；无需 Java/IDE/管理员权限；
- SmartScreen 首次运行提示「更多信息 → 仍要运行」（README 附说明）；
- 联机前置：房主防火墙放行 Gobang.exe 或 9527 端口入站；公网联机需房主端口映射，或双方 Tailscale/ZeroTier（README 附简明指引）。

---

## 9. 并发与竞态防护

| 风险 | 防护 |
|---|---|
| 网络回调晚于重开/返回菜单 | 消息回调统一校验 generation 与 link.isActive()，不符丢弃 |
| 多回调并发 send 交错 | NetLink.send synchronized |
| 断线回调重复触发弹窗 | AtomicBoolean 幂等，仅首次生效 |
| 心跳线程泄漏 | 共享 daemon ScheduledExecutorService，close 时注销全部任务 |
| 悔棋请求与落子并发 | 审批不阻塞落子；undo=撤最新 2 子语义天然一致 |
| 双方同时点再来一局 | REQ 互达即达成（幂等），nextRound 对称无歧义 |
| FX 线程阻塞 | accept/connect/read 全在守护线程，回调 Platform.runLater |
| 猜先消息越序 | 会话级小状态机，越序即违规断开 |

## 10. 边界情况清单（实现必须覆盖）

HELLO 版本不符；昵称超长/含控制字符/emoji；单行超长攻击；重复连接（房间已满）；加入时 IP 格式错/端口越界；连接 8s 超时；创建时端口占用；本机无可用地址；COMMIT 前点单双（按钮禁用兜底）；REVEAL 校验失败；悔棋 10s 超时；悔棋请求到达时已终局；双方几乎同时发悔棋请求；悔棋撤至空盘；双方同时点再来一局；对局中断线；猜先中断线；等待接入时房主取消；关窗时 BYE 发送失败；对方在悬停瞬间断线（hoverCanvas 清理）。

## 11. 测试用例清单（JUnit）

**ProtocolTest**
1. 全类型消息 encode→decode 往返相等；
2. 未知 TYPE 抛 ProtocolException；
3. r/c/color/odd/count 越界值抛异常；
4. hash/salt 非 hex 抛异常；
5. 超长行拒绝；
6. name 控制字符过滤与截断。

**GuessCryptoTest**

7. createCommit → verify 通过；
8. count 篡改后 verify 失败；
9. salt 篡改后 verify 失败；
10. 同 salt 同 count 哈希确定、异 salt 哈希不同；
11. 1000 次 createCommit 的 count 分布合理（防退化成常量）。

**LoopbackGameIT**（127.0.0.1 随机端口，心跳参数可注入缩短）
12. host+join 握手 HELLO 互通；
13. 完整猜先协议序列跑通，两侧 applyGuess 结果一致；
14. 交替 MOVE 20 手，两侧 history 完全一致；
15. UNDO_REQ→OK 双侧各撤 2 子一致；
16. REMATCH 双侧 nextRound 后 currentColor==BLACK 且盘面清空；
17. 一端 close，另一端 onDisconnected 恰好触发一次；
18. 静默 20s 内无误判断线。

**回归**：BoardTest / WinCheckerTest / SessionTest / AITest 全绿不回退。

## 12. 分阶段实施计划（每阶段独立可验证）

| 阶段 | 内容 | 验收标准 |
|---|---|---|
| A | net 四件套 + 单测（§11 用例 1~11） | mvn test 绿；协议往返与加密验证全覆盖 |
| B | LoopbackGameIT 回环集成（12~18） | 回环全场景绿 |
| C | NetLobbyView 创建/加入全流程 | 本机双实例可建可连；三种失败路径提示准确 |
| D | RemoteGuessDialog + 猜先接入 | 双实例完整猜先执黑正确；校验失败路径可模拟断开 |
| E | GameView 对局同步（MOVE/悬停/终局视角） | 双实例对打胜/负/平各一局，状态零分叉 |
| F | 悔棋 + 再来一局协商 + 断线弹窗 + 关窗 BYE | §6 全流程手测；三类断线触发源各验一次 |
| G | pom 依赖复制 + package.ps1 + 实机验证 | exe 双击可玩（字体/音效/BGM 正常）；zip ≤60MB |
| H | README 联机指引（防火墙/端口映射/Tailscale）+ 全量回归 | spec1 §8 + 本 spec §13 全过 |

依赖关系：A→B 串行；C/D/E 在 A 后可并行；F 依赖 D/E；G 独立可随时插入；H 收尾。

## 13. 最终验收清单

**联机功能**
- [ ] 创建房间显示真实局域网 IP，对方输 IP:端口 10s 内完成连接
- [ ] 远程猜先全程流畅；承诺校验失败路径正确断开
- [ ] 落子双向同步无丢失无重复，动效音效与本地一致
- [ ] 悔棋协商同意/拒绝/超时三路径正确
- [ ] 再来一局双方同步交换黑白
- [ ] 断线（主动退出/杀进程/心跳超时）均弹窗且可返回菜单
- [ ] 手工 telnet 注入非法消息被断开且不崩溃

**打包分发**
- [ ] 干净机器（无 JDK）解压 zip 双击 Gobang.exe 可玩
- [ ] 设置持久化、字体、合成音、BGM 全部正常
- [ ] zip 体积 ≤60MB

**工程**
- [ ] `mvn clean test` 全绿；spec1 §8 验收清单零回退
- [ ] 「制作：林森lsjs」标注覆盖新页面与全部联机弹窗
- [ ] 全程无异常堆栈输出

## 14. 风险与回退

| 风险 | 回退方案 |
|---|---|
| 公网 NAT 穿透成功率低 | 文档引导 Tailscale/ZeroTier（零代码成本）；不做自动穿透 |
| jpackage 产物过大 | --add-modules 精确裁剪 + jdeps 复核；Compress-Archive 最压缩 |
| 个别机器 javafx.media 缺解码器 | 音效本就 SynthWav 自给 wav；仅 BGM 受影响，try/catch 静默降级（既有原则） |
| 弱网心跳误判 | 阈值 15s（3 周期余量），参数集中定义可调 |
| 协议后续扩展（聊天等） | TYPE 白名单天然可扩展，HELLO.v 字段已留版本协商 |
