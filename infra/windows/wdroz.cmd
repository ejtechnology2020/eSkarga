@echo off
rem Uruchamia wdroz.ps1 (eSkarga, srodowisko deweloperskie). Parametry: -TylkoApp -Testy.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0wdroz.ps1" %*
if errorlevel 1 (
  echo.
  echo Wystapil blad - szczegoly powyzej.
  pause
  exit /b 1
)
