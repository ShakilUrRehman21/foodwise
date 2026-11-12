@echo off
cd /d "%~dp0"
set LOG=logs\env.log
echo === FoodWise environment check %DATE% %TIME% > %LOG%
for %%t in (java mvn node npm docker psql tesseract git winget) do (
  echo --- %%t >> %LOG%
  where %%t >> %LOG% 2>&1
)
echo --- java -version >> %LOG%
java -version >> %LOG% 2>&1
echo --- node -v >> %LOG%
node -v >> %LOG% 2>&1
echo --- docker version >> %LOG%
docker version --format "server {{.Server.Version}}" >> %LOG% 2>&1
echo --- maven central reachable? >> %LOG%
powershell -NoProfile -Command "try { (Invoke-WebRequest -UseBasicParsing -TimeoutSec 15 https://repo.maven.apache.org/maven2/ ).StatusCode } catch { $_.Exception.Message }" >> %LOG% 2>&1
echo --- port 5432 / 8080 / 3000 listeners >> %LOG%
netstat -ano | findstr /R ":5432 :8080 :3000" | findstr LISTENING >> %LOG% 2>&1
echo === DONE >> %LOG%
echo Environment check finished. This window will close in 5 seconds.
timeout /t 5 >nul
