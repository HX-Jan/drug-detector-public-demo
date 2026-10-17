param([switch]$VerifyOnly, [switch]$VerifyUi, [string]$JdkHome)
$ErrorActionPreference = 'Stop'
# 优先使用显式指定的 JDK，其次检查 JAVA_HOME、PATH 和本机缓存，避免误用仅有运行时的 Java 8。
$candidates = @()
if ($JdkHome) { $candidates += $JdkHome }
if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
$compiler = Get-Command javac -ErrorAction SilentlyContinue
if ($compiler) { $candidates += Split-Path -Parent (Split-Path -Parent $compiler.Source) }
$cachedJdks = Join-Path $env:USERPROFILE '.cache\drug-detector-build\jdk17'
if (Test-Path -LiteralPath $cachedJdks) {
    $candidates += @(Get-ChildItem -LiteralPath $cachedJdks -Directory | ForEach-Object FullName)
}
$selectedJdk = $null
foreach ($candidate in $candidates) {
    $javacPath = Join-Path $candidate 'bin\javac.exe'
    if (-not (Test-Path -LiteralPath $javacPath)) { continue }
    $versionText = (& $javacPath -version 2>&1 | Out-String)
    if ($versionText -match 'javac\s+(\d+)' -and [int]$Matches[1] -ge 17) {
        $selectedJdk = $candidate
        break
    }
}
if (-not $selectedJdk) { throw '需要 JDK 17 或更新版本。请用 -JdkHome 指定 JDK 安装目录。' }
$javaPath = Join-Path $selectedJdk 'bin\java.exe'
$javacPath = Join-Path $selectedJdk 'bin\javac.exe'
$repoRoot = Split-Path -Parent $PSScriptRoot
$buildDir = Join-Path $repoRoot 'desktop\build\standalone'
New-Item -ItemType Directory -Path $buildDir -Force | Out-Null
$sourceFiles = @(Get-ChildItem -LiteralPath (Join-Path $repoRoot 'core\src\main\java'),(Join-Path $repoRoot 'desktop\src\main\java'),(Join-Path $repoRoot 'core\src\test\java'),(Join-Path $repoRoot 'desktop\src\test\java') -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
& $javacPath --release 17 -encoding UTF-8 -d $buildDir @sourceFiles
if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed' }
& $javaPath '-Dfile.encoding=UTF-8' -cp $buildDir org.example.detector.core.CoreVerification
if ($LASTEXITCODE -ne 0) { throw 'Verification failed' }
& $javaPath '-Dfile.encoding=UTF-8' -cp $buildDir org.example.detector.core.StandardCurveVerification
if ($LASTEXITCODE -ne 0) { throw 'Standard curve verification failed' }
# UI 验收使用临时数据目录，不读取或污染日常演示记录。
if ($VerifyUi) {
    & $javaPath '-Dfile.encoding=UTF-8' -cp $buildDir org.example.detector.desktop.DesktopVerification (Join-Path $repoRoot 'desktop\build\ui-evidence')
    if ($LASTEXITCODE -ne 0) { throw 'Desktop UI verification failed' }
}
if (-not $VerifyOnly -and -not $VerifyUi) {
    Push-Location $repoRoot
    try { & $javaPath '-Dfile.encoding=UTF-8' -cp $buildDir org.example.detector.desktop.DesktopApp } finally { Pop-Location }
}
