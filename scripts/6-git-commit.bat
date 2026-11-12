@echo off
cd /d "%~dp0.."
set LOG=%~dp0logs\git.log
echo === git %DATE% %TIME% > "%LOG%"
git config user.name >> "%LOG%" 2>&1
git config user.email >> "%LOG%" 2>&1
if not exist .git git init -b main >> "%LOG%" 2>&1
git add -A >> "%LOG%" 2>&1
git commit -m "FoodWise: full-stack platform (Spring Boot API, Next.js app, Postgres, local OCR)" >> "%LOG%" 2>&1
git log --oneline -3 >> "%LOG%" 2>&1
git status --short >> "%LOG%" 2>&1
git ls-files | find /c /v "" >> "%LOG%" 2>&1
echo === DONE >> "%LOG%"
