<#
.SYNOPSIS
  Rejestruje (ponownie) klientów eskarga i eskarga-workers w Keycloaku ID-users oraz nadaje role użytkownikom testowym.
  Idempotentne – drugie uruchomienie niczego nie duplikuje. Odpowiednik realm.cmd w projekcie id.
#>
[CmdletBinding()]
param()
. "$PSScriptRoot\wspolne.ps1"
Assert-Docker
Initialize-Env
Write-Krok 'Sprawdzam ID-users'
Assert-IdUsers
Write-Krok 'Rejestruję eSkargę w Keycloaku ID-users (infra\docker\keycloak\zarejestruj-w-id-users.sh)'
if ((Invoke-Compose '--profile', 'id-users', 'run', '--rm', 'id-users-rejestracja') -ne 0) { exit 1 }
Write-Ok 'Klienci i role eSkargi w ID-users aktualne'
