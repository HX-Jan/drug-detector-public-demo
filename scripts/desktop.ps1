param([switch]$VerifyOnly)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$buildDir = Join-Path $repoRoot 'desktop\build\standalone'
New-Item -ItemType Directory -Path $buildDir -Force | Out-Null
$sourceFiles = @(Get-ChildItem -LiteralPath (Join-Path $repoRoot 'core\src\main\java'),(Join-Path $repoRoot 'desktop\src\main\java'),(Join-Path $repoRoot 'core\src\test\java') -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
& javac --release 17 -encoding UTF-8 -d $buildDir @sourceFiles
if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed' }
& java -cp $buildDir org.example.detector.core.CoreVerification
if ($LASTEXITCODE -ne 0) { throw 'Verification failed' }
if (-not $VerifyOnly) {
    Push-Location $repoRoot
    try { & java -cp $buildDir org.example.detector.desktop.DesktopApp } finally { Pop-Location }
}
