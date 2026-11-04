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
KC_HOSTPORT=${KC_URL#*://}; KC_HOSTPORT=${KC_HOSTPORT%%/*}
KC_HOST=${KC_HOSTPORT%%:*}; KC_PORT=${KC_HOSTPORT##*:}; [[ "$KC_HOST" == "$KC_PORT" ]] && KC_PORT=80

# Obraz Keycloaka (ubi-micro) nie ma curl ani coreutils – HTTP przez wbudowane /dev/tcp Basha.
# http_zadanie METODA ŚCIEŻKA [TREŚĆ] → ustawia ODP_STATUS (linia statusu), ODP_NAGLOWKI, ODP_TRESC.
http_zadanie() {
  local metoda=$1 sciezka=$2 tresc=${3:-} linia naglowki=1
  ODP_STATUS=""; ODP_NAGLOWKI=""; ODP_TRESC=""
  exec 3<>"/dev/tcp/$KC_HOST/$KC_PORT" || return 1
  # User-Agent jak w kcadm (Apache HttpClient) – Keycloak zapisuje z niego opis urządzenia sesji.
  printf '%s %s HTTP/1.1\r\nHost: %s\r\nUser-Agent: Apache-HttpClient/4.5.14 (Java/21.0.4)\r\nAccept: application/json\r\nContent-Type: application/x-www-form-urlencoded; charset=UTF-8\r\nContent-Length: %d\r\nConnection: close\r\n\r\n%s' \
    "$metoda" "$sciezka" "$KC_HOSTPORT" "${#tresc}" "$tresc" >&3
  while IFS= read -r -t 15 linia <&3 || [[ -n "$linia" ]]; do
    linia=${linia%$'\r'}
    if [[ -z "$ODP_STATUS" ]]; then ODP_STATUS=$linia; continue; fi
    if (( naglowki )); then [[ -z "$linia" ]] && naglowki=0 || ODP_NAGLOWKI+="$linia"$'\n'; continue; fi
    ODP_TRESC+="$linia"$'\n'; [[ -n "$linia" ]] || break
  done
  exec 3<&- 3>&-
}
pokaz_odpowiedz() { printf '  | %s\n' "$ODP_STATUS"; local l; while IFS= read -r l; do printf '  | %s\n' "$l"; done <<<"${ODP_NAGLOWKI%$'\n'}"; }
urlenc() { local s=$1 i c out=""; for ((i=0; i<${#s}; i++)); do c=${s:i:1}; case $c in [a-zA-Z0-9.~_-]) out+=$c;; *) out+=$(printf '%%%02X' "'$c");; esac; done; printf '%s' "$out"; }

echo "Sprawdzam Keycloak ID-users: $KC_URL/realms/master"
if ! http_zadanie GET /realms/master; then
  echo "Brak połączenia z $KC_HOST:$KC_PORT – czy środowisko ID-users działa (projekt id: docker compose -f dev/docker-compose.yml ps)?" >&2
  exit 1
fi
pokaz_odpowiedz
if [[ "$ODP_STATUS" != *" 200 "* || "$ODP_TRESC" != *'"realm":"master"'* ]]; then
  echo "Pod $KC_URL nie odpowiada Keycloak (oczekiwano JSON z \"realm\":\"master\"). Treść: ${ODP_TRESC:0:600}" >&2
  echo "Alternatywa: w .env OIDC_PROVIDER_URI_ADMIN=http://wildfly:8180 i docker compose --profile id-users run --rm --network id-users_default id-users-rejestracja" >&2
  exit 1
fi

# Token administratora pobierany bezpośrednio (password grant admin-cli, jak w kcadm) i przekazywany do kcadm opcją --token.
# Logowanie `kcadm config credentials` w tym samym Keycloaku kończy się błędem serwera „Cannot parse the JSON [unknown_error]”,
# choć identyczne żądanie z Basha zwraca 200 – przyczyna do wyjaśnienia w logu Keycloaka ID-users (docs/adr, notatki T-005).
TOKEN=""; TOKEN_CZAS=0
pobierz_token() {
  local dane="grant_type=password&client_id=admin-cli&username=$(urlenc "${KEYCLOAK_ADMIN_USER:-admin}")&password=$(urlenc "${KEYCLOAK_ADMIN_PASSWORD:?brak hasła administratora}")"
  http_zadanie POST /realms/master/protocol/openid-connect/token "$dane" || { echo "Brak połączenia z $KC_HOST:$KC_PORT" >&2; return 1; }
  if [[ "$ODP_STATUS" != *" 200 "* ]]; then
    echo "Token administratora: odmowa." >&2; pokaz_odpowiedz >&2; printf '  | treść: %s\n' "${ODP_TRESC:0:1500}" >&2
    echo "Sprawdź KEYCLOAK_ADMIN_USER/KEYCLOAK_ADMIN_PASSWORD (dev/.env projektu id) i log: docker compose -f dev/docker-compose.yml logs keycloak --tail 150" >&2
    return 1
  fi
  [[ "$ODP_TRESC" =~ \"access_token\":\"([^\"]+)\" ]] || { echo "Odpowiedź 200 bez access_token: ${ODP_TRESC:0:300}" >&2; return 1; }
  TOKEN=${BASH_REMATCH[1]}; TOKEN_CZAS=$SECONDS
}
echo "Pobieram token administratora: POST $KC_URL/realms/master/protocol/openid-connect/token"
pobierz_token || exit 1
pokaz_odpowiedz
# Token realm master żyje domyślnie 60 s – odświeżany przed każdym wywołaniem kcadm, gdy ma ponad 30 s.
kcadm() {
  if (( SECONDS - TOKEN_CZAS > 30 )); then pobierz_token >/dev/null || return 1; fi
  "$KCADM" "$@" --server "$KC_URL" --token "$TOKEN"
}

# Tylko wbudowane polecenia Basha – obraz Keycloaka (ubi-micro) nie gwarantuje coreutils ani grep.
pierwsza_linia() { local l=""; IFS= read -r l || true; printf '%s\n' "$l"; }
zawiera_linie() { local l; while IFS= read -r l; do [[ "$l" == "$1" ]] && return 0; done; return 1; }
client_id() { kcadm get clients -r "$REALM" -q "clientId=$1" --fields id --format csv --noquotes | pierwsza_linia; }

# --- klient WWW: eskarga ---------------------------------------------------------------------------------------------
ID=$(client_id eskarga)
if [[ -z "$ID" ]]; then
  kcadm create clients -r "$REALM" -s clientId=eskarga -s "name=eSkarga – Repertorium Ts/Tw" -s enabled=true >/dev/null
  ID=$(client_id eskarga); echo "Utworzono klienta eskarga"
fi
kcadm update "clients/$ID" -r "$REALM" -s publicClient=false -s standardFlowEnabled=true -s directAccessGrantsEnabled=false \
  -s serviceAccountsEnabled=true -s fullScopeAllowed=false -s "secret=${OIDC_CLIENT_SECRET:?brak OIDC_CLIENT_SECRET}" \
  -s "redirectUris=[\"$PUBLICZNY/callback\"]" -s "webOrigins=[\"${PUBLICZNY%/eskarga}\"]" \
  -s 'attributes."pkce.code.challenge.method"=S256' \
  -s "attributes.\"backchannel.logout.url\"=$BACKCHANNEL" -s 'attributes."backchannel.logout.session.required"=true' \
  -s "attributes.\"post.logout.redirect.uris\"=$PUBLICZNY/+" \
  -s 'attributes."acr.loa.map"={"haslo":1,"mfa":2,"wysoki":3}'
for ROLA in TK_REFERENT TK_ASYSTENT TK_SEDZIA TK_PREZES TK_KIEROWNIK TK_BIURO_PODAWCZE TK_ARCHIWISTA TK_ADMIN eskarga-worker eskarga-docs; do
  kcadm create "clients/$ID/roles" -r "$REALM" -s "name=$ROLA" >/dev/null 2>&1 || true
done
# zakres `roles` (resource_access.eskarga.roles) i `acr` jako domyślne zakresy klienta
while IFS=, read -r SID NAZWA; do
  if [[ "$NAZWA" == "roles" || "$NAZWA" == "acr" || "$NAZWA" == "profile" || "$NAZWA" == "email" ]]; then
    kcadm update "clients/$ID/default-client-scopes/$SID" -r "$REALM" >/dev/null 2>&1 || true
  fi
done < <(kcadm get client-scopes -r "$REALM" --fields id,name --format csv --noquotes)
# konto serwisowe aplikacji → usługa dokumentów
kcadm add-roles -r "$REALM" --uusername service-account-eskarga --cclientid eskarga --rolename eskarga-docs >/dev/null 2>&1 || true

# --- klient M2M: eskarga-workers --------------------------------------------------------------------------------------
WID=$(client_id eskarga-workers)
if [[ -z "$WID" ]]; then
  kcadm create clients -r "$REALM" -s clientId=eskarga-workers -s "name=eSkarga – workery integracji" -s enabled=true >/dev/null
  WID=$(client_id eskarga-workers); echo "Utworzono klienta eskarga-workers"
fi
kcadm update "clients/$WID" -r "$REALM" -s publicClient=false -s standardFlowEnabled=false -s directAccessGrantsEnabled=false \
  -s serviceAccountsEnabled=true -s fullScopeAllowed=false -s "secret=${OIDC_WORKERS_SECRET:?brak OIDC_WORKERS_SECRET}"
# token workerów ma aud=eskarga (aplikacja i usługa dokumentów walidują aud/azp)
if ! kcadm get "clients/$WID/protocol-mappers/models" -r "$REALM" --fields name --format csv --noquotes | zawiera_linie "audience eskarga"; then
  kcadm create "clients/$WID/protocol-mappers/models" -r "$REALM" -s "name=audience eskarga" -s protocol=openid-connect \
    -s protocolMapper=oidc-audience-mapper -s 'config."included.client.audience"=eskarga' -s 'config."access.token.claim"=true' >/dev/null
fi
kcadm add-roles -r "$REALM" --uusername service-account-eskarga-workers --cclientid eskarga --rolename eskarga-worker --rolename eskarga-docs >/dev/null 2>&1 || true

# --- role eSkargi dla fikcyjnych użytkowników testowych ID-users (nadpisz zmienną ESKARGA_UZYTKOWNICY: login:ROLA,ROLA;...) --
MAPA="${ESKARGA_UZYTKOWNICY:-anna.nowak:TK_BIURO_PODAWCZE;marek.zielinski:TK_REFERENT;agnieszka.duda:TK_ASYSTENT;adam.kowal:TK_SEDZIA,TK_PREZES;beata.sowa:TK_KIEROWNIK;katarzyna.lis:TK_ARCHIWISTA;marcin.kaminski:TK_ADMIN;admin.wszystko:TK_REFERENT,TK_KIEROWNIK}"
IFS=';' read -ra WPISY <<<"$MAPA"
for wpis in "${WPISY[@]}"; do
  login="${wpis%%:*}"; role="${wpis#*:}"
  if [[ -z "$(kcadm get users -r "$REALM" -q "username=$login" -q exact=true --fields id --format csv --noquotes | pierwsza_linia)" ]]; then
    echo "pomijam $login – brak użytkownika w ID-users"; continue
  fi
  args=(); IFS=',' read -ra lista <<<"$role"; for r in "${lista[@]}"; do args+=(--rolename "$r"); done
  kcadm add-roles -r "$REALM" --uusername "$login" --cclientid eskarga "${args[@]}" >/dev/null 2>&1 || true
  echo "$login → $role"
done
echo "eSkarga zarejestrowana w ID-users: $PUBLICZNY (callback $PUBLICZNY/callback, back-channel $BACKCHANNEL)"
