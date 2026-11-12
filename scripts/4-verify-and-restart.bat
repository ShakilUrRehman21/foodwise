@echo off
cd /d "%~dp0"
call 2-backend-build-test.bat
call 3-start-app.bat
echo DONE > logs\verify-done.txt
