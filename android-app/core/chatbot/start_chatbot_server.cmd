@echo off
title Chatbot Server Launcher
echo ===================================================
echo Starting Chatbot Server on Port 8050...
echo ===================================================

set "SCRIPT_DIR=%~dp0"

cd /d "%SCRIPT_DIR%"
python -m uvicorn main:app --host 0.0.0.0 --port 8050
pause
