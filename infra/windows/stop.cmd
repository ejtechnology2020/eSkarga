@echo off
rem Uruchamia stop.ps1 (eSkarga, srodowisko deweloperskie). Dane zostaja.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0stop.ps1" %*
if errorlevel 1 (
  echo.
  echo Wystapil blad - szczegoly powyzej.
  pause
  exit /b 1
)
