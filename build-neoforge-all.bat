@echo off
cd /d "%~dp0"
call gradlew.bat :neoforge-1.20.1:build :neoforge-1.21.1:build :neoforge-1.21.10:build :neoforge-1.21.11:build :neoforge-1.21.9:build :neoforge-26.1:build :neoforge-26.2:build :neoforge-26.3:build --continue
exit /b %ERRORLEVEL%
