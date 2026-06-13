@echo off
title Build - ALL FABRIC versions
cd /d "%~dp0"
echo ============================================
echo  Building ALL FABRIC versions...
echo ============================================
echo.

call gradlew.bat ^
  :fabric-1.21:build ^
  :fabric-1.21.4:build ^
  :fabric-1.21.6:build ^
  :fabric-1.21.9:build ^
  :fabric-26.1:build ^
  --continue

echo.
if %ERRORLEVEL% EQU 0 (
    echo [SUCCESS] All Fabric builds completed!
) else (
    echo [WARNING] Some builds failed. Check output above.
)
echo.
pause
