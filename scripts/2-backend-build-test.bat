@echo off
setlocal
cd /d "%~dp0"
set LOG=%~dp0logs\backend-test.log
set TOOLS=%~dp0.tools
set MVN_VER=3.9.9
echo === Backend build + test %DATE% %TIME% > "%LOG%"

rem ---- portable Maven (no admin, kept inside the project)
if not exist "%TOOLS%\apache-maven-%MVN_VER%\bin\mvn.cmd" (
  echo Downloading Maven %MVN_VER%... >> "%LOG%"
  if not exist "%TOOLS%" mkdir "%TOOLS%"
  powershell -NoProfile -Command "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest -UseBasicParsing https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%MVN_VER%/apache-maven-%MVN_VER%-bin.zip -OutFile '%TOOLS%\maven.zip'; Expand-Archive -Force '%TOOLS%\maven.zip' '%TOOLS%'; Remove-Item '%TOOLS%\maven.zip'" >> "%LOG%" 2>&1
)
set PATH=%TOOLS%\apache-maven-%MVN_VER%\bin;%PATH%
call mvn -v >> "%LOG%" 2>&1

rem ---- Docker Desktop (Testcontainers needs it for the Postgres integration tests)
docker info >nul 2>&1
if errorlevel 1 (
  echo Starting Docker Desktop... >> "%LOG%"
  if exist "%LOCALAPPDATA%\Programs\DockerDesktop\Docker Desktop.exe" start "" "%LOCALAPPDATA%\Programs\DockerDesktop\Docker Desktop.exe"
  if exist "%ProgramFiles%\Docker\Docker\Docker Desktop.exe" start "" "%ProgramFiles%\Docker\Docker\Docker Desktop.exe"
  for /l %%i in (1,1,60) do (
    docker info >nul 2>&1 && goto dockerready
    timeout /t 5 >nul
  )
  echo WARNING: Docker did not become ready - integration tests will be skipped. >> "%LOG%"
)
:dockerready
docker version --format "docker server {{.Server.Version}}" >> "%LOG%" 2>&1

rem ---- build + all tests
cd /d "%~dp0..\backend"
echo === mvn test >> "%LOG%"
call mvn -B -ntp test >> "%LOG%" 2>&1
echo === EXIT %ERRORLEVEL% >> "%LOG%"
endlocal
