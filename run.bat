@echo off
setlocal
cd /d "%~dp0"
set "JAVA_EXE=javaw.exe"
if exist "C:\Program Files\Java\jdk-26.0.2.1\bin\javaw.exe" (
    set "JAVA_EXE=C:\Program Files\Java\jdk-26.0.2.1\bin\javaw.exe"
)
start "" "%JAVA_EXE%" --enable-native-access=ALL-UNNAMED -jar "%~dp0target\ReceiptTracker-1.0.0.jar"
