@echo off
cd /d "%~dp0"
call gradlew.bat :fabric-1.20.1:build :fabric-1.21:build :fabric-1.21.3:build :fabric-1.21.4:build :fabric-1.21.6:build :fabric-1.21.9:build :fabric-26.1:build :fabric-26.2:build :fabric-26.3:build --continue
exit /b %ERRORLEVEL%
