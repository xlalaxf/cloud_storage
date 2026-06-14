@echo off
setlocal

set "JAVA_HOME=C:\Program Files\Java\jdk-21.0.10"

if not exist "%JAVA_HOME%\bin\java.exe" (
  echo [ERROR] JDK 21 not found: %JAVA_HOME%
  pause
  exit /b 1
)

set "PATH=%JAVA_HOME%\bin;%PATH%"

echo Backend Java: %JAVA_HOME%
java -version
echo.

cd /d "%~dp0"
call mvnw.cmd spring-boot:run
