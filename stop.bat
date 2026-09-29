@echo off
cd /d "%~dp0"

echo Stopping services...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :8181') do taskkill /F /PID %%a > nul 2>&1
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :5173') do taskkill /F /PID %%a > nul 2>&1

echo Done.
timeout /t 1 /nobreak > nul