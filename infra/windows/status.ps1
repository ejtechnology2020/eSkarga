<#
.SYNOPSIS
  Pokazuje stan kontenerów i punktów zdrowia środowiska eSkarga.
#>
[CmdletBinding()]
param()
. "$PSScriptRoot\wspolne.ps1"
Assert-Docker
Initialize-Env
Write-Krok 'Kontenery'
Invoke-Compose '--profile', 'monitoring', 'ps', '--all' | Out-Null

Write-Krok 'Punkty zdrowia'
$app = Get-Ustawienie 'PORT_APP' '8082'
$sprawdzenia = [ordered]@{
  'Aplikacja /eskarga/health' = "http://localhost:$app/eskarga/health"
  'Operaton Cockpit'          = "http://localhost:$app/operaton/app/cockpit/"
  'MinIO (S3)'                = ('http://localhost:{0}/minio/health/live' -f (Get-Ustawienie 'PORT_S3' '9100'))
  'OpenSearch'                = ('http://localhost:{0}/_cluster/health' -f (Get-Ustawienie 'PORT_OPENSEARCH' '9200'))
  'WireMock (zaślepki)'       = ('http://localhost:{0}/__admin/health' -f (Get-Ustawienie 'PORT_WIREMOCK' '8089'))
  'Keycloak ID-users'         = ('http://localhost:{0}/realms/id-users/.well-known/openid-configuration' -f (Get-PortIdUsers))
}
foreach ($nazwa in $sprawdzenia.Keys) {
  try {
    $odp = Invoke-WebRequest -Uri $sprawdzenia[$nazwa] -UseBasicParsing -TimeoutSec 5
    Write-Ok ("{0}: HTTP {1}" -f $nazwa, $odp.StatusCode)
  } catch {
    Write-Uwaga ("{0}: niedostępny ({1})" -f $nazwa, $sprawdzenia[$nazwa])
  }
}
