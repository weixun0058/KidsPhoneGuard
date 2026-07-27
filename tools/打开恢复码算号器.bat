@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0recovery-code-calculator.ps1"
if errorlevel 1 pause
