@echo off
setlocal

set "ROOT=%~dp0"
set "BACKEND_DIR=%ROOT%backend"
set "FRONTEND_DIR=%ROOT%frontend"

echo Cloud Storage one-click launcher
echo Root: %ROOT%
echo.

if not exist "%BACKEND_DIR%\mvnw.cmd" (
  echo [ERROR] backend\mvnw.cmd not found.
  pause
  exit /b 1
)

if not exist "%FRONTEND_DIR%\package.json" (
  echo [ERROR] frontend\package.json not found.
  pause
  exit /b 1
)

where java >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Java is not available in PATH.
  pause
  exit /b 1
)

where npm.cmd >nul 2>nul
if errorlevel 1 (
  echo [ERROR] npm.cmd is not available in PATH.
  pause
  exit /b 1
)

if not exist "%FRONTEND_DIR%\node_modules" (
  echo Installing frontend dependencies...
  pushd "%FRONTEND_DIR%"
  call npm.cmd install
  if errorlevel 1 (
    popd
    echo [ERROR] npm install failed.
    pause
    exit /b 1
  )
  popd
)

powershell -NoProfile -Command "if (Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue) { exit 0 } else { exit 1 }"
if errorlevel 1 (
  echo Starting backend on http://localhost:8080
  start "Cloud Storage Backend" cmd /k "cd /d ""%BACKEND_DIR%"" && call mvnw.cmd spring-boot:run"
) else (
  echo Backend is already running on http://localhost:8080
)

powershell -NoProfile -Command "if (Get-NetTCPConnection -LocalPort 5173 -State Listen -ErrorAction SilentlyContinue) { exit 0 } else { exit 1 }"
if errorlevel 1 (
  echo Starting frontend on http://127.0.0.1:5173
  start "Cloud Storage Frontend" cmd /k "cd /d ""%FRONTEND_DIR%"" && call npm.cmd run dev -- --host 127.0.0.1"
) else (
  echo Frontend is already running on http://127.0.0.1:5173
)

echo.
echo Services are starting in separate windows.
echo Frontend: http://127.0.0.1:5173
echo Backend:  http://localhost:8080
echo Close the two service windows or press Ctrl+C inside them to stop.
echo.
pause
