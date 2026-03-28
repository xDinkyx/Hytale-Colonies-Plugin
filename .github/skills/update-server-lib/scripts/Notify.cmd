@echo off
REM ============================================
REM Windows Toast Notification Helper
REM Usage: Notify.cmd "Title" "Message"
REM Silently succeeds even if notifications are unavailable.
REM ============================================
powershell -NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File "%~dp0Notify.ps1" -Title "%~1" -Message "%~2"
exit /b 0
