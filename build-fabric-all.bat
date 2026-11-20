@echo off
title Build - ALL FABRIC versions
cd /d "%~dp0"
call "%~dp0build-modules.bat" fabric-1.20.1 fabric-1.21 fabric-1.21.2 fabric-1.21.4 fabric-1.21.6 fabric-1.21.9 fabric-26.1 fabric-26.2 fabric-26.3
