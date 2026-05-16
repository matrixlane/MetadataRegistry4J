@echo off
cd /d "%~dp0"

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
start "Backend" javaw -jar "%~dp0metadata-registry-web\target\metadata-registry-web-0.1.0-SNAPSHOT.jar"

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
