$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir
$javaCmd = if (Test-Path "C:\Program Files\Java\jdk-26.0.2.1\bin\javaw.exe") { "C:\Program Files\Java\jdk-26.0.2.1\bin\javaw.exe" } else { "javaw" }
$jarPath = Join-Path $scriptDir "target\ReceiptTracker-1.0.0.jar"
Write-Host "Starting Receipt Collection Tracker..." -ForegroundColor Green
Start-Process $javaCmd -ArgumentList "--enable-native-access=ALL-UNNAMED", "-jar", "`"$jarPath`""
