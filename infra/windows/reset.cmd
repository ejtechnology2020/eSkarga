@echo off
rem Uruchamia reset.ps1 (eSkarga, srodowisko deweloperskie). Parametry: -Wymus -ZObrazami -ZMaven.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0reset.ps1" %*
if errorlevel 1 (
  echo.
  echo Wystapil blad - szczegoly powyzej.
  pause
  exit /b 1
)
