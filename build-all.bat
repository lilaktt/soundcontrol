@echo off
cd /d "%~dp0"
call gradlew.bat build --continue
exit /b %ERRORLEVEL%
