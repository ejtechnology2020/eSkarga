#!/usr/bin/env bash
# Rejestracja eSkargi w Keycloaku ID-users (realm id-users) – TYLKO środowisko deweloperskie (docelowo portal integratora PU-INT-01).
# Idempotentnie: klienci `eskarga` (WWW, poufny, code + PKCE S256, konto serwisowe) i `eskarga-workers` (M2M), role klienta,
# audience dla workerów, role dla fikcyjnych użytkowników testowych ID-users (dev/keycloak/uzytkownicy-dev.sh projektu id).
set -euo pipefail
KCADM=${KCADM:-/opt/keycloak/bin/kcadm.sh}
REALM=id-users
PUBLICZNY="${ESKARGA_PUBLIC_URL:-http://localhost:8082/eskarga}"
BACKCHANNEL="${ESKARGA_BACKCHANNEL_URL:-http://host.docker.internal:8082/eskarga/oidc/backchannel-logout}"

"$KCADM" config credentials --server "${KEYCLOAK_URL:-http://host.docker.internal:8180}" --realm master \
  --user "${KEYCLOAK_ADMIN_USER:-admin}" --password "${KEYCLOAK_ADMIN_PASSWORD:?brak hasła administratora}" >/dev/null

client_id() { "$KCADM" get clients -r "$REALM" -q "clientId=$1" --fields id --format csv --noquotes | head -n1; }

# --- klient WWW: eskarga ---------------------------------------------------------------------------------------------
ID=$(client_id eskarga)
if [[ -z "$ID" ]]; then
  "$KCADM" create clients -r "$REALM" -s clientId=eskarga -s "name=eSkarga – Repertorium Ts/Tw" -s enabled=true >/dev/null
  ID=$(client_id eskarga); echo "Utworzono klienta eskarga"
fi
"$KCADM" update "clients/$ID" -r "$REALM" -s publicClient=false -s standardFlowEnabled=true -s directAccessGrantsEnabled=false \
  -s serviceAccountsEnabled=true -s fullScopeAllowed=false -s "secret=${OIDC_CLIENT_SECRET:?brak OIDC_CLIENT_SECRET}" \
  -s "redirectUris=[\"$PUBLICZNY/callback\"]" -s "webOrigins=[\"${PUBLICZNY%/eskarga}\"]" \
  -s 'attributes."pkce.code.challenge.method"=S256' \
  -s "attributes.\"backchannel.logout.url\"=$BACKCHANNEL" -s 'attributes."backchannel.logout.session.required"=true' \
  -s "attributes.\"post.logout.redirect.uris\"=$PUBLICZNY/+" \
  -s 'attributes."acr.loa.map"={"haslo":1,"mfa":2,"wysoki":3}'
for ROLA in TK_REFERENT TK_ASYSTENT TK_SEDZIA TK_PREZES TK_KIEROWNIK TK_BIURO_PODAWCZE TK_ARCHIWISTA TK_ADMIN eskarga-worker eskarga-docs; do
  "$KCADM" create "clients/$ID/roles" -r "$REALM" -s "name=$ROLA" >/dev/null 2>&1 || true
done
# zakres `roles` (resource_access.eskarga.roles) i `acr` jako domyślne zakresy klienta
while IFS=, read -r SID NAZWA; do
  if [[ "$NAZWA" == "roles" || "$NAZWA" == "acr" || "$NAZWA" == "profile" || "$NAZWA" == "email" ]]; then
    "$KCADM" update "clients/$ID/default-client-scopes/$SID" -r "$REALM" >/dev/null 2>&1 || true
  fi
done < <("$KCADM" get client-scopes -r "$REALM" --fields id,name --format csv --noquotes)
# konto serwisowe aplikacji → usługa dokumentów
"$KCADM" add-roles -r "$REALM" --uusername service-account-eskarga --cclientid eskarga --rolename eskarga-docs >/dev/null 2>&1 || true

# --- klient M2M: eskarga-workers --------------------------------------------------------------------------------------
WID=$(client_id eskarga-workers)
if [[ -z "$WID" ]]; then
  "$KCADM" create clients -r "$REALM" -s clientId=eskarga-workers -s "name=eSkarga – workery integracji" -s enabled=true >/dev/null
  WID=$(client_id eskarga-workers); echo "Utworzono klienta eskarga-workers"
fi
"$KCADM" update "clients/$WID" -r "$REALM" -s publicClient=false -s standardFlowEnabled=false -s directAccessGrantsEnabled=false \
  -s serviceAccountsEnabled=true -s fullScopeAllowed=false -s "secret=${OIDC_WORKERS_SECRET:?brak OIDC_WORKERS_SECRET}"
# token workerów ma aud=eskarga (aplikacja i usługa dokumentów walidują aud/azp)
if ! "$KCADM" get "clients/$WID/protocol-mappers/models" -r "$REALM" --fields name --format csv --noquotes | grep -q "^audience eskarga$"; then
  "$KCADM" create "clients/$WID/protocol-mappers/models" -r "$REALM" -s "name=audience eskarga" -s protocol=openid-connect \
    -s protocolMapper=oidc-audience-mapper -s 'config."included.client.audience"=eskarga' -s 'config."access.token.claim"=true' >/dev/null
fi
"$KCADM" add-roles -r "$REALM" --uusername service-account-eskarga-workers --cclientid eskarga --rolename eskarga-worker --rolename eskarga-docs >/dev/null 2>&1 || true

# --- role eSkargi dla fikcyjnych użytkowników testowych ID-users (nadpisz zmienną ESKARGA_UZYTKOWNICY: login:ROLA,ROLA;...) --
MAPA="${ESKARGA_UZYTKOWNICY:-anna.nowak:TK_BIURO_PODAWCZE;marek.zielinski:TK_REFERENT;agnieszka.duda:TK_ASYSTENT;adam.kowal:TK_SEDZIA,TK_PREZES;beata.sowa:TK_KIEROWNIK;katarzyna.lis:TK_ARCHIWISTA;marcin.kaminski:TK_ADMIN;admin.wszystko:TK_REFERENT,TK_KIEROWNIK}"
IFS=';' read -ra WPISY <<<"$MAPA"
for wpis in "${WPISY[@]}"; do
  login="${wpis%%:*}"; role="${wpis#*:}"
  if [[ -z "$("$KCADM" get users -r "$REALM" -q "username=$login" -q exact=true --fields id --format csv --noquotes | head -n1)" ]]; then
    echo "pomijam $login – brak użytkownika w ID-users"; continue
  fi
  args=(); IFS=',' read -ra lista <<<"$role"; for r in "${lista[@]}"; do args+=(--rolename "$r"); done
  "$KCADM" add-roles -r "$REALM" --uusername "$login" --cclientid eskarga "${args[@]}" >/dev/null 2>&1 || true
  echo "$login → $role"
done
echo "eSkarga zarejestrowana w ID-users: $PUBLICZNY (callback $PUBLICZNY/callback, back-channel $BACKCHANNEL)"
