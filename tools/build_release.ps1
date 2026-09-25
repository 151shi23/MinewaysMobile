<#
  MinewaysMobile · 一键构建（debug + release，release 自动对齐并签名）
  ==========================================================================
  为什么需要它：
    1. 本工程路径含中文与空格，AGP/NDK 在非 ASCII 路径下曾出现构建异常，
       因此脚本先把工程复制到纯 ASCII 沙箱（默认 C:\mmbuild）再构建；
    2. 工程里没有配置 signingConfig，正式包一直是手工用 apksigner 签的，
       这里把「zipalign → apksigner → 校验 → 分发」固化成一条命令。

  用法：
    powershell -ExecutionPolicy Bypass -File neteasemc\tools\build_release.ps1
    # 口令未提供时读环境变量 MINEWAYS_STOREPASS；仍为空则交互输入
    #   -StorePass <口令>   -Alias mineways   -Sandbox C:\mmbuild
    #   -SkipSign          只构建不签名
    #   -Offline           强制离线（依赖已全部缓存时更快）

  注意：本文件是 UTF-8 with BOM（Windows PowerShell 5.1 读中文必需），
        编辑保存时请保留 BOM，否则中文会被按 ANSI 解析而报语法错。
#>
[CmdletBinding()]
param(
    [string]$StorePass = 'Mineways2026',
    [string]$Alias = 'mineways',
    [string]$Sandbox = 'C:\mmbuild',
    [switch]$SkipSign,
    [switch]$Offline
)

$ErrorActionPreference = 'Continue'
$scriptDir = if ($PSScriptRoot) { $PSScriptRoot } else { Split-Path -Parent $MyInvocation.MyCommand.Path }
$ws = Split-Path -Parent (Split-Path -Parent $scriptDir)     # ...\<工作区>\neteasemc 的上层
$project = Join-Path $ws 'neteasemc'
$jks = Join-Path $ws '产物\mineways-release2.jks'
$sdk = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } elseif ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { 'C:\Android\android-sdk' }
$bt = (Get-ChildItem (Join-Path $sdk 'build-tools') -Directory -ErrorAction SilentlyContinue |
        Sort-Object { [version]($_.Name -replace '[^0-9.]', '') } -Descending | Select-Object -First 1).FullName

if (-not (Test-Path $project)) { Write-Host "找不到工程目录：$project" -ForegroundColor Red; exit 1 }
Write-Host ("工程：{0}" -f $project)
Write-Host ("沙箱：{0}" -f $Sandbox)
Write-Host ("build-tools：{0}" -f $bt)

# ---------------------------------------------------------------- 1. 沙箱
Write-Host '== 1/6 复制到 ASCII 沙箱 ==' -ForegroundColor Cyan
if (Test-Path $Sandbox) { cmd /c "rmdir /s /q `"$Sandbox`"" | Out-Null }
robocopy $project $Sandbox /E /XD build .gradle /NFL /NDL /NJH /NJS /NP | Out-Null
$files = Get-ChildItem $Sandbox -Recurse -File
Write-Host ("  {0} 个文件, {1:N1} MB" -f $files.Count, (($files | Measure-Object Length -Sum).Sum / 1MB))

# ---------------------------------------------------------------- 2. 构建
Write-Host '== 2/6 构建 debug + release ==' -ForegroundColor Cyan
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
$log = Join-Path $Sandbox 'build.log'
$offlineArg = if ($Offline) { '--offline' } else { '' }
Push-Location $Sandbox
cmd /c "gradle $offlineArg :app:assembleDebug :app:assembleRelease > `"$log`" 2>&1"
$code = $LASTEXITCODE
Pop-Location
Get-Content $log -Encoding Default | Where-Object { $_ -match 'BUILD SUCCESSFUL|BUILD FAILED|error:|^e: ' } | ForEach-Object { '  ' + $_ }
if ($code -ne 0) {
    Write-Host '  构建失败，日志尾部：' -ForegroundColor Red
    Get-Content $log -Encoding Default | Select-Object -Last 30
    exit 1
}

$unsigned = Join-Path $Sandbox 'app\build\outputs\apk\release\app-release-unsigned.apk'
$debugApk = Join-Path $Sandbox 'app\build\outputs\apk\debug\app-debug.apk'

# ---------------------------------------------------------------- 3. 对齐 + 签名
$release = $null
if ($SkipSign) {
    Write-Host '== 3/6 跳过签名（-SkipSign） ==' -ForegroundColor Yellow
    $release = $unsigned
} else {
    Write-Host '== 3/6 zipalign + 签名 ==' -ForegroundColor Cyan
    if (-not $StorePass) { $StorePass = Read-Host '请输入密钥库口令 (store/key 同一口令)' }
    if (-not (Test-Path $jks)) { Write-Host "  找不到密钥库：$jks" -ForegroundColor Red; exit 1 }
    $aligned = Join-Path $Sandbox 'app-aligned.apk'
    $release = Join-Path $Sandbox 'MinewaysMobile-release.apk'
    & "$bt\zipalign.exe" -f -P 16 4 $unsigned $aligned 2>&1 | ForEach-Object { '  ' + $_ }
    if ($LASTEXITCODE -ne 0) { & "$bt\zipalign.exe" -f 4 $unsigned $aligned 2>&1 | ForEach-Object { '  ' + $_ } }
    & "$bt\apksigner.bat" sign --ks $jks --ks-key-alias $Alias `
        --ks-pass "pass:$StorePass" --key-pass "pass:$StorePass" `
        --out $release $aligned 2>&1 | ForEach-Object { '  ' + $_ }
    if (-not (Test-Path $release)) { Write-Host '  签名失败' -ForegroundColor Red; exit 1 }
}

# ---------------------------------------------------------------- 4. 核验
Write-Host '== 4/6 核验 ==' -ForegroundColor Cyan
if (-not $SkipSign) {
    & "$bt\apksigner.bat" verify --print-certs $release 2>&1 |
        Where-Object { $_ -match 'SHA-256 digest|DN:|Verified using v2|Verified using v3' } | ForEach-Object { '  ' + $_ }
}
$aapt = (Get-ChildItem (Join-Path $sdk 'build-tools') -Recurse -Filter aapt2.exe -ErrorAction SilentlyContinue | Select-Object -First 1).FullName
if ($aapt) {
    & $aapt dump badging $release 2>&1 | Where-Object { $_ -match "^package:|application-label:'" } |
        Select-Object -First 2 | ForEach-Object { '  ' + $_ }
    $tree = & $aapt dump xmltree --file AndroidManifest.xml $release 2>&1 | Out-String
    foreach ($act in @('MiniGameActivity', 'BlockbenchActivity')) {
        Write-Host ('  ' + $act + ' 已注册: ' + [bool]($tree -match $act))
    }
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($release)
$mg = $zip.Entries | Where-Object { $_.FullName -like 'assets/minigame/*' }
$bb = $zip.GetEntry('assets/blockbench/dist/bundle.js')
$saver = $zip.GetEntry('assets/blockbench/plugins/mineways_saver/mineways_saver.js')
Write-Host ("  小游戏资产 {0} 个, {1:N1} MB；保存助手 {2}；Blockbench 内核 {3}" -f `
    $mg.Count, (($mg | Measure-Object Length -Sum).Sum / 1MB), [bool]$saver, [bool]$bb)
$zip.Dispose()

# ---------------------------------------------------------------- 5/6. 兼容性自检
# 旧 Android 上不存在的 Java 9+ 类型。本项目踩过一次：
# caffeine 3.x 引用 java.lang.System$Logger → 转换时报 NoSuchMethodError(getLogger)
# 紧接着 NoClassDefFoundError: Caffeine。此后构建时统一自检，避免同类问题再溜进包里。
Write-Host '== 5/6 Java 9+ 类型自检 ==' -ForegroundColor Cyan
$risky = @('java/lang/System$Logger', 'java/lang/Module', 'java/util/HexFormat', 'java/lang/StackWalker', ')Ljava/nio/MappedByteBuffer;')  # 最后一项：Java 9 的 MappedByteBuffer 协变签名，Android API<34 没有（见 chunker-core/libs 的补丁 jar）
$hits = @()
$zip2 = [System.IO.Compression.ZipFile]::OpenRead($release)
foreach ($e in $zip2.Entries) {
    if ($e.FullName -notlike '*.dex') { continue }
    $ms = New-Object System.IO.MemoryStream
    $s = $e.Open(); $s.CopyTo($ms); $s.Dispose()
    $txt = [System.Text.Encoding]::ASCII.GetString($ms.ToArray())
    foreach ($r in $risky) { if ($txt.Contains($r)) { $hits += ("{0}  in  {1}" -f $r, $e.FullName) } }
}
$zip2.Dispose()
if ($hits.Count) {
    Write-Host '  [警告] 包内引用了旧 Android 上可能不存在的类型：' -ForegroundColor Yellow
    $hits | Select-Object -Unique | ForEach-Object { '    ' + $_ }
} else {
    Write-Host '  通过：未引用 System$Logger / Module / HexFormat / StackWalker' -ForegroundColor Green
}

# ---------------------------------------------------------------- 6/6. 分发
Write-Host '== 5/6 分发到工作区 ==' -ForegroundColor Cyan
Copy-Item $release (Join-Path $ws 'MinewaysMobile-release.apk') -Force
Copy-Item $debugApk (Join-Path $ws 'MinewaysMobile-debug.apk') -Force
$prodDir = Join-Path $ws '产物'
if (Test-Path $prodDir) { Copy-Item $release (Join-Path $prodDir 'MinewaysMobile-release.apk') -Force }
Get-ChildItem $ws -Filter '*.apk' | ForEach-Object { Write-Host ("  {0}  {1:N1} MB" -f $_.Name, ($_.Length / 1MB)) -ForegroundColor Green }

Write-Host '== 完成 ==' -ForegroundColor Cyan
Write-Host ("  沙箱保留在 {0}（含未签名包与日志；不需要可删）" -f $Sandbox)
