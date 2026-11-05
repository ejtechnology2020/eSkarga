<#
.SYNOPSIS
  Przebudowuje artefakty Mavenem i wdraża je ponownie po zmianach w kodzie.
.DESCRIPTION
  WAR-y aplikacji i usługi dokumentów są montowane z app\target i docs-service\target – po buildzie
  wystarczy restart WildFly. Workery są kopiowane do obrazu – ich obraz jest budowany ponownie.
.PARAMETER TylkoApp
  Tylko WildFly (aplikacja i usługa dokumentów), bez workerów.
.PARAMETER Testy
  Buduje z testami (mvn verify) zamiast samego pakowania.
#>
[CmdletBinding()]
param([switch]$TylkoApp, [switch]$Testy)
. "$PSScriptRoot\wspolne.ps1"
Assert-Docker
Initialize-Env
if ((Build-Maven -Testy:$Testy) -ne 0) { Write-Blad 'Build Mavena zakończył się błędem – szczegóły powyżej.'; exit 1 }
$env:DOCKER_BUILDKIT = '1'
Write-Krok 'Restartuję WildFly (eskarga.war, eskarga-docs-service.war)'
if ((Invoke-Compose 'restart', 'app') -ne 0) { exit 1 }
if (-not $TylkoApp) {
  Write-Krok 'Przebudowuję i uruchamiam workery'
  if ((Invoke-Compose 'up', '-d', '--build', 'workers') -ne 0) { exit 1 }
}
Wait-Usluga 'app' 900
Wait-Http 'Aplikacja eSkarga' ("http://localhost:{0}/eskarga/health" -f (Get-Ustawienie 'PORT_APP' '8082')) 300
if (-not $TylkoApp) { Wait-Usluga 'workers' 120 -Uruchomiona }
Show-Adresy
