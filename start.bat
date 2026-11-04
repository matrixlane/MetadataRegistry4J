@echo off
cd /d "%~dp0"

rem Overridable endpoints. The defaults keep the current behaviour; the
rem variables exist so the launcher can be exercised on spare ports without
rem touching a running instance.
if not defined MDR_RESTART_CONTROL_DIR set "MDR_RESTART_CONTROL_DIR=%USERPROFILE%\.metadata-registry\restart"
if not defined MDR_BACKEND_PORT (
    if defined SERVER_PORT (set "MDR_BACKEND_PORT=%SERVER_PORT%") else (set "MDR_BACKEND_PORT=8081")
)
if not defined MDR_FRONTEND_PORT set "MDR_FRONTEND_PORT=5173"

rem Prefer the configured JAVA_HOME, then the standard local JDK 17 install, then any valid JDK 17+ on PATH.
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        echo [INFO] Using JAVA_HOME=%JAVA_HOME%
        goto :jdk_ready
    )
)

if exist "%ProgramFiles%\Java\jdk-17\bin\java.exe" (
    set "JAVA_HOME=%ProgramFiles%\Java\jdk-17"
    echo [INFO] Using local JDK 17 from Program Files.
    goto :jdk_ready
)

where java >nul 2>nul
if not errorlevel 1 (
    for /f "delims=" %%I in ('where java') do set "JAVA_EXE=%%I"
    for %%I in ("%JAVA_EXE%") do set "JAVA_HOME=%%~dpI.."
    set "JAVA_HOME=%JAVA_HOME:~0,-1%"
    if exist "%JAVA_HOME%\bin\java.exe" (
        echo [INFO] Using detected JAVA_HOME=%JAVA_HOME%
        goto :jdk_ready
    )
)

echo [ERROR] No suitable JDK 17+ installation found.
echo Please install JDK 17 LTS and set JAVA_HOME, or run: set JAVA_HOME=%ProgramFiles%\Java\jdk-17
echo Then rerun start.bat.
pause
exit /b 1

:jdk_ready
set "PATH=%JAVA_HOME%\bin;%PATH%"
java -version

set "JAR=%~dp0metadata-registry-web\target\metadata-registry-web-0.1.0-SNAPSHOT.jar"

rem ---------- Backend on :%MDR_BACKEND_PORT% ----------
rem Reuse a running instance of this app instead of failing with "port already
rem in use"; refuse to touch a foreign process holding the port.
rem scripts\port-state.ps1 exit codes: 0 = FREE, 1 = OURS, 2 = TAKEN.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\port-state.ps1" -Port %MDR_BACKEND_PORT% -Names javaw.exe,java.exe -Match metadata-registry-web
if errorlevel 2 goto :backend_taken
if errorlevel 1 goto :backend_reuse
goto :backend_start

:backend_reuse
powershell -NoProfile -ExecutionPolicy Bypass -Command ". '%~dp0scripts\backend-supervisor.ps1' -ControlDir '%MDR_RESTART_CONTROL_DIR%'; Initialize-SupervisorPaths; $status = Get-JsonContent $script:StatusPath; if ($null -ne $status -and $script:TerminalStates -notcontains [string]$status.state) { exit 1 }; exit 0"
if errorlevel 1 goto :backend_recovery
powershell -NoProfile -ExecutionPolicy Bypass -Command ". '%~dp0scripts\backend-supervisor.ps1' -ControlDir '%MDR_RESTART_CONTROL_DIR%'; Initialize-SupervisorPaths; $rec = Get-ProcessRecord $script:SupervisorPidPath; if ($null -ne (Get-OwnedProcess $rec)) { exit 0 } else { exit 1 }"
if errorlevel 1 echo [HINT] The running instance is not managed by the supervisor, so "apply and restart" stays unavailable. Run stop.bat, then start.bat again to enable it.
echo [Backend] Already running on :%MDR_BACKEND_PORT% - reusing it.
goto :frontend

:backend_recovery
echo [Backend] Incomplete restart detected - recovering the previous configuration.
goto :backend_start

:backend_taken
echo [ERROR] Port %MDR_BACKEND_PORT% is held by another process that is not this app.
echo         Free the port or run stop.bat first, then retry.
pause
exit /b 1

:backend_start
rem Build only when the JAR is missing or is not a repackaged Spring Boot
rem JAR (no Main-Class - a plain JAR from a partial build fails at launch
rem with "no main manifest attribute").
if not exist "%JAR%" goto :build
powershell -NoProfile -Command "Add-Type -AssemblyName System.IO.Compression.FileSystem; $z = [System.IO.Compression.ZipFile]::OpenRead('%JAR%'); $e = $z.GetEntry('META-INF/MANIFEST.MF'); if ($null -eq $e) { 'BAD' } else { $r = New-Object System.IO.StreamReader($e.Open()); $m = $r.ReadToEnd(); $r.Close(); if ($m -match 'Main-Class:') { 'OK' } else { 'BAD' } }; $z.Dispose()" | findstr /C:"OK" >nul
if errorlevel 1 (
    echo [Build] JAR missing or not a Spring Boot repackaged JAR. Rebuilding...
    goto :build
)
goto :start_backend

:build
call mvnw.cmd clean package -DskipTests
if errorlevel 1 (
    echo [ERROR] Build failed!
    pause
    exit /b 1
)

:start_backend
if not exist "%MDR_RESTART_CONTROL_DIR%" mkdir "%MDR_RESTART_CONTROL_DIR%"
if exist "%MDR_RESTART_CONTROL_DIR%\stop.request" (
    del /q "%MDR_RESTART_CONTROL_DIR%\stop.request"
    if errorlevel 1 (
        echo [ERROR] Could not clear the previous stop request.
        pause
        exit /b 1
    )
)
echo [1/2] Starting backend on :%MDR_BACKEND_PORT%...
rem The resident supervisor owns javaw and retains the existing hidden-window
rem startup and stdout/stderr troubleshooting logs.
rem This launcher starts the personal profile (default SQLite file library).
rem For a team deployment switch the supervisor to --spring.profiles.active=team
rem (see application-team.yml); the team profile does not load orm-sqlite.xml.
powershell -NoProfile -ExecutionPolicy Bypass -Command ". '%~dp0scripts\backend-supervisor.ps1' -ControlDir '%MDR_RESTART_CONTROL_DIR%' -ConfigPath '%USERPROFILE%\.metadata-registry\application-local.yml' -JavaPath '%JAVA_HOME%\bin\javaw.exe' -JarPath '%JAR%' -Port %MDR_BACKEND_PORT% -ServerPort %MDR_BACKEND_PORT%; Start-SupervisorHost"
if errorlevel 1 (
    echo [ERROR] Backend supervisor failed to start.
    pause
    exit /b 1
)
rem The supervisor writes this marker when another instance already owns the
rem control directory. Without it the automatic restart silently stays off.
if exist "%MDR_RESTART_CONTROL_DIR%\supervisor.error" (
    echo [HINT] Another supervisor instance already owns %MDR_RESTART_CONTROL_DIR%.
    echo        "Apply and restart" stays unavailable until that instance stops.
    echo        Run stop.bat first, then start.bat again.
)

:frontend
rem ---------- Frontend on :%MDR_FRONTEND_PORT% ----------
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\port-state.ps1" -Port %MDR_FRONTEND_PORT% -Names node.exe -Match metadata-registry-ui
if errorlevel 2 goto :frontend_taken
if errorlevel 1 goto :frontend_reuse
goto :frontend_start

:frontend_reuse
echo [Frontend] Already running on :%MDR_FRONTEND_PORT% - reusing it.
goto :summary

:frontend_taken
echo [WARN] Port %MDR_FRONTEND_PORT% is held by another process - Vite will fall back to a nearby port, e.g. 5174.

:frontend_start
echo [2/2] Starting frontend on :%MDR_FRONTEND_PORT%...
cd /d "%~dp0metadata-registry-ui"
powershell -NoProfile -ExecutionPolicy Bypass -Command ". '%~dp0scripts\backend-supervisor.ps1' -ControlDir '%MDR_RESTART_CONTROL_DIR%'; Initialize-SupervisorPaths; $p = $null; try { $p = Start-Process 'cmd.exe' -ArgumentList '/c','npm run dev -- --port %MDR_FRONTEND_PORT%' -WorkingDirectory '%~dp0metadata-registry-ui' -WindowStyle Minimized -PassThru; Write-ProcessRecord -Process $p -Path $script:FrontendPidPath } catch { if ($null -ne $p) { Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue }; exit 1 }"
if errorlevel 1 (
    echo [ERROR] Frontend failed to start.
    pause
    exit /b 1
)

:summary
cd /d "%~dp0"
echo.
echo === MetadataRegistry4j Started ===
echo Backend:  http://localhost:%MDR_BACKEND_PORT%
echo Frontend: http://localhost:%MDR_FRONTEND_PORT%
echo Swagger:  http://localhost:%MDR_BACKEND_PORT%/swagger-ui.html
echo.
echo Close this window does NOT stop services.
echo Run stop.bat to stop all services.
echo.
pause
