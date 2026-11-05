@echo off
rem Uruchamia status.ps1 (eSkarga, srodowisko deweloperskie). Stan kontenerow i punktow zdrowia.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0status.ps1" %*
if errorlevel 1 (
  echo.
  echo Wystapil blad - szczegoly powyzej.
  pause
  exit /b 1
)
