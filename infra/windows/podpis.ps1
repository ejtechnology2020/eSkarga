<#
.SYNOPSIS
  Podpis zarządzeń i postanowień na stacji dev (ADR 0008, D10) bez YubiKey: klucz testowy w PKCS#12 i aplikacja lokalna
  local-signer na http://127.0.0.1:17227 (CORS dla adresu eSkargi). Za pierwszym razem tworzy klucz, certyfikat i magazyn
  zaufania usługi dokumentów (certyfikat testowy + certyfikat TSA dev), wpisuje ustawienia do infra\docker\.env i prosi
  o restart aplikacji. Nic z tego nie trafia do Gita (infra\docker\.local).
.PARAMETER Pin
  Hasło/PIN klucza testowego (domyślnie 123456 – tylko dev).
.PARAMETER TylkoKlucze
  Przygotuj klucze i magazyn zaufania, nie uruchamiaj aplikacji lokalnej.
#>
[CmdletBinding()]
param([string]$Pin = '123456', [switch]$TylkoKlucze)
. "$PSScriptRoot\wspolne.ps1"
$ErrorActionPreference = 'Stop'

Write-Krok 'Sprawdzam JDK (keytool, java)'
if (-not ((Test-Polecenie 'java') -and (Test-Polecenie 'keytool'))) { Write-Blad 'Brak JDK 21 w PATH (java, keytool). winget install EclipseAdoptium.Temurin.21.JDK'; exit 1 }
$jar = Join-Path $Script:Repo 'local-signer\target\eskarga-local-signer.jar'
if (-not (Test-Path $jar)) { Write-Blad "Brak $jar – najpierw infra\windows\buduj.cmd (lub buduj.cmd -Szybko)"; exit 1 }

$dir = Join-Path $Script:Docker '.local\podpis'
New-Item -ItemType Directory -Force -Path $dir | Out-Null
$p12 = Join-Path $dir 'sedzia-test.p12'
$cer = Join-Path $dir 'sedzia-test.cer'
$tsaCer = Join-Path $dir 'tsa-dev.cer'
$trust = Join-Path $dir 'truststore.p12'

if (-not (Test-Path $p12)) {
  Write-Krok 'Tworzę klucz testowy sędziego (RSA 3072, certyfikat samopodpisany, dane fikcyjne)'
  & keytool -genkeypair -alias sedzia -keyalg RSA -keysize 3072 -sigalg SHA256withRSA -validity 730 -storetype PKCS12 -keystore $p12 -storepass $Pin -keypass $Pin `
    -dname 'CN=Sedzia Testowy, OU=Wydzial Weryfikacyjny (dev), O=Trybunal Konstytucyjny (dev), C=PL' -ext 'KeyUsage=digitalSignature,nonRepudiation' -ext 'ExtendedKeyUsage=emailProtection'
  if ($LASTEXITCODE -ne 0) { exit 1 }
  Write-Ok "klucz: $p12"
}
& keytool -exportcert -alias sedzia -keystore $p12 -storepass $Pin -storetype PKCS12 -rfc -file $cer | Out-Null

Write-Krok 'Pobieram certyfikat TSA dev (znacznik czasu PAdES-B-T)'
$portTsa = Get-Ustawienie 'PORT_TSA' '3180'
try {
  Invoke-WebRequest -Uri "http://localhost:$portTsa/tsa.crt" -OutFile $tsaCer -UseBasicParsing -TimeoutSec 10
  Write-Ok "certyfikat TSA: $tsaCer"
} catch {
  Write-Blad "TSA dev nie odpowiada na http://localhost:$portTsa/tsa.crt – uruchom środowisko (start.cmd; po zmianie obrazu TSA: start.cmd -Przebuduj). Bez TSA podpis z B-T nie powstanie."
  exit 1
}

Write-Krok 'Buduję magazyn zaufania usługi dokumentów (certyfikat testowy + TSA dev)'
if (Test-Path $trust) { Remove-Item $trust }
& keytool -importcert -noprompt -alias sedzia-test -file $cer -keystore $trust -storepass $Pin -storetype PKCS12 | Out-Null
& keytool -importcert -noprompt -alias tsa-dev -file $tsaCer -keystore $trust -storepass $Pin -storetype PKCS12 | Out-Null
Write-Ok "magazyn zaufania: $trust"

# ustawienia w infra\docker\.env (ścieżka w kontenerze – katalog .local\podpis jest montowany do /opt/eskarga/podpis)
$envLines = Get-Content $Script:PlikEnv -Encoding UTF8
$ustaw = @{ 'ESKARGA_DOCS_TRUSTSTORE' = '/opt/eskarga/podpis/truststore.p12'; 'ESKARGA_DOCS_TRUSTSTOREPASSWORD' = $Pin }
$zmieniono = $false
foreach ($k in $ustaw.Keys) {
  $wzor = "^\s*$k\s*="
  $nowa = "$k=$($ustaw[$k])"
  if ($envLines -match $wzor) {
    if (-not ($envLines -contains $nowa)) { $envLines = $envLines | ForEach-Object { if ($_ -match $wzor) { $nowa } else { $_ } }; $zmieniono = $true }
  } else { $envLines += $nowa; $zmieniono = $true }
}
if ($zmieniono) {
  Set-Content -Path $Script:PlikEnv -Value $envLines -Encoding UTF8
  Write-Uwaga 'Zmieniono infra\docker\.env (ESKARGA_DOCS_TRUSTSTORE, ESKARGA_DOCS_TRUSTSTOREPASSWORD) – uruchom ponownie aplikację: infra\windows\wdroz.cmd -TylkoApp'
} else { Write-Ok 'infra\docker\.env bez zmian' }

if ($TylkoKlucze) { exit 0 }

$url = Get-Ustawienie 'ESKARGA_PUBLIC_URL' 'http://localhost:8082/eskarga'
$origin = ([Uri]$url).GetLeftPart([UriPartial]::Authority)
Write-Krok "Uruchamiam aplikację lokalną do podpisu (klucz testowy, CORS dla $origin) – zostaw to okno otwarte, Ctrl+C kończy"
$env:ESKARGA_SIGNER_PIN = $Pin
$ErrorActionPreference = 'Continue'
& java -jar $jar --pkcs12 $p12 --origin $origin
exit $LASTEXITCODE
