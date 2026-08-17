# spec3 — 2048 全平台发行计划（作者：林森lsjs）

> 状态（2026-08-13）：一、二、四、五、六 已完成；三-1 网页版已完成（引擎 22 测试全绿，视觉与桌面版对齐：内嵌 MiSans 字体 + 背景光晕，线上 https://game2048-9zy.pages.dev，Cloudflare Pages 免费托管，Gitee Pages 已永久下线故弃用）；三-2 手机版改为**离线单机 APK**（dist/2048.apk，5.5MB：WebView 内嵌全部游戏资源，点开图标直接进游戏，断网可玩，无任何网络权限，签名指纹 4FA6D3...381D20，与 assetlinks 一致）；七/八 待收尾。
>
> 打包脚本：`packaging/build-installer.ps1`（Windows exe）、`packaging/build-offline.ps1`（离线单机 APK，模板在 packaging/offline-tpl/，复用 twa 的 gradle wrapper + keystore）。APK 构建链：gradle(阿里/腾讯镜像)→zipalign→apksigner；keystore 密码 2048GameLSJS（packaging/twa/android-keystore）；web/.well-known/assetlinks.json 随 Cloudflare Pages 部署。`build-twa.ps1`（在线壳）已弃用但保留。网页改版后：重跑 build-offline.ps1 出新 APK；如需刷新线上版用 `wrangler pages deploy web`。

## 背景
项目现状：JavaFX 17 + Maven 桌面版 2048，含纯逻辑引擎（3~8 棋盘）、双语、明暗主题、音效、撒花动画、统计面板。本次目标：可分享的安装包、正式主菜单、手机端生态（最关键）、作者署名、胜利反馈升级。

技术事实：JavaFX 无法运行于安卓/手机，手机端采用 **网页版（HTML5）+ TWA 打包 APK** 路线；游戏逻辑从现有 `GameEngine` 移植，UI 按移动端重写。

## 一、桌面安装包（Windows exe，需求1）
1. `mvn clean javafx:jlink` → 自包含运行时 `target/image`（用户无需装 Java）
2. `jpackage --type exe --name "2048" --app-version 1.0.0 --vendor "林森lsjs" --description "2048 由林森lsjs制作" --win-shortcut --win-menu ...` → `dist/2048-1.0.exe`
3. 效果：双击安装 → 桌面 + 开始菜单出现"2048"图标，点击即玩；exe 可经微信/网盘/QQ 分享，任何 Win10/11 电脑可装，无环境限制
4. 提供 `build-exe.bat` 一键脚本；图标先用默认，日后可换自定义 .ico（待用户提供）

## 二、主菜单界面（需求2）
新增 `mainMenu.fxml` + `MainMenuController`，App 启动先进主菜单，流程：
- 主菜单布局：标题"2048"品牌字 → 主按钮区 → 最高分卡片 → 底部作者署名
- **开始游戏**：主按钮；棋盘选择并入菜单（3×3~8×8 按钮网格，4×4 默认选中并标注"经典"），选中即带尺寸进游戏
- **设置**：语言（中文/English）、主题（明亮/暗黑）、音效开关 + 点击试听、震动开关（手机版）
- **最高分**：历史最佳 + Top5（复用 ScoreStore）
- **退出**：桌面版真正退出
- 游戏界面新增"返回主菜单"按钮（标题区，手机友好），新游戏按菜单所选尺寸开局
- 主菜单与游戏共用现有主题光晕、玻璃卡片、双语机制

## 三、手机版（需求3，最重要，两级交付）
### 3.1 网页版（PWA，零依赖 HTML+CSS+原生 JS）
- 新目录 `web/`：完整 2048，逻辑自 `GameEngine` 移植（移动/合并/计分/胜负/撤销/最高分 localStorage/中英/明暗主题）
- **触摸滑动操作**（方向手势识别）、键盘兼容；手机优先拇指友好布局
- PWA：`manifest.json` + Service Worker（离线可玩、"添加到主屏幕"全屏）
- 胜利反馈与震动同第五章
- **托管**：默认 Gitee Pages（国内访问快，需实名），备选用户自有空间（见待确认项）
### 3.2 TWA 打包 APK
- 步骤：keytool 生成签名 → `@bubblewrap/cli` init + build → `2048.apk`
- 效果：安卓手机直接安装 APK → 桌面图标、全屏、触摸、震动、离线可用；APK 指向托管 URL，网页更新即全员自动更新
- 限制：依赖系统 WebView（Android 5+），安装时允许"未知来源"即可，不走应用商店

## 四、作者署名"林森lsjs"（需求4）
- 主菜单底部"© 林森lsjs"；游戏界面标题区小字"制作：林森lsjs"
- 网页版：主菜单底部 + 关于弹窗
- 安装包属性（卸载列表/详情）：vendor 与描述均署名

## 五、胜利反馈升级（需求5，全升级）
- 桌面版：增强 `EffectManager.confetti` 为大风量全屏撒花 + 分数数字放大脉冲 + 胜利面板升级为"继续游戏/再来一局/返回主菜单"三按钮 + 增强版胜利音效（新音频资源，沿用 M7 生成方式）；桌面无震动（物理限制）
- 网页版：Canvas 全屏撒花 + `navigator.vibrate` 震动（可开关）+ 胜利音效 + 胜利三按钮面板
- 游戏结束同样加强反馈（得分展示 + "超越最佳"提示）

## 六、i18n
新增 key：开始游戏、设置、棋盘选择、经典、最高分、退出、返回、关于、作者、震动、预览音效、继续游戏、再来一局等（中英两文件）

## 七、测试与验证
- 现有 JUnit 全通过（引擎逻辑不动）
- 手工清单：菜单完整流程、设置持久化、尺寸传入、回归原功能
- 网页版：手机真机 + 桌面浏览器 + DevTools 移动模拟
- APK：真机安装/触摸滑动/震动/全屏/图标/离线
- exe：无 Java 环境机器安装运行

## 八、交付物
`dist/2048-1.0.exe`、`web/`+托管 URL、`2048.apk`、`build-exe.bat`

## 待你确认的开放项
1. 网页版托管选 **Gitee Pages** 还是你家别的空间？（决定 APK 里的网址）
2. APK 应用名用"2048"还是"2048·林森lsjs"？
3. 胜利音效应新增一个更隆重的音频（我用工具生成），还是沿用现有 win.wav？
