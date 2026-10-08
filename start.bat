@echo off
cd /d "%~dp0"

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

rem ---------- Backend on :8081 ----------
rem Reuse a running instance of this app instead of failing with "Port 8081
rem already in use"; refuse to touch a foreign process holding the port.
rem scripts\port-state.ps1 exit codes: 0 = FREE, 1 = OURS, 2 = TAKEN.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\port-state.ps1" -Port 8081 -Names javaw.exe,java.exe -Match metadata-registry-web
if errorlevel 2 goto :backend_taken
if errorlevel 1 goto :backend_reuse
goto :backend_start

:backend_reuse
echo [Backend] Already running on :8081 - reusing it.
goto :frontend

:backend_taken
echo [ERROR] Port 8081 is held by another process that is not this app.
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
echo [1/2] Starting backend on :8081...
rem No console window (javaw is GUI) + startup logs for failure diagnosis.
rem Start-Process sets stdout/stderr file handles natively and does not wait,
rem so no lingering "Backend" window (cmd /c would wait on the javapath shim
rem and keep the window for the whole backend lifetime - verified).
rem The logs are truncated on each start; they grow during a run (app logs)
rem and are meant for startup troubleshooting - safe to delete anytime.
powershell -NoProfile -Command "Start-Process '%JAVA_HOME%\bin\javaw.exe' -ArgumentList '-jar','%~dp0metadata-registry-web\target\metadata-registry-web-0.1.0-SNAPSHOT.jar' -RedirectStandardOutput '%~dp0logs\backend-stdout.log' -RedirectStandardError '%~dp0logs\backend-stderr.log'"

:frontend
rem ---------- Frontend on :5173 ----------
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\port-state.ps1" -Port 5173 -Names node.exe -Match metadata-registry-ui
if errorlevel 2 goto :frontend_taken
if errorlevel 1 goto :frontend_reuse
goto :frontend_start

:frontend_reuse
echo [Frontend] Already running on :5173 - reusing it.
goto :summary

:frontend_taken
echo [WARN] Port 5173 is held by another process - Vite will fall back to a nearby port, e.g. 5174.

:frontend_start
echo [2/2] Starting frontend on :5173...
cd /d "%~dp0metadata-registry-ui"
start /min "Frontend" cmd /c "npm run dev"

:summary
cd /d "%~dp0"
echo.
echo === MetadataRegistry4j Started ===
echo Backend:  http://localhost:8081
echo Frontend: http://localhost:5173
echo Swagger:  http://localhost:8081/swagger-ui.html
echo.
echo Close this window does NOT stop services.
echo Run stop.bat to stop all services.
echo.
pause
