<#
.SYNOPSIS
  Pokazuje logi usług środowiska eSkarga.
.PARAMETER Usluga
  Nazwa usługi: app, workers, db, flyway, s3, s3-init, opensearch, softhsm, tsa, wiremock, id-users-rejestracja,
  prometheus, grafana. Bez nazwy – wszystkie usługi.
.PARAMETER Sledz
  Śledzi nowe wpisy (Ctrl+C kończy).
.EXAMPLE
  .\infra\windows\logi.ps1 app -Sledz
#>
[CmdletBinding()]
param(
  [Parameter(Position = 0)][string]$Usluga,
  [switch]$Sledz,
  [int]$Linie = 200
)
. "$PSScriptRoot\wspolne.ps1"
Assert-Docker
Initialize-Env
$argumenty = @('--profile', 'monitoring', '--profile', 'id-users', 'logs', '--tail', "$Linie")
if ($Sledz) { $argumenty += '--follow' }
if ($Usluga) { $argumenty += $Usluga }
Invoke-Compose @argumenty | Out-Null
