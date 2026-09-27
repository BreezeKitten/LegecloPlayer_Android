@echo off
chcp 65001 >nul
title LegecloPlayer - 安裝/更新 APK 至手機
cd /d "%~dp0"
echo [*] 正在檢查 ADB 連線...
..\adb.exe devices
echo.
echo [*] 正在安裝 LegecloPlayer.apk 至手機...
..\adb.exe install -r -d -g LegecloPlayer.apk
if %errorlevel% neq 0 (
    echo.
    echo ❌ 安裝失敗，請確認手機已連接並允許 USB 偵錯。
) else (
    echo.
    echo ✔ 安裝成功！正在手機上啟動播放器...
    ..\adb.exe shell "am force-stop com.legeclo.player; am start -n com.legeclo.player/.MainActivity"
)
pause
