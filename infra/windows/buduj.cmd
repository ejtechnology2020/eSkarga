@echo off
rem Uruchamia buduj.ps1 (eSkarga, srodowisko deweloperskie). Parametry: -Szybko -WDockerze.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0buduj.ps1" %*
if errorlevel 1 (
  echo.
  echo Wystapil blad - szczegoly powyzej.
  pause
  exit /b 1
)
