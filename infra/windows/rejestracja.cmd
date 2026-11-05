@echo off
rem Uruchamia rejestracja.ps1 (eSkarga, srodowisko deweloperskie). Klienci eSkargi w Keycloaku ID-users.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0rejestracja.ps1" %*
if errorlevel 1 (
  echo.
  echo Wystapil blad - szczegoly powyzej.
  pause
  exit /b 1
)
