@echo off
set GIT_PATH="C:\Program Files\Git\cmd\git.exe"
set REPO_DIR=%~dp0
cd /d %REPO_DIR%

echo [1/4] Staging changes...
%GIT_PATH% add .

echo [2/4] Committing...
%GIT_PATH% commit -m "Port to 26.1.1 and UI improvements (gold entries, draggable radar)"

echo [3/4] Switching to port-26.1.1 branch...
%GIT_PATH% checkout -b port-26.1.1 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo Branch port-26.1.1 already exists, switching to it...
    %GIT_PATH% checkout port-26.1.1
)

echo [4/4] Pushing to GitHub...
%GIT_PATH% push github port-26.1.1

echo.
echo ==========================================
echo DONE! Check your GitHub: https://github.com/lilaktt/soundcontrol
echo ==========================================
pause
