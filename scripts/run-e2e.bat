@echo off
setlocal
set LOG=%~dp0logs\e2e.log
cd /d "%~dp0..\frontend"
echo === E2E %DATE% %TIME% > "%LOG%"
echo === npm install >> "%LOG%"
call npm install --no-audit --no-fund >> "%LOG%" 2>&1
echo === playwright install >> "%LOG%"
call npx playwright install chromium >> "%LOG%" 2>&1
echo === typecheck >> "%LOG%"
call npm run typecheck >> "%LOG%" 2>&1
echo === TYPECHECK EXIT %ERRORLEVEL% >> "%LOG%"
echo === lint >> "%LOG%"
call npm run lint >> "%LOG%" 2>&1
echo === LINT EXIT %ERRORLEVEL% >> "%LOG%"
echo === start app >> "%LOG%"
call "%~dp03-start-app.bat"
cd /d "%~dp0..\frontend"
echo === playwright test >> "%LOG%"
call npx playwright test >> "%LOG%" 2>&1
echo === E2E EXIT %ERRORLEVEL% >> "%LOG%"
endlocal
