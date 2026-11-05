<#
.SYNOPSIS
  Uruchamia środowisko deweloperskie eSkarga w Docker Desktop (Windows) obok działającego ID-users.
.DESCRIPTION
  Buduje WAR-y i workery Mavenem (lokalny JDK 21 albo kontener Maven – Javy nie trzeba instalować),
  rejestruje klientów eSkargi w Keycloaku ID-users (idempotentnie), uruchamia WildFly+Operaton,
  PostgreSQL z migracjami Flyway, RustFS (S3), OpenSearch, SoftHSM, TSA dev, WireMock i workery, a potem
  czeka na gotowość. Pierwsze uruchomienie trwa 10–20 minut (obrazy, dystrybucja Operaton, zależności Maven).
.PARAMETER Przebuduj
  Buduje artefakty i obrazy ponownie (po zmianach w kodzie).
.PARAMETER BezPrzegladarki
  Nie otwiera aplikacji w przeglądarce po starcie.
.PARAMETER BezRejestracji
  Pomija rejestrację klientów w Keycloaku ID-users.
.PARAMETER Monitoring
  Uruchamia też Prometheusa i Grafanę (profil monitoring).
.EXAMPLE
  .\infra\windows\start.ps1
.EXAMPLE
  .\infra\windows\start.ps1 -Przebuduj -Monitoring
#>
[CmdletBinding()]
param(
  [switch]$Przebuduj,
  [switch]$BezPrzegladarki,
  [switch]$BezRejestracji,
  [switch]$Monitoring
)
. "$PSScriptRoot\wspolne.ps1"

Write-Krok 'Sprawdzam Docker Desktop'
Assert-Docker
Initialize-Env
$tryb = Get-TrybBezpieczenstwa

if ($tryb -eq 'oidc') {
  Write-Krok 'Sprawdzam ID-users (logowanie przez Keycloak ID-users)'
  Assert-IdUsers
} else {
  Write-Uwaga "Tryb $tryb – bez ID-users (ADR 0004)"
}

Write-Krok 'Sprawdzam wolne porty'
$dzialajace = @(Get-StanUslug | Where-Object { (Get-Pole $_ 'State') -eq 'running' })
if ($dzialajace.Count -eq 0) {
  $zajete = @()
  $domyslne = [ordered]@{ PORT_APP = '8082'; PORT_APP_ADMIN = '9992'; PORT_DB = '5433'; PORT_S3 = '9100'; PORT_S3_CONSOLE = '9101'; PORT_OPENSEARCH = '9200'; PORT_TSA = '3180'; PORT_WIREMOCK = '8089' }
  if ($Monitoring) { $domyslne['PORT_PROMETHEUS'] = '9090'; $domyslne['PORT_GRAFANA'] = '3000' }
  foreach ($nazwa in $domyslne.Keys) {
    $port = [int](Get-Ustawienie $nazwa $domyslne[$nazwa])
    if (-not (Test-PortWolny $port)) { $zajete += "$port ($nazwa)" }
  }
  if ($zajete.Count -gt 0) {
    Write-Blad ('Zajęte porty: ' + ($zajete -join ', ') + '. Zmień je w infra\docker\.env (PORT_*) albo zatrzymaj programy, które ich używają.')
    exit 1
  }
  Write-Ok 'Porty wolne'
} else {
  Write-Ok 'Środowisko częściowo działa – porty pomijam'
}

if ($Przebuduj -or -not (Test-Artefakty)) {
  if ((Build-Maven) -ne 0) {
    Write-Blad 'Build Mavena zakończył się błędem – szczegóły powyżej.'
    exit 1
  }
  Write-Ok 'Artefakty zbudowane (app\target, docs-service\target, workers\target)'
} else {
  Write-Ok 'Artefakty już zbudowane (start.cmd -Przebuduj buduje ponownie)'
}

if ($tryb -eq 'oidc' -and -not $BezRejestracji) {
  Write-Krok 'Rejestruję klientów eskarga i eskarga-workers w Keycloaku ID-users (idempotentnie)'
  if ((Invoke-Compose '--profile', 'id-users', 'run', '--rm', 'id-users-rejestracja') -ne 0) {
    Write-Blad 'Rejestracja w ID-users nie powiodła się – szczegóły powyżej (hasło administratora: KEYCLOAK_ADMIN_PASSWORD w infra\docker\.env, jak w dev\.env projektu id).'
    exit 1
  }
}

Write-Krok 'Buduję obrazy i uruchamiam usługi (pierwszy raz może to potrwać kilkanaście minut)'
$argumenty = @()
if ($Monitoring) { $argumenty += @('--profile', 'monitoring') }
$argumenty += @('up', '-d')
# Brakujące obrazy compose buduje sam; -Przebuduj wymusza budowę (nowy JAR workerów, nowa wersja Operaton).
if ($Przebuduj) { $argumenty += '--build' }
$env:DOCKER_BUILDKIT = '1'
if ((Invoke-Compose @argumenty) -ne 0) {
  Write-Blad 'docker compose up zakończył się błędem – szczegóły powyżej.'
  exit 1
}

Write-Krok 'Czekam na gotowość usług'
Wait-Usluga 'db' 180
Wait-Usluga 'flyway' 300 -Jednorazowa
Wait-Usluga 's3' 180
Wait-Usluga 's3-init' 180 -Jednorazowa
Wait-Usluga 'opensearch' 300
Wait-Usluga 'app' 900 -Uruchomiona
Write-Host '    (start WildFly z Operatonem i wdrożenie WAR-ów trwa zwykle 1–2 minuty)'
Wait-Http 'Aplikacja eSkarga' ("http://localhost:{0}/eskarga/health" -f (Get-Ustawienie 'PORT_APP' '8082')) 600
Wait-Usluga 'workers' 120 -Uruchomiona

Show-Adresy
if (-not $BezPrzegladarki) {
  Start-Process ("http://localhost:{0}/eskarga" -f (Get-Ustawienie 'PORT_APP' '8082'))
}
