@echo off
title Build - ALL versions (Fabric + NeoForge)
cd /d "%~dp0"
call "%~dp0build-modules.bat" fabric-1.20.1 fabric-1.21 fabric-1.21.2 fabric-1.21.4 fabric-1.21.6 fabric-1.21.9 fabric-26.1 fabric-26.2 fabric-26.3 neoforge-1.20.1 neoforge-1.21.1 neoforge-1.21.9 neoforge-1.21.10 neoforge-1.21.11 neoforge-26.1 neoforge-26.2 neoforge-26.3
