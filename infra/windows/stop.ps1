<#
.SYNOPSIS
  Zatrzymuje kontenery środowiska eSkarga (dane w bazie, MinIO i OpenSearch zostają).
#>
[CmdletBinding()]
param()
. "$PSScriptRoot\wspolne.ps1"
Assert-Docker
Initialize-Env
Write-Krok 'Zatrzymuję środowisko eSkarga'
if ((Invoke-Compose '--profile', 'monitoring', 'stop') -ne 0) { exit 1 }
Write-Ok 'Zatrzymano. Ponowne uruchomienie: infra\windows\start.cmd'
