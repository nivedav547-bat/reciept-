@echo off
setlocal
cd /d "%~dp0"
set "JAVA_EXE=java.exe"
if exist "C:\Program Files\Java\jdk-26.0.2.1\bin\java.exe" (
    set "JAVA_EXE=C:\Program Files\Java\jdk-26.0.2.1\bin\java.exe"
)
echo Starting Receipt Tracker Localhost Web App on http://localhost:8080 ...
start "Receipt Tracker Web Server" "%JAVA_EXE%" -cp "%~dp0target\ReceiptTracker-1.0.0.jar" com.receipttracker.ReceiptTrackerServer
start http://localhost:8080
