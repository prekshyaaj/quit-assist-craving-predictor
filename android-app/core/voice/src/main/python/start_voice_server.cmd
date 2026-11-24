@echo off
title Voice Server Launcher
echo ===================================================
echo Starting Voice ^& Whisper Server...
echo ===================================================

set "SCRIPT_DIR=%~dp0"
set "ROOT_DIR=%SCRIPT_DIR%..\..\..\..\..\"

set "WHISPER_BINARY=%ROOT_DIR%core\whisper\src\main\whisperRepo\whisper.cpp\build\bin\Release\whisper-server.exe"
set "WHISPER_MODEL=%ROOT_DIR%app\src\main\assets\models\ggml-tiny.en.bin"

echo Working Directory: %SCRIPT_DIR%
echo Whisper Binary:    %WHISPER_BINARY%
echo Whisper Model:     %WHISPER_MODEL%
echo ---------------------------------------------------

cd /d "%SCRIPT_DIR%"
python -m uvicorn main:app --host 0.0.0.0 --port 8080
pause
