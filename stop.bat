@echo off
cd /d "%~dp0"
if not defined MDR_RESTART_CONTROL_DIR set "MDR_RESTART_CONTROL_DIR=%USERPROFILE%\.metadata-registry\restart"
if not defined MDR_BACKEND_PORT set "MDR_BACKEND_PORT=8081"
if not defined MDR_FRONTEND_PORT set "MDR_FRONTEND_PORT=5173"

echo Stopping services...
powershell -NoProfile -ExecutionPolicy Bypass -Command ". '%~dp0scripts\backend-supervisor.ps1' -ControlDir '%MDR_RESTART_CONTROL_DIR%'; Initialize-SupervisorPaths; Write-AtomicText -Path $script:StopPath -Content 'stop'; if (Invoke-SupervisorStop) { exit 0 } else { exit 1 }"
if errorlevel 1 (
    echo [ERROR] Could not stop all managed services.
    exit /b 1
)

rem Fall back for instances started before the supervisor existed: stop them only
rem when port-state.ps1 identifies the holder as this application, never a
rem foreign program that happens to sit on the same port.
rem The kind argument is used instead of passing a comma separated name list
rem through CALL: CALL drops arguments that contain commas.
call :stop_port %MDR_BACKEND_PORT% backend
call :stop_port %MDR_FRONTEND_PORT% frontend

echo Done.
rem Fully qualified: the GNU timeout from a Git Bash PATH would reject "/t".
"%SystemRoot%\System32\timeout.exe" /t 1 /nobreak > nul
exit /b 0

:stop_port
set "PORT=%~1"
set "KIND=%~2"
if /i "%KIND%"=="backend" (
    set "NAMES=javaw.exe,java.exe"
    set "MATCH=metadata-registry-web"
) else (
    set "NAMES=node.exe"
    set "MATCH=metadata-registry-ui"
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\port-state.ps1" -Port %PORT% -Names %NAMES% -Match %MATCH%
if errorlevel 2 (
    echo [WARN] Port %PORT% is held by another program - left untouched.
    exit /b 0
)
if errorlevel 1 (
    powershell -NoProfile -ExecutionPolicy Bypass -Command "$c = Get-NetTCPConnection -State Listen -LocalPort %PORT% -ErrorAction SilentlyContinue | Select-Object -First 1; if ($c) { Stop-Process -Id $c.OwningProcess -Force -ErrorAction SilentlyContinue; Write-Host ('    stopped unmanaged pid ' + $c.OwningProcess) }"
    exit /b 0
)
exit /b 0