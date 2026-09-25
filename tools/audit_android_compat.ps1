<#
  MinewaysMobile · Android 兼容性审计
  ==========================================================================
  起因：caffeine 3.2.4 引用了 java.lang.System$Logger —— 这个 API 在任何 Android
  版本上都不存在（JDK 9 引入，Android 的 libcore 从未实现），导致世界转换一启动就崩：
      NoSuchMethodError: No static method getLogger…Ljava/lang/System$Logger;
      NoClassDefFoundError: com.github.benmanes.caffeine.cache.Caffeine
  同类问题（引用了 Android 上不存在的 JDK 类型）只要走到那条代码路径就会崩，
  因此本脚本把「所有依赖 JAR 里引用的 java*/javax*/jdk*/sun* 类型」逐个与 android.jar
  的公开类型表对比，列出**引用了 Android 上不存在类型**的类。

  用法：
      powershell -ExecutionPolicy Bypass -File neteasemc\tools\audit_android_compat.ps1
      # -Sdk <路径>  指定 Android SDK   -ExtraJar <目录或jar>  追加审计目标

  说明：本文件是 UTF-8 with BOM（Windows PowerShell 5.1 读中文必需），编辑时请保留 BOM。
        本脚本只能发现「类型缺失」（如 java.awt / javax.imageio / System$Logger），
        无法发现「方法缺失」（如 List.of、String.isBlank），后者需人工或 lint 复核。
#>
[CmdletBinding()]
param(
    [string]$Sdk = $(if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } elseif ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { 'C:\Android\android-sdk' }),
    [string[]]$ExtraJar = @()
)

$ErrorActionPreference = 'Continue'
Add-Type -AssemblyName System.IO.Compression.FileSystem

# ---------------------------------------------------------------- 1. android.jar 类型表
$platform = Get-ChildItem (Join-Path $Sdk 'platforms') -Directory -ErrorAction SilentlyContinue |
    Sort-Object { [int]($_.Name -replace '\D', '') } -Descending | Select-Object -First 1
if (-not $platform) { Write-Host "找不到 Android 平台目录：$Sdk\platforms" -ForegroundColor Red; exit 1 }
$androidJar = Join-Path $platform.FullName 'android.jar'
Write-Host ("android.jar：{0}" -f $androidJar)

$android = New-Object 'System.Collections.Generic.HashSet[string]'
$zip = [System.IO.Compression.ZipFile]::OpenRead($androidJar)
foreach ($e in $zip.Entries) { if ($e.FullName -like '*.class') { [void]$android.Add(($e.FullName -replace '\.class$', '')) } }
$zip.Dispose()
Write-Host ("android 公开类型：{0} 个" -f $android.Count)

# ---------------------------------------------------------------- 2. 收集待审计 JAR
$cache = Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1'
$jars = @()
if (Test-Path $cache) {
    $jars += Get-ChildItem $cache -Recurse -Filter *.jar -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match 'caffeine|leveldb|guava|fastutil|lz4|gson|picocli|desugar' }
}
foreach ($x in $ExtraJar) {
    if (Test-Path $x) {
        $i = Get-Item $x
        if ($i.PSIsContainer) { $jars += Get-ChildItem $i.FullName -Recurse -Filter *.jar -File } else { $jars += $i }
    }
}
$jars = $jars | Sort-Object FullName -Unique
Write-Host ("待审计 JAR：{0} 个" -f $jars.Count)
Write-Host ''

# ---------------------------------------------------------------- 3. 扫描
$grandTotal = 0
foreach ($j in $jars) {
    $missing = [ordered]@{}
    try {
        $zip = [System.IO.Compression.ZipFile]::OpenRead($j.FullName)
        foreach ($e in $zip.Entries) {
            # 跳过 META-INF（含 multi-release 的版本专属类，运行时不会用到）
            if ($e.FullName -notlike '*.class' -or $e.FullName -like 'META-INF/*') { continue }
            $ms = New-Object System.IO.MemoryStream
            $s = $e.Open(); $s.CopyTo($ms); $s.Dispose()
            $txt = [System.Text.Encoding]::ASCII.GetString($ms.ToArray())
            foreach ($m in [regex]::Matches($txt, 'L((?:java|javax|jdk|sun)/[A-Za-z0-9_$/]+);')) {
                $t = $m.Groups[1].Value
                if ($android.Contains($t)) { continue }
                if (-not $missing.Contains($t)) { $missing[$t] = New-Object System.Collections.ArrayList }
                if ($missing[$t].Count -lt 3) { [void]$missing[$t].Add($e.FullName) }
            }
        }
        $zip.Dispose()
    } catch {
        Write-Host ("  [读取失败] {0}: {1}" -f $j.Name, $_.Exception.Message) -ForegroundColor Yellow
        continue
    }
    if ($missing.Count) {
        $grandTotal += $missing.Count
        Write-Host ('=== ' + $j.Name) -ForegroundColor Yellow
        foreach ($t in $missing.Keys) {
            Write-Host ('   缺类型 ' + $t)
            $missing[$t] | ForEach-Object { Write-Host ('      ← ' + $_) -ForegroundColor DarkGray }
        }
        Write-Host ''
    }
}

Write-Host ''
if ($grandTotal) {
    Write-Host ("发现 {0} 处「android.jar 里没有的类型」引用 —— 走到对应代码路径即会崩。" -f $grandTotal) -ForegroundColor Yellow
    Write-Host '处置建议：换用 Android 兼容的库版本，或在应用侧避开相关代码路径。'
} else {
    Write-Host '未发现缺失类型引用。' -ForegroundColor Green
}
