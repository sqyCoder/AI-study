# 五子棋一键打包脚本（spec3 §8.2）
# 用法：
#   powershell -ExecutionPolicy Bypass -File package.ps1            # 完整构建（含测试）
#   powershell -ExecutionPolicy Bypass -File package.ps1 -SkipTests # 跳过测试加速
# 产物：dist\Gobang-win64.zip（免安装，解压双击 Gobang.exe 即玩）
#
# 方案说明：Maven 版 OpenJFX 的 module-info 与实现分离，无法直接参与 jlink 解析，
# 故采用「jlink 裁剪纯 JDK 运行时 + JavaFX 走类路径」的标准做法：
#   1) jlink --add-modules <JDK 模块> 生成精简运行时
#   2) jpackage --runtime-image 引用该运行时，应用 jar 全部留在类路径
# JDK 模块根集合依据：javafx.graphics 需要 java.desktop/jdk.unsupported；
# javafx.media 需要 java.sql；其余为传递依赖自动解析。升级依赖后可用下述命令复核：
#   jdeps --multi-release 17 --print-module-deps --ignore-missing-deps target\gobang-1.0-SNAPSHOT.jar
# app-image 不需要 WiX；将来出 msi 安装包仅需把 --type 改为 msi（需先装 WiX Toolset 3.x）。

param(
    [switch]$SkipTests
)

$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

$jdkBin = 'C:\Program Files\Java\jdk-17\bin'
if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\jpackage.exe'))) {
    $jdkBin = Join-Path $env:JAVA_HOME 'bin'
}
$jpackage = Join-Path $jdkBin 'jpackage.exe'
$jlink = Join-Path $jdkBin 'jlink.exe'

Write-Host '==> [1/6] Maven 构建' -ForegroundColor Cyan
if ($SkipTests) {
    mvn -q clean package -DskipTests
} else {
    mvn -q clean package
}
if ($LASTEXITCODE -ne 0) { Write-Host '[错误] Maven 构建失败' -ForegroundColor Red; exit 1 }

Write-Host '==> [2/6] 暂存 jar 与依赖' -ForegroundColor Cyan
$jpkg = 'target\jpkg'
if (Test-Path $jpkg) { Remove-Item $jpkg -Recurse -Force }
New-Item -ItemType Directory -Path $jpkg | Out-Null
Copy-Item 'target\gobang-1.0-SNAPSHOT.jar' $jpkg
if (Test-Path 'target\deps') { Copy-Item 'target\deps\*' $jpkg }

Write-Host '==> [3/6] jlink 裁剪运行时' -ForegroundColor Cyan
$runtime = 'target\runtime'
if (Test-Path $runtime) { Remove-Item $runtime -Recurse -Force }
& $jlink `
    --add-modules java.base,java.desktop,java.sql,java.xml,java.logging,java.management,jdk.unsupported,jdk.crypto.ec `
    --strip-debug --no-header-files --no-man-pages --compress=2 `
    --output $runtime
if ($LASTEXITCODE -ne 0) { Write-Host '[错误] jlink 失败' -ForegroundColor Red; exit 1 }

Write-Host '==> [4/6] jpackage 生成免安装镜像' -ForegroundColor Cyan
$dist = 'target\dist'
if (Test-Path "$dist\Gobang") { Remove-Item "$dist\Gobang" -Recurse -Force }
& $jpackage `
    --type app-image `
    --name Gobang `
    --app-version 1.0 `
    --input $jpkg `
    --main-jar gobang-1.0-SNAPSHOT.jar `
    --main-class org.example.gobang.Launcher `
    --runtime-image $runtime `
    --java-options '-Dfile.encoding=UTF-8' `
    --dest $dist
if ($LASTEXITCODE -ne 0) { Write-Host '[错误] jpackage 失败' -ForegroundColor Red; exit 1 }

Write-Host '==> [5/6] 压缩为 zip' -ForegroundColor Cyan
New-Item -ItemType Directory -Path dist -Force | Out-Null
if (Test-Path 'dist\Gobang-win64.zip') { Remove-Item 'dist\Gobang-win64.zip' -Force }
Compress-Archive -Path "$dist\Gobang" -DestinationPath 'dist\Gobang-win64.zip' -CompressionLevel Optimal

Write-Host '==> [6/6] 产物摘要' -ForegroundColor Cyan
$imgMB = [math]::Round((Get-ChildItem "$dist\Gobang" -Recurse | Measure-Object Length -Sum).Sum / 1MB, 1)
$zipMB = [math]::Round((Get-Item 'dist\Gobang-win64.zip').Length / 1MB, 1)
Write-Host ("  目录: target\dist\Gobang\Gobang.exe  ({0} MB)" -f $imgMB)
Write-Host ("  压缩包: dist\Gobang-win64.zip          ({0} MB)" -f $zipMB)
Write-Host '完成。把 zip 发给朋友即可（Windows 10/11 x64，无需安装 Java）。' -ForegroundColor Green
