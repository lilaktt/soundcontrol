@echo off
title Build - ALL NEOFORGE versions
cd /d "%~dp0"
echo ============================================
echo  Building ALL NEOFORGE versions...
echo ============================================
echo.

call gradlew.bat ^
  :neoforge-1.21.1:build ^
  :neoforge-1.21.9:build ^
  :neoforge-1.21.10:build ^
  :neoforge-1.21.11:build ^
  :neoforge-26.1:build ^
  --continue

echo.
if %ERRORLEVEL% EQU 0 (
    echo [SUCCESS] All NeoForge builds completed!
) else (
    echo [WARNING] Some builds failed. Check output above.
)
echo.
pause
