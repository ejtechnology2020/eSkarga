@echo off
rem Uruchamia start.ps1 (eSkarga, srodowisko deweloperskie). Parametry przekazywane dalej: -Przebuduj -BezPrzegladarki -BezRejestracji -Monitoring.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start.ps1" %*
if errorlevel 1 (
  echo.
  echo Wystapil blad - szczegoly powyzej.
  pause
  exit /b 1
)
