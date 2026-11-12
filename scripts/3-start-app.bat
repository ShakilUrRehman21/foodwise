@echo off
setlocal
cd /d "%~dp0.."
set LOG=%~dp0logs\start-app.log
echo === Start FoodWise %DATE% %TIME% > "%LOG%"
docker info >nul 2>&1
if errorlevel 1 (
  if exist "%LOCALAPPDATA%\Programs\DockerDesktop\Docker Desktop.exe" start "" "%LOCALAPPDATA%\Programs\DockerDesktop\Docker Desktop.exe"
  for /l %%i in (1,1,60) do (
    docker info >nul 2>&1 && goto ready
    timeout /t 5 >nul
  )
)
:ready
docker compose up --build -d >> "%LOG%" 2>&1
echo === COMPOSE EXIT %ERRORLEVEL% >> "%LOG%"
echo Waiting for the API to become healthy... >> "%LOG%"
for /l %%i in (1,1,60) do (
  powershell -NoProfile -Command "try { (Invoke-WebRequest -UseBasicParsing -TimeoutSec 3 http://localhost:8080/api/v1/health).Content } catch { exit 1 }" >> "%LOG%" 2>&1 && goto up
  timeout /t 5 >nul
)
echo API did not become healthy in time >> "%LOG%"
docker compose logs --tail 80 api >> "%LOG%" 2>&1
goto end
:up
echo. >> "%LOG%"
echo === APP READY: http://localhost:3000 >> "%LOG%"
docker compose ps >> "%LOG%" 2>&1
start "" http://localhost:3000
:end
endlocal
