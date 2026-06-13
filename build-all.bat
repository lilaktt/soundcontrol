@echo off
title Build - ALL versions (Fabric + NeoForge)
cd /d "%~dp0"
echo ============================================
echo  Building ALL versions (Fabric + NeoForge)
echo ============================================
echo.

call gradlew.bat ^
  :common:build ^
  :fabric-1.21:build ^
  :fabric-1.21.3:build ^
  :fabric-1.21.4:build ^
  :fabric-1.21.6:build ^
  :fabric-1.21.9:build ^
  :fabric-26.1:build ^
  :neoforge-1.21.1:build ^
  :neoforge-1.21.9:build ^
  :neoforge-1.21.10:build ^
  :neoforge-1.21.11:build ^
  :neoforge-26.1:build ^
  --continue

echo.
echo ============================================
if %ERRORLEVEL% EQU 0 (
    echo  [SUCCESS] ALL builds completed!
    echo  JAR files are in each module's build\libs\ folder.
) else (
    echo  [WARNING] Some builds failed. Check output above.
)
echo ============================================
echo.

REM Show all generated JARs
echo Generated JARs:
for /r "." %%f in (build\libs\*.jar) do (
    if not "%%~nxf"=="-sources.jar" (
        echo   %%f
    )
)
echo.
pause
