@echo off
rem Uruchamia logi.ps1 (eSkarga, srodowisko deweloperskie). Parametry: [usluga] -Sledz.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0logi.ps1" %*
if errorlevel 1 (
  echo.
  echo Wystapil blad - szczegoly powyzej.
  pause
  exit /b 1
)
