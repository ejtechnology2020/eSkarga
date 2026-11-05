<#
.SYNOPSIS
  Usuwa kontenery i dane środowiska eSkarga (PostgreSQL, RustFS, OpenSearch, tokeny SoftHSM, TSA).
.PARAMETER Wymus
  Bez pytania o potwierdzenie.
.PARAMETER ZObrazami
  Usuwa także zbudowane lokalnie obrazy (WildFly+Operaton, workery, OpenSearch, SoftHSM, TSA).
.PARAMETER ZMaven
  Usuwa też wolumen eskarga-m2 z zależnościami Mavena (kolejny build pobiera je od nowa).
#>
[CmdletBinding()]
param([switch]$Wymus, [switch]$ZObrazami, [switch]$ZMaven)
. "$PSScriptRoot\wspolne.ps1"
Assert-Docker
Initialize-Env
if (-not $Wymus) {
  $odp = Read-Host 'Usunąć kontenery i WSZYSTKIE dane środowiska deweloperskiego eSkarga? Wpisz TAK, aby potwierdzić'
  if ($odp -ne 'TAK') { Write-Uwaga 'Anulowano – nic nie zmieniono.'; exit 0 }
}
Write-Krok 'Usuwam kontenery i wolumeny'
$argumenty = @('--profile', 'monitoring', '--profile', 'id-users', '--profile', 'oidc-local', 'down', '--volumes', '--remove-orphans')
if ($ZObrazami) { $argumenty += @('--rmi', 'local') }
if ((Invoke-Compose @argumenty) -ne 0) { exit 1 }
if ($ZMaven) {
  Write-Krok "Usuwam wolumen $Script:WolumenM2"
  Invoke-DockerCicho @('volume', 'rm', $Script:WolumenM2) | Out-Null
}
Write-Ok 'Środowisko usunięte (klienci eSkargi w Keycloaku ID-users zostają). Nowe uruchomienie: infra\windows\start.cmd'
