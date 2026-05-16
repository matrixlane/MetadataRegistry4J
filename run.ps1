#Requires -Version 5.1
System.Management.Automation.Internal.Host.InternalHost.UI.RawUI.WindowTitle = "MetadataRegistry4j Launcher"
 = Split-Path -Parent System.Management.Automation.InvocationInfo.MyCommand.Path
Set-Location 

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "   MetadataRegistry4j Startup" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""

 = Join-Path  "metadata-registry-web\target\metadata-registry-web-0.1.0-SNAPSHOT.jar"
if (-not (Test-Path )) {
    Write-Host "[INFO] Backend JAR not found, building now..." -ForegroundColor Yellow
    & .\mvnw.cmd clean package -DskipTests
    if ( -ne 0) {
        Write-Host "[ERROR] Build failed!" -ForegroundColor Red
        Read-Host "Press Enter to exit"
        exit 1
    }
}

Write-Host "[1/2] Starting Backend (Spring Boot :8081)..." -ForegroundColor Green
 = Start-Process cmd -ArgumentList "/k cd /d "" ^&^& java -jar metadata-registry-web\target\metadata-registry-web-0.1.0-SNAPSHOT.jar" -PassThru

Write-Host "[2/2] Starting Frontend (Vite + Vue :5173)..." -ForegroundColor Green
 = Start-Process cmd -ArgumentList "/k cd /d "\metadata-registry-ui" ^&^& npm run dev" -PassThru

Write-Host ""
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "   Services started!" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Backend:  http://localhost:8081"
Write-Host "Frontend: http://localhost:5173"
Write-Host "Swagger:  http://localhost:8081/swagger-ui.html"
Write-Host ""
Write-Host "Press Enter to stop all services..."
[void][System.Console]::ReadLine()

Stop-Process -Id .Id -Force -ErrorAction SilentlyContinue
Stop-Process -Id .Id -Force -ErrorAction SilentlyContinue