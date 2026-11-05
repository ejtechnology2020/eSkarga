@echo off
rem Uruchamia testy-gui.ps1 (Playwright: procesy eSkargi w trybie OIDC na srodowisku dev; -Demo: ekrany demo i axe).
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0testy-gui.ps1" %*
if errorlevel 1 (
  echo.
  echo Wystapil blad - szczegoly powyzej.
  pause
  exit /b 1
)
