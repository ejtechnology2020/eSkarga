@echo off
rem Uruchamia podpis.ps1: klucz testowy sedziego (PKCS#12), magazyn zaufania usługi dokumentów i aplikacja lokalna local-signer na 127.0.0.1:17227.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0podpis.ps1" %*
if errorlevel 1 (
  echo.
  echo Wystapil blad - szczegoly powyzej.
  pause
  exit /b 1
)
