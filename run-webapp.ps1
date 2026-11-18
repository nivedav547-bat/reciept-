$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir
$javaCmd = if (Test-Path "C:\Program Files\Java\jdk-26.0.2.1\bin\java.exe") { "C:\Program Files\Java\jdk-26.0.2.1\bin\java.exe" } else { "java" }
$jarPath = Join-Path $scriptDir "target\ReceiptTracker-1.0.0.jar"
Write-Host "Starting Receipt Tracker Localhost Web App on http://localhost:8080 ..." -ForegroundColor Green
Start-Process $javaCmd -ArgumentList "-cp", "`"$jarPath`"", "com.receipttracker.ReceiptTrackerServer"
Start-Sleep -Seconds 1
Start-Process "http://localhost:8080"
