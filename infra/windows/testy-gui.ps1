<#
.SYNOPSIS
  Testy GUI eSkargi (Playwright) na działającym środowisku dev obok ID-users – procesy od końca do końca w trybie OIDC
  (logowanie z kodem jednorazowym, rejestracja pisma ze skanem, kolejka, założenie sprawy, akta, repertorium, dziennik,
  wylogowanie). Konto testowe e2e.eskarga zakłada sam test (Admin API Keycloaka), hasła czyta z id\dev\.env – nic nie trafia do repozytorium.
.PARAMETER Demo
  Zamiast procesów OIDC uruchamia testy ekranów w trybie demo i audyt axe (projekt „demo”; aplikacja z ESKARGA_SECURITY_MODE=demo).
.PARAMETER Login
  Istniejące konto ID-users zamiast zakładania e2e.eskarga (wtedy potrzebny -KluczTotp albo plik totp-<login>.txt w ID_USERS_KLUCZE_DIR).
.PARAMETER KluczTotp
  Klucz TOTP (Base32) konta z -Login.
.PARAMETER Przegladarka
  Pokazuje okno przeglądarki (domyślnie headless).
#>
[CmdletBinding()]
param(
  [switch]$Demo,
  [string]$Login,
  [string]$KluczTotp,
  [switch]$Przegladarka
)
. "$PSScriptRoot\wspolne.ps1"
$ErrorActionPreference = 'Stop'

function Read-PlikEnv([string]$Plik, [string]$Nazwa, [string]$Domyslna) {
  if (Test-Path $Plik) {
    foreach ($linia in Get-Content $Plik -Encoding UTF8) {
      if ($linia -match "^\s*$([regex]::Escape($Nazwa))\s*=\s*(.*)$") {
        $w = $Matches[1]; if ($w -match '^(.*?)\s+#.*$') { $w = $Matches[1] }; return $w.Trim()
      }
    }
  }
  return $Domyslna
}

Write-Krok 'Sprawdzam Node.js'
if (-not (Test-Polecenie 'npm')) { Write-Blad 'Brak Node.js (npm). Zainstaluj Node.js 22 LTS: https://nodejs.org/'; exit 1 }
Write-Ok "npm $(& npm --version)"

$uiTests = Join-Path $Script:Repo 'ui-tests'
$env:ESKARGA_URL = Get-Ustawienie 'ESKARGA_PUBLIC_URL' 'http://localhost:8082/eskarga'
Write-Krok "Sprawdzam eSkargę ($env:ESKARGA_URL)"
try { Invoke-WebRequest -Uri "$env:ESKARGA_URL/" -UseBasicParsing -TimeoutSec 10 -MaximumRedirection 0 -ErrorAction SilentlyContinue | Out-Null; Write-Ok 'aplikacja odpowiada' }
catch { if ($_.Exception.Response) { Write-Ok 'aplikacja odpowiada' } else { Write-Blad "eSkarga nie odpowiada pod $env:ESKARGA_URL – uruchom infra\windows\start.cmd"; exit 1 } }

if (-not $Demo) {
  Assert-IdUsers
  $idEnv = Join-Path $Script:Repo '..\id\dev\.env'
  $kluczeDir = Get-Ustawienie 'ID_USERS_KLUCZE_DIR' '../../../id/frontend/narzedzia/wyniki/eskarga'
  $env:ID_USERS_KLUCZE_DIR = (Resolve-Path (Join-Path $Script:Docker $kluczeDir) -ErrorAction SilentlyContinue).Path
  $env:ESKARGA_E2E = '1'
  $env:ESKARGA_E2E_KEYCLOAK_URL = "http://localhost:$(Get-PortIdUsers)"
  if ($Login) {
    $env:ESKARGA_E2E_LOGIN = $Login
    $env:ESKARGA_E2E_HASLO = Read-PlikEnv $idEnv 'DEV_USER_PASSWORD' ''
    if ($KluczTotp) { $env:ESKARGA_E2E_TOTP = $KluczTotp }
    Write-Ok "konto $Login (hasło DEV_USER_PASSWORD z id\dev\.env)"
  } else {
    $adminHaslo = Read-PlikEnv $idEnv 'KEYCLOAK_ADMIN_PASSWORD' ''
    if (-not $adminHaslo) { Write-Blad "Brak KEYCLOAK_ADMIN_PASSWORD w $idEnv – podaj -Login istniejącego konta albo uzupełnij id\dev\.env"; exit 1 }
    $env:ESKARGA_E2E_KC_ADMIN_HASLO = $adminHaslo
    $env:ESKARGA_E2E_LOGIN = 'e2e.eskarga'
    $env:ESKARGA_E2E_HASLO = 'E2e-Haslo-Testowe-2026'
    Write-Ok 'konto e2e.eskarga zakłada test (role klienta eskarga: biuro podawcze, referent, asystent, administrator; OTP przy pierwszym logowaniu)'
  }
}

Write-Krok 'Zależności Playwright (ui-tests)'
Push-Location $uiTests
try {
  $ErrorActionPreference = 'Continue'
  if (-not (Test-Path 'node_modules')) { & npm ci --no-audit --no-fund; if ($LASTEXITCODE -ne 0) { exit 1 } }
  & npx playwright install chromium; if ($LASTEXITCODE -ne 0) { exit 1 }
  $projekt = if ($Demo) { 'demo' } else { 'oidc' }
  Write-Krok "Uruchamiam testy (projekt $projekt)"
  $argumenty = @('playwright', 'test', '--project', $projekt)
  if ($Przegladarka) { $argumenty += '--headed' }
  & npx @argumenty
  $kod = $LASTEXITCODE
  if ($kod -eq 0) { Write-Ok 'Testy GUI zielone' } else { Write-Blad "Testy GUI nie przeszły (kod $kod). Raport: ui-tests\playwright-report\index.html (npx playwright show-report)" }
  exit $kod
} finally { Pop-Location }
