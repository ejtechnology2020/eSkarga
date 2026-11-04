#!/usr/bin/env bash
# Rejestracja eSkargi w Keycloaku ID-users (realm id-users) – TYLKO środowisko deweloperskie (docelowo portal integratora PU-INT-01).
# Idempotentnie: klienci `eskarga` (WWW, poufny, code + PKCE S256, konto serwisowe) i `eskarga-workers` (M2M), role klienta,
# audience dla workerów, role dla fikcyjnych użytkowników testowych ID-users (dev/keycloak/uzytkownicy-dev.sh projektu id).
set -euo pipefail
KCADM=${KCADM:-/opt/keycloak/bin/kcadm.sh}
REALM=id-users
PUBLICZNY="${ESKARGA_PUBLIC_URL:-http://localhost:8082/eskarga}"
BACKCHANNEL="${ESKARGA_BACKCHANNEL_URL:-http://host.docker.internal:8082/eskarga/oidc/backchannel-logout}"

KC_URL="${KEYCLOAK_URL:-http://host.docker.internal:8180}"

# Sonda HTTP na wbudowanym /dev/tcp (obraz nie ma curl): co odpowiada pod KC_URL/realms/master? Keycloak zwraca
# JSON {"realm":"master",...}; HTML, przekierowanie lub brak połączenia oznaczają zły adres albo wyłączony ID-users.
sonda_http() {
  local hostport host port linia n=0
  hostport=${KC_URL#*://}; hostport=${hostport%%/*}
  host=${hostport%%:*}; port=${hostport##*:}; [[ "$host" == "$port" ]] && port=80
  echo "Sprawdzam Keycloak ID-users: $KC_URL/realms/master"
  if ! exec 3<>"/dev/tcp/$host/$port"; then
    echo "Brak połączenia z $host:$port – czy środowisko ID-users działa (projekt id: docker compose -f dev/docker-compose.yml ps)?" >&2
    return 1
  fi
  printf 'GET /realms/master HTTP/1.1\r\nHost: %s\r\nConnection: close\r\n\r\n' "$hostport" >&3
  while IFS= read -r -t 15 linia <&3; do
    printf '  | %s\n' "${linia%$'\r'}"; n=$((n+1)); (( n >= 12 )) && break
  done
  exec 3<&- 3>&-
}
sonda_http || exit 1

# Surowe żądanie o token (jak kcadm: grant_type=password, client_id=admin-cli). Wypisuje status i nagłówki; treść tylko
# gdy status ≠ 200 (odpowiedź 200 zawiera token). Hasła nie wypisuje.
urlenc() { local s=$1 i c out=""; for ((i=0; i<${#s}; i++)); do c=${s:i:1}; case $c in [a-zA-Z0-9.~_-]) out+=$c;; *) out+=$(printf '%%%02X' "'$c");; esac; done; printf '%s' "$out"; }
sonda_token() {
  local hostport host port linia status="" body="" n=0 dane
  hostport=${KC_URL#*://}; hostport=${hostport%%/*}
  host=${hostport%%:*}; port=${hostport##*:}; [[ "$host" == "$port" ]] && port=80
  dane="grant_type=password&client_id=admin-cli&username=$(urlenc "${KEYCLOAK_ADMIN_USER:-admin}")&password=$(urlenc "${KEYCLOAK_ADMIN_PASSWORD:?brak hasła administratora}")"
  echo "Sprawdzam endpoint tokenów: POST $KC_URL/realms/master/protocol/openid-connect/token"
  exec 3<>"/dev/tcp/$host/$port" || return 1
  # User-Agent jak w kcadm (Apache HttpClient) – Keycloak zapisuje z niego opis urządzenia sesji jako JSON.
  printf 'POST /realms/master/protocol/openid-connect/token HTTP/1.1\r\nHost: %s\r\nUser-Agent: Apache-HttpClient/4.5.14 (Java/21.0.4)\r\nContent-Type: application/x-www-form-urlencoded; charset=UTF-8\r\nContent-Length: %d\r\nAccept: application/json\r\nConnection: close\r\n\r\n%s' "$hostport" "${#dane}" "$dane" >&3
  while IFS= read -r -t 15 linia <&3 || [[ -n "$linia" ]]; do
    linia=${linia%$'\r'}
    if [[ -z "$status" ]]; then status=$linia; printf '  | %s\n' "$linia"; continue; fi
    if (( n == 0 )); then [[ -z "$linia" ]] && n=1 || printf '  | %s\n' "$linia"; continue; fi
    body+="$linia"$'\n'; [[ -n "$linia" ]] || break
  done
  exec 3<&- 3>&-
  if [[ "$status" != *" 200 "* ]]; then printf '  | treść odpowiedzi:\n%s\n' "${body:0:1500}"; fi
}
sonda_token || true

if ! "$KCADM" -x config credentials --server "$KC_URL" --realm master \
  --user "${KEYCLOAK_ADMIN_USER:-admin}" --password "${KEYCLOAK_ADMIN_PASSWORD:?brak hasła administratora}"; then
  cat >&2 <<KONIEC
Logowanie kcadm do $KC_URL nie powiodło się. Odpowiedź sondy powyżej pokazuje, kto odpowiada pod tym adresem.
- Keycloak ID-users słucha na porcie 8180 kontenera wildfly (projekt Compose id-users). Zamiast przez hosta można
  podłączyć się do jego sieci: w .env ustaw OIDC_PROVIDER_URI_ADMIN=http://wildfly:8180 i uruchom
  docker compose --profile id-users run --rm --network id-users_default id-users-rejestracja
- Hasło administratora: KEYCLOAK_ADMIN_USER/KEYCLOAK_ADMIN_PASSWORD z dev/.env projektu id.
KONIEC
  exit 1
fi

# Tylko wbudowane polecenia Basha – obraz Keycloaka (ubi-micro) nie gwarantuje coreutils ani grep.
pierwsza_linia() { local l=""; IFS= read -r l || true; printf '%s\n' "$l"; }
zawiera_linie() { local l; while IFS= read -r l; do [[ "$l" == "$1" ]] && return 0; done; return 1; }
client_id() { "$KCADM" get clients -r "$REALM" -q "clientId=$1" --fields id --format csv --noquotes | pierwsza_linia; }

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
if ! "$KCADM" get "clients/$WID/protocol-mappers/models" -r "$REALM" --fields name --format csv --noquotes | zawiera_linie "audience eskarga"; then
  "$KCADM" create "clients/$WID/protocol-mappers/models" -r "$REALM" -s "name=audience eskarga" -s protocol=openid-connect \
    -s protocolMapper=oidc-audience-mapper -s 'config."included.client.audience"=eskarga' -s 'config."access.token.claim"=true' >/dev/null
fi
"$KCADM" add-roles -r "$REALM" --uusername service-account-eskarga-workers --cclientid eskarga --rolename eskarga-worker --rolename eskarga-docs >/dev/null 2>&1 || true

# --- role eSkargi dla fikcyjnych użytkowników testowych ID-users (nadpisz zmienną ESKARGA_UZYTKOWNICY: login:ROLA,ROLA;...) --
MAPA="${ESKARGA_UZYTKOWNICY:-anna.nowak:TK_BIURO_PODAWCZE;marek.zielinski:TK_REFERENT;agnieszka.duda:TK_ASYSTENT;adam.kowal:TK_SEDZIA,TK_PREZES;beata.sowa:TK_KIEROWNIK;katarzyna.lis:TK_ARCHIWISTA;marcin.kaminski:TK_ADMIN;admin.wszystko:TK_REFERENT,TK_KIEROWNIK}"
IFS=';' read -ra WPISY <<<"$MAPA"
for wpis in "${WPISY[@]}"; do
  login="${wpis%%:*}"; role="${wpis#*:}"
  if [[ -z "$("$KCADM" get users -r "$REALM" -q "username=$login" -q exact=true --fields id --format csv --noquotes | pierwsza_linia)" ]]; then
    echo "pomijam $login – brak użytkownika w ID-users"; continue
  fi
  args=(); IFS=',' read -ra lista <<<"$role"; for r in "${lista[@]}"; do args+=(--rolename "$r"); done
  "$KCADM" add-roles -r "$REALM" --uusername "$login" --cclientid eskarga "${args[@]}" >/dev/null 2>&1 || true
  echo "$login → $role"
done
echo "eSkarga zarejestrowana w ID-users: $PUBLICZNY (callback $PUBLICZNY/callback, back-channel $BACKCHANNEL)"
