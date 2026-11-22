@echo off
chcp 65001 >nul
title KeyConnect — Instant Windows Setup & Receiver
cd /d "%~dp0"

set "CSC=%WINDIR%\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
if not exist "%CSC%" set "CSC=%WINDIR%\Microsoft.NET\Framework\v4.0.30319\csc.exe"

if not exist "%CSC%" (
    echo [Error] Built-in .NET Framework 4 compiler was not found on this Windows system.
    pause
    exit /b 1
)

if not exist "instant-windows\KeyConnectQuickHost.exe" (
    echo Building KeyConnect Windows Receiver using built-in Windows .NET compiler...
    "%CSC%" /nologo /target:winexe /optimize+ ^
        /r:System.dll /r:System.Drawing.dll /r:System.Windows.Forms.dll ^
        /out:"instant-windows\KeyConnectQuickHost.exe" ^
        "instant-windows\KeyConnectQuickHost.cs"
    if errorlevel 1 (
        echo [Error] Compilation failed.
        pause
        exit /b 1
    )
)

start "" "instant-windows\KeyConnectQuickHost.exe"
exit /b 0
