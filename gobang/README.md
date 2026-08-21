# 五子棋（Gobang）

Java 17 + JavaFX 21 桌面五子棋：人机对战（三档 AI）/ 双人同屏 / **远程联机**，
含猜先仪式、落子物理动效、程序合成音效与 BGM。

## 构建与运行

```bash
mvn javafx:run        # IDE 或命令行直接运行
mvn test              # 全量测试
```

## 打包分发（发给朋友）

```powershell
powershell -ExecutionPolicy Bypass -File package.ps1            # 含测试
powershell -ExecutionPolicy Bypass -File package.ps1 -SkipTests # 跳过测试
```

产物 `dist/Gobang-win64.zip`：**免安装、捆绑 JRE**，对方无需安装 Java/IDE。
Windows 首次运行若弹出 SmartScreen，点「更多信息 → 仍要运行」即可。

## 远程联机指南

入口：主菜单 →「联机对战」。三种方式任选：

### 方式一：房间码联机（推荐，异地首选，零额外软件）

1. 一方点 **「生成房间码」** → 得到 4 位房号（如 `K7XQ`）；
2. 把房号发给朋友（微信即可）；
3. 朋友在「按房码加入」输入房号 → 连接 → 自动猜先开局。

> 走 Supabase 中转，任何网络环境可用。注意：免费项目 7 天无人游玩会自动暂停，
> 需到控制台点 Restore 恢复；首次部署需在 SQL Editor 执行 `sql/supabase-gobang.sql`。

### 方式二：局域网直连（同一 WiFi，延迟最低）

1. 一方点「创建房间」→ 选本机 IP → 端口默认 9527 →「生成房间」；
2. 另一方点「加入房间」→ 输入对方 IP 和端口 →「连接」。
> 房主首次开房时 Windows 弹防火墙提示，必须勾选「专用网络」并允许。

### 方式三：虚拟局域网（异地 + 想要 P2P 低延迟）

双方安装 [Radmin VPN](https://www.radmin-vpn.com)（免费）加入同一网络，
之后等同局域网：建房时 IP 选 `26.x.x.x`，对方输该 IP 连接。

### 规则说明

- 房主=持子方、客人=猜单双；SHA-256 承诺-揭示保证双方无法作弊；
- 悔棋需对方同意（10 秒未响应自动取消）；终局后「再来一局」双方确认并交换黑白；
- 断线即对局作废（不支持重连），可返回菜单重新开局。

## 测试

```bash
mvn test    # 含协议编解码 / 承诺揭示 / 本机回环对局等 59 项用例
```

## 制作

林森lsjs
