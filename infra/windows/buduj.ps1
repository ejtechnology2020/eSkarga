<#
.SYNOPSIS
  Build i testy eSkargi: mvn -B verify (JUnit 5, testy procesów Operaton, H2) albo samo pakowanie.
.DESCRIPTION
  Z lokalnym JDK 21 i Mavenem (np. winget install EclipseAdoptium.Temurin.21.JDK; winget install Apache.Maven)
  buduje bezpośrednio, w przeciwnym razie w kontenerze maven:3.9-eclipse-temurin-21 (zależności w wolumenie eskarga-m2).
.PARAMETER Szybko
  Pomija testy (mvn -B -DskipTests package).
.PARAMETER WDockerze
  Wymusza build w kontenerze Maven także wtedy, gdy lokalny Maven jest dostępny.
#>
[CmdletBinding()]
param([switch]$Szybko, [switch]$WDockerze)
. "$PSScriptRoot\wspolne.ps1"
if ($WDockerze -or -not ((Test-Polecenie 'mvn') -and (Test-Polecenie 'java'))) { Assert-Docker }
$kod = Build-Maven -Testy:(-not $Szybko) -WDockerze:$WDockerze
if ($kod -ne 0) { exit $kod }
Write-Ok 'Build zakończony: app\target\eskarga.war, docs-service\target\eskarga-docs-service.war, workers\target\eskarga-workers.jar (+lib), local-signer\target'
