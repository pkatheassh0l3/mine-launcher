@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0Publicar-Versiones.ps1" %*
pause
