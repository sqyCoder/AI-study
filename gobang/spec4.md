# 五子棋 · Supabase 房间码联机规格书（spec v4）

> 版本 v4 · 编写日期 2026-08-21
> 前置文档：spec3.md（P2P 联机已交付），全部既有能力保持不回退
> 目标：基于 Supabase 的「房间码」联机——建房得 4 位房号，
> 朋友输号即进，**双方零额外软件**；P2P IP 直连模式完整保留并存。

> **⚠ 修订记录（v4.1，2026-08-21）**：实测发现该项目 Realtime WebSocket 后端
> 对任何帧（含 phoenix 心跳）完全静默（Node/Java 双端、新旧两版密钥交叉验证，
> 错误密钥可被网关正确拒绝而合法密钥升级后无响应），判定为 Realtime 服务端异常。
> 传输层由「Realtime Broadcast」切换为 **PostgREST 表轮询**（gobang_msg 表 +
> 350ms 短轮询，仅依赖实测稳定的 REST 通道）。协议复用、UI、房间码语义全部不变；
> 新增一次性建表脚本 sql/supabase-gobang.sql。延迟代价：落子同步最坏 ~700ms，回合制可接受。

> **⚠ 修订记录（v4.2，2026-08-21）**：应用户要求移除 P2P 局域网直连入口（NetLink/
> HostTicket 保留于代码库供回环测试与未来复用，但大厅页不再暴露），大厅简化为
> 单卡「房间码联机」；创建房间支持**自定义房号**（输入框留空则随机生成）；
> SupaRestLink 创建期请求失败改为可见报错（连续 5 次失败即断开提示）。

---

## 0. 已确认的方向决策（问答记录）

| 决策点 | 结论 |
|---|---|
| 基础设施 | **复用贪吃蛇排行榜的同一 Supabase 免费实例**（`fypacgpgvaponiwqvsne`，新加坡区），零成本 |
| 数据隔离 | 只用 Realtime **Broadcast** 通道，不建表、不改 schema、不碰排行榜数据 |
| Java 接入 | JDK17 内置 `java.net.http.WebSocket` + 手写 Phoenix 协议帧 + 极简 JSON 工具，**零第三方依赖** |
| 房号 | 4 位，字母表去除易混字符 0/O/1/I/L（30 字符 → 81 万组合，配合免费层并发限制防猜足够） |
| 已接受风险 | 免费层 7 天无访问自动暂停（控制台一键恢复）；RTT≈230ms（回合制无感）；国内连通无 SLA |

## 0.1 硬约束

- 沿用 spec3 全部硬约束：800×900 固定窗口、FX 线程模型、fail-fast 断开原则、「制作：林森lsjs」标注。
- 零第三方依赖。
- P2P 直连模式与现有 59 项测试零回退。

## 1. 总体架构：传输抽象

```
GameView(ONLINE) ──> Link 接口
                      ├── NetLink   （TCP P2P 直连，现状保留）
                      └── SupaLink  （新增：WSS + Phoenix 协议）
                            └── wss://{project}.supabase.co/realtime/v1/websocket?apikey=...&vsn=1.0.0
                                  └── Realtime Broadcast channel: gobang:{房号}
```

唯一结构性改动：把 `NetLink` 公开方法抽成 `Link` 接口，`GameView` 字段改为接口类型——**对局逻辑零改动**。

### 1.1 工程结构变更清单

**新增**
```text
net/Link.java                 传输接口（start/send/close/isActive/isHost/peerName）
net/supa/JsonKit.java         极简 JSON 编解码（扁平+嵌套，含转义与畸形拒绝）
net/supa/Phoenix.java         Phoenix 帧构造与解析
net/supa/RoomCodes.java       4 位房号生成（去易混字符）
net/supa/SupaConfig.java      resources/supabase.properties 加载
net/supa/SupaLink.java        Supabase 传输实现
src/main/resources/supabase.properties
test: JsonKitTest / PhoenixTest / RoomCodesTest / SupaLinkIT(默认禁用)
```

**修改**
```text
net/NetLink.java    实现 Link 接口（行为不变）；Listener 增加 default onPeerReady()
fx/GameView.java    字段类型 NetLink→Link（一行级）
fx/NetLobbyView.java 第三张卡片「房间码联机」；三卡宽度 340→240
Main.java           openOnlineGame 参数类型 NetLink→Link
README.md           房间码联机指引
```

## 2. Phoenix 协议适配（v4.1 起废弃——Realtime 静默问题，保留原文供追溯）

> **⚠ 本节已由表轮询传输取代**：`net/supa` 现含 `JsonKit`（JSON 编解码，仍用于
> PostgREST 请求/响应）、`RoomCodes`、`SupaConfig`、`SupaRestLink`。
> 消息仍为 Protocol 行文本原样入库（gobang_msg.body），其余语义见修订记录。

### 2.1 帧格式（JSON 数组 `[joinRef, ref, topic, event, payload]`）

| 用途 | 帧 |
|---|---|
| 心跳 | `[null,"<ref>","phoenix","heartbeat",{}]`（每 15s，服务端要求 ≤30s） |
| 加入频道 | `[null,"<ref>","gobang:{CODE}","phx_join",{"config":{"broadcast":{"self":false}}}]` |
| 发消息 | `["<joinRef>","<ref>","gobang:{CODE}","broadcast",{"event":"msg","payload":"<Protocol行文本>"}]` |
| 服务端回执 | `[..,"phx_reply",{"status":"ok|error",...}]` |

- `self:false` 使发送方不收到自己的广播，Listener 天然干净；
- broadcast payload 直接承载现有 Protocol 行文本——**网络层协议 100% 复用**；
- HELLO/PING/PONG/BYE 语义与 NetLink 完全一致：HELLO 握手定 peerName、BYE 触发断线、PING 自动回 PONG。

### 2.2 会话状态机

```
CONNECTING ──ws open──> JOINING ──phx_reply ok──> ONLINE ──异常/BYE/心跳超时──> CLOSED
   │ 重试×3(间隔2s)        │ ack 超时10s计入重试
   └──────────────────────┴──> CLOSED(drop 回调)
```

- 心跳超时判定：任意入站帧刷新 lastRecv，20s 无入站 → drop("心跳超时")；
- 客人加入后 **15s 未收到房主 HELLO** → drop("房间不存在或房主未就绪")；
- 房主无超时（等同 P2P 开房等待）。

### 2.3 onPeerReady 时机

双方各自在「收到对方 HELLO」时触发一次 `Listener.onPeerReady()`（default 空实现）：
大厅页用它导航进对局；对局页忽略。保证进入 GameView 时对方必然在线。

## 3. UI 变更

- `NetLobbyView` 三卡横排（各 240px）：`创建房间(P2P)` / `加入房间(P2P)` / `房间码联机`；
- 房间码卡片：上方 [生成房间码] → 大字房号（点击复制）+ 等待动画 + 取消；下方房号输入框 + [加入]；
- 配置缺失（supabase.properties 不存在）→ 该卡片置灰并提示；
- 对局页 modeLabel：`联机对战 · vs {昵称}`（不变），底栏 moveLabel 前追加房号显示可选。

## 4. 测试计划

| 类 | 覆盖 |
|---|---|
| JsonKitTest | 编解码往返、字符串转义（引号/反斜杠/中文/emoji）、畸形输入抛异常、数字/布尔/null |
| PhoenixTest | 心跳/join/broadcast 帧构造正确；decode 提取 event/topic/status/payload；畸形帧拒绝 |
| RoomCodesTest | 字母表合法、长度 4、无易混字符、1000 次分布合理 |
| SupaLinkIT | 真实连 Supabase 双客户端完整对局协议序列；默认 @Disabled，`-Dsupa.it=true` 启用（避免 CI 依赖外网） |
| 回归 | spec3 的 59 项全绿 |

## 5. 分阶段实施

| 阶段 | 内容 | 验收标准 |
|---|---|---|
| A | spec4 落盘 + JsonKit/Phoenix/RoomCodes + 单测 | mvn test 新增用例全绿 |
| B | SupaConfig + SupaLink + Listener.onPeerReady | 编译通过；IT 手动触发跑通握手 |
| C | Link 接口抽取 + GameView/Main/Lobby 改造 | 房间码双实例完整对局；P2P 回归无损 |
| D | package.ps1 重打包实测 + README + 全量回归 | exe 内房间码对局成功；全测试绿 |

## 6. 风险与回退

| 风险 | 回退方案 |
|---|---|
| 国内连通劣化 | P2P+Radmin VPN 入口保留为回退；endpoint 配置化未来可换自建服务 |
| 项目 7 天暂停 | README 注明恢复步骤；连接失败提示含此可能原因 |
| Phoenix 协议变更 | vsn=1.0.0 固定；解析失败 fail-fast 弹窗提示 |
| 房号碰撞 | join 时 presence/HELLO 缺失即报「房间不存在」，换号重试 |
