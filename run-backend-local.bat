@echo off
chcp 65001 >nul
rem Local dev launcher: loads private keys from env.local.bat (gitignored), then runs the backend jar.
rem Used by .claude/launch.json. Without env.local.bat, AI features fall back to the rule engine.
if exist "%~dp0env.local.bat" call "%~dp0env.local.bat"
java -jar "%~dp0canteen-backend\target\canteen-backend-1.0.0.jar"
