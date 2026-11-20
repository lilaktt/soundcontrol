@echo off
title Build - ALL NEOFORGE versions
cd /d "%~dp0"
call "%~dp0build-modules.bat" neoforge-1.20.1 neoforge-1.21.1 neoforge-1.21.9 neoforge-1.21.10 neoforge-1.21.11 neoforge-26.1 neoforge-26.2 neoforge-26.3
