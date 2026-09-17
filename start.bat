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

if not exist "metadata-registry-web\target\metadata-registry-web-0.1.0-SNAPSHOT.jar" (
    echo [Build] JAR not found, building...
    call mvnw.cmd clean package -DskipTests
    if errorlevel 1 (
        echo [ERROR] Build failed!
        pause
        exit /b 1
    )
)

echo [1/2] Starting backend on :8081...
start "Backend" "%JAVA_HOME%\bin\javaw.exe" -jar "%~dp0metadata-registry-web\target\metadata-registry-web-0.1.0-SNAPSHOT.jar"

echo [2/2] Starting frontend on :5173...
cd /d "%~dp0metadata-registry-ui"
start /min "Frontend" cmd /c "npm run dev"

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
