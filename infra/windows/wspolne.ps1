# Funkcje wspólne skryptów środowiska deweloperskiego eSkarga dla Windows (wzór: dev\windows projektu ID-users).
# Zgodne z Windows PowerShell 5.1 i PowerShell 7. Dołączane przez: . "$PSScriptRoot\wspolne.ps1"

$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }

$Script:Repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$Script:Docker = Join-Path $Script:Repo 'infra\docker'
$Script:PlikCompose = Join-Path $Script:Docker 'compose.yml'
$Script:PlikEnv = Join-Path $Script:Docker '.env'
$Script:PlikEnvPrzyklad = Join-Path $Script:Docker '.env.example'
$Script:ObrazMaven = 'maven:3.9-eclipse-temurin-21'
$Script:WolumenM2 = 'eskarga-m2'
# Artefakty montowane do kontenerów (app, docs-service) i kopiowane do obrazu (workers) – patrz infra\docker\compose.yml.
$Script:Artefakty = @('app\target\eskarga.war', 'docs-service\target\eskarga-docs-service.war', 'workers\target\eskarga-workers.jar', 'workers\target\lib')

function Write-Krok([string]$Tekst) { Write-Host ''; Write-Host "==> $Tekst" -ForegroundColor Cyan }
function Write-Ok([string]$Tekst) { Write-Host "    [OK] $Tekst" -ForegroundColor Green }
function Write-Uwaga([string]$Tekst) { Write-Host "    [UWAGA] $Tekst" -ForegroundColor Yellow }
function Write-Blad([string]$Tekst) { Write-Host "    [BŁĄD] $Tekst" -ForegroundColor Red }

function Test-Polecenie([string]$Nazwa) {
  return [bool](Get-Command $Nazwa -ErrorAction SilentlyContinue)
}

# Polecenia natywne uruchamiamy z ErrorActionPreference=Continue: w Windows PowerShell 5.1
# przekierowany stderr programu (np. postęp docker) przy "Stop" przerywałby skrypt.
function Invoke-DockerCicho([string[]]$Argumenty) {
  $ErrorActionPreference = 'Continue'
  & docker @Argumenty *> $null
  return ($LASTEXITCODE -eq 0)
}

function Get-DockerWyjscie([string[]]$Argumenty) {
  $ErrorActionPreference = 'Continue'
  $wynik = & docker @Argumenty 2> $null
  if ($LASTEXITCODE -ne 0) { return @() }
  return @($wynik)
}

function Get-Pole($Obiekt, [string]$Nazwa) {
  $p = $Obiekt.PSObject.Properties[$Nazwa]
  if ($p) { return $p.Value }
  return $null
}

# Docker Desktop zainstalowany i uruchomiony, Compose v2 dostępny.
function Assert-Docker {
  if (-not (Test-Polecenie 'docker')) {
    Write-Blad 'Nie znaleziono polecenia docker. Zainstaluj Docker Desktop: https://www.docker.com/products/docker-desktop/'
    throw 'Brak Docker Desktop'
  }
  if (-not (Invoke-DockerCicho @('info', '--format', '{{.ServerVersion}}'))) {
    Write-Blad 'Docker Desktop nie działa. Uruchom go (ikona wieloryba w zasobniku) i poczekaj, aż będzie gotowy.'
    throw 'Docker nie działa'
  }
  if (-not (Invoke-DockerCicho @('compose', 'version'))) {
    Write-Blad 'Brak Docker Compose v2 (polecenie "docker compose"). Zaktualizuj Docker Desktop.'
    throw 'Brak Docker Compose v2'
  }
  $pamiec = [int64]((Get-DockerWyjscie @('info', '--format', '{{.MemTotal}}')) -join '')
  $gb = [math]::Round($pamiec / 1GB, 1)
  if ($gb -lt 6) {
    Write-Uwaga "Docker ma przydzielone $gb GB pamięci. eSkarga obok ID-users potrzebuje łącznie co najmniej 8 GB (Docker Desktop > Settings > Resources lub .wslconfig)."
  } else {
    Write-Ok "Docker działa ($gb GB pamięci)"
  }
}

# infra\docker\.env z wartościami testowymi (kopia .env.example przy pierwszym uruchomieniu).
function Initialize-Env {
  if (-not (Test-Path $Script:PlikEnv)) {
    Copy-Item $Script:PlikEnvPrzyklad $Script:PlikEnv
    Write-Ok 'Utworzono infra\docker\.env z .env.example (wyłącznie wartości testowe – hasła zmień przed użyciem poza stacją dev)'
  }
  Sync-WersjeObrazow
}

# Wersje obrazów (klucze *_IMAGE) i Operatona (OPERATON_VERSION) są zarządzane przez repozytorium:
# istniejący .env dostaje wartości z .env.example; porty, hasła i pozostałe ustawienia zostają bez zmian.
function Sync-WersjeObrazow {
  $wzor = '^\s*([A-Z0-9_]+_IMAGE|OPERATON_VERSION|OPERATON_SHA256)\s*=\s*(.*?)\s*$'
  $oczekiwane = @{}
  foreach ($linia in Get-Content $Script:PlikEnvPrzyklad -Encoding UTF8) {
    if ($linia -match $wzor) { $oczekiwane[$Matches[1]] = $Matches[2] }
  }
  $zmiany = New-Object System.Collections.Generic.List[string]
  $linie = foreach ($linia in Get-Content $Script:PlikEnv -Encoding UTF8) {
    if ($linia -match $wzor) {
      $klucz = $Matches[1]
      $wartosc = $Matches[2]
      if ($oczekiwane.ContainsKey($klucz) -and $wartosc -ne $oczekiwane[$klucz]) {
        $zmiany.Add("$klucz`: $wartosc -> $($oczekiwane[$klucz])")
        "$klucz=$($oczekiwane[$klucz])"
        continue
      }
    }
    $linia
  }
  if ($zmiany.Count -gt 0) {
    # UTF-8 bez BOM – docker compose czyta plik .env dosłownie
    [System.IO.File]::WriteAllLines($Script:PlikEnv, [string[]]$linie, (New-Object System.Text.UTF8Encoding $false))
    Write-Uwaga 'Zaktualizowano wersje w infra\docker\.env zgodnie z .env.example:'
    foreach ($z in $zmiany) { Write-Host "      $z" -ForegroundColor Yellow }
  }
}

# Odczyt wartości z infra\docker\.env (porty, tryb, hasła testowe do komunikatów). Komentarz po wartości jest pomijany.
function Get-Ustawienie([string]$Nazwa, [string]$Domyslna) {
  if (Test-Path $Script:PlikEnv) {
    foreach ($linia in Get-Content $Script:PlikEnv -Encoding UTF8) {
      if ($linia -match "^\s*$([regex]::Escape($Nazwa))\s*=\s*(.*)$") {
        $wartosc = $Matches[1]
        if ($wartosc -match '^(.*?)\s+#.*$') { $wartosc = $Matches[1] }
        return $wartosc.Trim()
      }
    }
  }
  return $Domyslna
}

# Argument wiersza poleceń Windows w cudzysłowie, gdy zawiera spacje (np. C:\Users\Jan Kowalski\...).
function ConvertTo-ArgumentWiersza([string]$Argument) {
  if ($Argument -eq '') { return '""' }
  if ($Argument -notmatch '[\s"]') { return $Argument }
  $tekst = $Argument -replace '(\\*)"', '$1$1\"'
  $tekst = $tekst -replace '(\\+)$', '$1$1'
  return '"' + $tekst + '"'
}

# Spłaszcza argumenty do listy łańcuchów. Parametr ValueFromRemainingArguments bez typu: lista po przecinku
# (Invoke-Compose 'run', '--rm', 'x') trafia jako jedna zagnieżdżona tablica, a [string[]] skleiłby ją w jeden
# łańcuch ("run --rm x" jako jedna flaga – błąd "unknown flag" w Docker Compose).
function ConvertTo-ListaArgumentow($Argumenty) {
  $lista = New-Object System.Collections.Generic.List[string]
  foreach ($a in @($Argumenty)) {
    if ($null -eq $a) { continue }
    if ($a -is [System.Array]) { foreach ($b in (ConvertTo-ListaArgumentow $a)) { $lista.Add($b) } }
    else { $lista.Add([string]$a) }
  }
  return $lista.ToArray()
}

# Docker dziedziczy konsolę (Start-Process -NoNewWindow): budowanie obrazów (BuildKit) wymaga prawdziwej konsoli,
# a przekierowanie wyjścia do potoku PowerShella kończyło się błędem "failed to get console: The handle is invalid".
function Invoke-Docker {
  param([Parameter(ValueFromRemainingArguments = $true)]$Argumenty)
  $linia = ((ConvertTo-ListaArgumentow $Argumenty) | ForEach-Object { ConvertTo-ArgumentWiersza $_ }) -join ' '
  $proces = Start-Process -FilePath 'docker' -ArgumentList $linia -NoNewWindow -Wait -PassThru
  return $proces.ExitCode
}

# Wywołanie docker compose dla projektu eSkarga; zwraca kod wyjścia. Opcje globalne (np. --profile id-users) podaj na początku.
function Invoke-Compose {
  param([Parameter(ValueFromRemainingArguments = $true)]$Argumenty)
  $wszystkie = @('compose', '--env-file', $Script:PlikEnv, '-f', $Script:PlikCompose) + (ConvertTo-ListaArgumentow $Argumenty)
  return (Invoke-Docker @wszystkie)
}

# Stan usług z "docker compose ps" (format JSON – tablica albo jeden obiekt na linię, zależnie od wersji).
function Get-StanUslug {
  $wyjscie = Get-DockerWyjscie @('compose', '--env-file', $Script:PlikEnv, '-f', $Script:PlikCompose, 'ps', '--all', '--format', 'json')
  if ($wyjscie.Count -eq 0) { return @() }
  $tekst = ($wyjscie -join "`n").Trim()
  if ($tekst.StartsWith('[')) { return @($tekst | ConvertFrom-Json) }
  $lista = @()
  foreach ($linia in $wyjscie) { if ($linia.Trim()) { $lista += ($linia | ConvertFrom-Json) } }
  return $lista
}

function Get-Usluga([string]$Nazwa) {
  foreach ($u in Get-StanUslug) { if ((Get-Pole $u 'Service') -eq $Nazwa) { return $u } }
  return $null
}

# Czeka, aż usługa będzie zdrowa (healthcheck), działała (-Uruchomiona, usługi bez healthchecku)
# albo – dla zadań jednorazowych (-Jednorazowa) – zakończyła się kodem 0.
function Wait-Usluga([string]$Nazwa, [int]$LimitSekund = 600, [switch]$Jednorazowa, [switch]$Uruchomiona) {
  $start = Get-Date
  while (((Get-Date) - $start).TotalSeconds -lt $LimitSekund) {
    $u = Get-Usluga $Nazwa
    if ($u) {
      $stan = Get-Pole $u 'State'
      if ($Jednorazowa) {
        if ($stan -eq 'exited') {
          $kod = [int](Get-Pole $u 'ExitCode')
          if ($kod -eq 0) { Write-Host ''; Write-Ok "$Nazwa zakończona poprawnie"; return }
          Write-Blad "$Nazwa zakończyła się kodem $kod. Logi: infra\windows\logi.cmd $Nazwa"
          throw "Błąd usługi $Nazwa"
        }
      } elseif ($Uruchomiona -and $stan -eq 'running') {
        Write-Host ''
        Write-Ok "$Nazwa działa"
        return
      } elseif ((Get-Pole $u 'Health') -eq 'healthy') {
        Write-Host ''
        Write-Ok "$Nazwa gotowa"
        return
      } elseif ($stan -eq 'exited') {
        Write-Blad "$Nazwa zatrzymała się. Logi: infra\windows\logi.cmd $Nazwa"
        throw "Usługa $Nazwa nie działa"
      }
    }
    Start-Sleep -Seconds 5
    Write-Host '.' -NoNewline
  }
  Write-Host ''
  Write-Blad "Przekroczono czas oczekiwania ($LimitSekund s) na $Nazwa. Logi: infra\windows\logi.cmd $Nazwa"
  throw "Limit czasu: $Nazwa"
}

# Czy adres HTTP odpowiada kodem 2xx/3xx.
function Test-Http([string]$Url, [int]$LimitSekund = 5) {
  try {
    $odp = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec $LimitSekund
    return ($odp.StatusCode -ge 200 -and $odp.StatusCode -lt 400)
  } catch {
    return $false
  }
}

# Czeka, aż adres HTTP odpowie (np. /eskarga/health po wdrożeniu WAR-a przez WildFly).
function Wait-Http([string]$Nazwa, [string]$Url, [int]$LimitSekund = 300) {
  $start = Get-Date
  while (((Get-Date) - $start).TotalSeconds -lt $LimitSekund) {
    if (Test-Http $Url) { Write-Host ''; Write-Ok "$Nazwa odpowiada ($Url)"; return }
    Start-Sleep -Seconds 5
    Write-Host '.' -NoNewline
  }
  Write-Host ''
  Write-Blad "Przekroczono czas oczekiwania ($LimitSekund s) na $Nazwa ($Url). Logi: infra\windows\logi.cmd app"
  throw "Limit czasu: $Nazwa"
}

# Czy port TCP na localhost jest wolny.
function Test-PortWolny([int]$Port) {
  $nasluch = $null
  try {
    $nasluch = New-Object System.Net.Sockets.TcpListener([System.Net.IPAddress]::Loopback, $Port)
    $nasluch.Start()
    return $true
  } catch {
    return $false
  } finally {
    if ($nasluch) { $nasluch.Stop() }
  }
}

# Tryb bezpieczeństwa z .env: oidc (logowanie przez Keycloak ID-users) albo demo (bez ID-users, ADR 0004).
function Get-TrybBezpieczenstwa { return (Get-Ustawienie 'ESKARGA_SECURITY_MODE' 'oidc') }

# Port Keycloaka ID-users na hoście – z OIDC_PROVIDER_URI (np. http://host.docker.internal:8180/realms/id-users).
function Get-PortIdUsers {
  $uri = Get-Ustawienie 'OIDC_PROVIDER_URI' 'http://host.docker.internal:8180/realms/id-users'
  if ($uri -match ':(\d+)/realms/') { return $Matches[1] }
  return '8180'
}

# Środowisko ID-users (projekt id) musi działać: realm id-users pod http://localhost:8180.
function Assert-IdUsers {
  $url = "http://localhost:$(Get-PortIdUsers)/realms/id-users"
  try {
    $odp = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 5
    if ($odp.Content -match '"realm"\s*:\s*"id-users"') { Write-Ok "ID-users odpowiada ($url)"; return }
  } catch { }
  Write-Blad "ID-users nie odpowiada pod $url. Uruchom projekt id (dev\windows\start.cmd) albo ustaw ESKARGA_SECURITY_MODE=demo w infra\docker\.env (interfejs bez logowania, dane fikcyjne)."
  throw 'Brak ID-users'
}

# Czy zbudowane są wszystkie artefakty montowane do kontenerów.
function Test-Artefakty {
  foreach ($a in $Script:Artefakty) { if (-not (Test-Path (Join-Path $Script:Repo $a))) { return $false } }
  return $true
}

# Build Mavena: lokalnie (JDK 21 + mvn w PATH) albo w kontenerze maven:3.9-eclipse-temurin-21
# z zależnościami w wolumenie eskarga-m2 (nie trzeba instalować Javy). Zwraca kod wyjścia.
function Build-Maven([switch]$Testy, [switch]$WDockerze) {
  $cele = @('-B', '-DskipTests', 'package')
  if ($Testy) { $cele = @('-B', 'verify') }
  if (-not $WDockerze -and (Test-Polecenie 'mvn') -and (Test-Polecenie 'java')) {
    Write-Krok ('mvn ' + ($cele -join ' ') + ' (lokalny JDK)')
    Push-Location $Script:Repo
    try {
      $ErrorActionPreference = 'Continue'
      & mvn @cele
      return $LASTEXITCODE
    } finally {
      Pop-Location
    }
  }
  Write-Krok ('mvn ' + ($cele -join ' ') + " w kontenerze $Script:ObrazMaven (zależności w wolumenie $Script:WolumenM2; pierwszy raz kilka minut)")
  $argumenty = @('run', '--rm', '-v', ($Script:Repo + ':/src'), '-v', ($Script:WolumenM2 + ':/root/.m2'), '-w', '/src', $Script:ObrazMaven, 'mvn') + $cele
  return (Invoke-Docker @argumenty)
}

# Hasło fikcyjnych użytkowników testowych ID-users – z dev\.env projektu id, jeśli leży obok (..\id).
function Get-HasloIdUsers {
  $env = Join-Path (Split-Path $Script:Repo -Parent) 'id\dev\.env'
  if (Test-Path $env) {
    foreach ($linia in Get-Content $env -Encoding UTF8) {
      if ($linia -match '^\s*DEV_USER_PASSWORD\s*=\s*(.*?)\s*$') { return $Matches[1] }
    }
  }
  return 'DEV_USER_PASSWORD z dev\.env projektu id'
}

function Show-Adresy {
  $app = Get-Ustawienie 'PORT_APP' '8082'
  $admin = Get-Ustawienie 'PORT_APP_ADMIN' '9992'
  $s3c = Get-Ustawienie 'PORT_S3_CONSOLE' '9101'
  $os = Get-Ustawienie 'PORT_OPENSEARCH' '9200'
  $wm = Get-Ustawienie 'PORT_WIREMOCK' '8089'
  $kc = Get-PortIdUsers
  $tryb = Get-TrybBezpieczenstwa
  Write-Host ''
  Write-Host "Środowisko eSkarga działa (tryb $tryb):" -ForegroundColor Green
  Write-Host "  Aplikacja             http://localhost:$app/eskarga"
  Write-Host "  Stan (health)         http://localhost:$app/eskarga/health"
  Write-Host "  Operaton Cockpit      http://localhost:$app/operaton/"
  Write-Host "  Konsola WildFly       http://localhost:$admin/"
  Write-Host "  RustFS (akta, S3)     http://localhost:$s3c/   ($(Get-Ustawienie 'S3_ACCESS_KEY' 'eskarga-dev') / $(Get-Ustawienie 'S3_SECRET_KEY' '…'))"
  Write-Host "  OpenSearch            http://localhost:$os/"
  Write-Host "  WireMock (zaślepki)   http://localhost:$wm/__admin/"
  if ($tryb -eq 'oidc') {
    Write-Host "  Keycloak ID-users     http://localhost:$kc/realms/id-users/account"
    Write-Host ''
    Write-Host "Fikcyjne konta testowe ID-users (hasło: $(Get-HasloIdUsers)):" -ForegroundColor Green
    Write-Host '  anna.nowak        biuro podawcze (TK_BIURO_PODAWCZE)'
    Write-Host '  marek.zielinski   referent (TK_REFERENT)'
    Write-Host '  agnieszka.duda    asystent sędziego (TK_ASYSTENT)'
    Write-Host '  adam.kowal        sędzia i Prezes TK (TK_SEDZIA, TK_PREZES)'
    Write-Host '  beata.sowa        kierownik (TK_KIEROWNIK)'
    Write-Host '  katarzyna.lis     archiwista (TK_ARCHIWISTA)'
    Write-Host '  marcin.kaminski   administrator (TK_ADMIN)'
    Write-Host '  admin.wszystko    referent i kierownik'
    Write-Host '  Mapowanie ról zmienia ESKARGA_UZYTKOWNICY (infra\docker\keycloak\zarejestruj-w-id-users.sh).'
  } else {
    Write-Host ''
    Write-Host "Tryb demo: role i stany ekranów parametrami ?rola=, ?stan=, ?widok= (ADR 0004), data ESKARGA_DEMO_TODAY=$(Get-Ustawienie 'ESKARGA_DEMO_TODAY' '')." -ForegroundColor Green
  }
}
