@echo off
cd /d "%~dp0.."
set LOG=%~dp0logs\stop-app.log
echo === Stop %DATE% %TIME% > "%LOG%"
docker compose down >> "%LOG%" 2>&1
docker desktop stop >> "%LOG%" 2>&1
if errorlevel 1 taskkill /IM "Docker Desktop.exe" /F >> "%LOG%" 2>&1
echo === DONE >> "%LOG%"
