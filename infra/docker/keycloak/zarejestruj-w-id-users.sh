#!/usr/bin/env bash
# Rejestracja eSkargi w Keycloaku ID-users (realm id-users) – TYLKO środowisko deweloperskie (docelowo portal integratora PU-INT-01).
# Idempotentnie: klienci `eskarga` (WWW, poufny, code + PKCE S256, konto serwisowe) i `eskarga-workers` (M2M), role klienta,
# audience dla workerów, role dla fikcyjnych użytkowników testowych ID-users (dev/keycloak/uzytkownicy-dev.sh projektu id).
#
# Wyłącznie Admin REST API przez wbudowane /dev/tcp Basha (obraz Keycloaka ubi-micro nie ma curl ani coreutils).
# Nie używa kcadm: w Keycloaku ID-users każde żądanie kcadm (Apache HttpClient) kończy się błędem serwera
# „Cannot parse the JSON [unknown_error]”, podczas gdy identyczne żądania z Basha przechodzą (notatki T-005, infra/docker/README.md).
set -euo pipefail
REALM=id-users
PUBLICZNY="${ESKARGA_PUBLIC_URL:-http://localhost:8082/eskarga}"
BACKCHANNEL="${ESKARGA_BACKCHANNEL_URL:-http://host.docker.internal:8082/eskarga/oidc/backchannel-logout}"
KC_URL="${KEYCLOAK_URL:-http://host.docker.internal:8180}"
KC_HOSTPORT=${KC_URL#*://}; KC_HOSTPORT=${KC_HOSTPORT%%/*}
KC_HOST=${KC_HOSTPORT%%:*}; KC_PORT=${KC_HOSTPORT##*:}; [[ "$KC_HOST" == "$KC_PORT" ]] && KC_PORT=80
ADMIN="/admin/realms/$REALM"

# --- HTTP ---------------------------------------------------------------------------------------------------------------
# http_zadanie METODA ŚCIEŻKA [TREŚĆ] [CONTENT-TYPE] → ODP_STATUS (linia statusu), ODP_NAGLOWKI, ODP_TRESC. Token w $TOKEN.
TOKEN=""; TOKEN_CZAS=0
http_zadanie() {
  local metoda=$1 sciezka=$2 tresc=${3:-} typ=${4:-application/x-www-form-urlencoded} linia naglowki=1 auth="" bajty
  ODP_STATUS=""; ODP_NAGLOWKI=""; ODP_TRESC=""
  [[ -n "$TOKEN" ]] && auth=$'Authorization: Bearer '"$TOKEN"$'\r\n'
  bajty=$(LC_ALL=C; printf '%d' "${#tresc}")      # Content-Length w bajtach, nie znakach (UTF-8 w nazwach)
  exec 3<>"/dev/tcp/$KC_HOST/$KC_PORT" || return 1
  printf '%s %s HTTP/1.1\r\nHost: %s\r\n%sAccept: application/json\r\nContent-Type: %s\r\nContent-Length: %d\r\nConnection: close\r\n\r\n%s' \
    "$metoda" "$sciezka" "$KC_HOSTPORT" "$auth" "$typ" "$bajty" "$tresc" >&3
  while IFS= read -r -t 20 linia <&3 || [[ -n "$linia" ]]; do
    linia=${linia%$'\r'}
    if [[ -z "$ODP_STATUS" ]]; then ODP_STATUS=$linia; continue; fi
    if (( naglowki )); then [[ -z "$linia" ]] && naglowki=0 || ODP_NAGLOWKI+="$linia"$'\n'; continue; fi
    ODP_TRESC+="$linia"$'\n'; [[ -n "$linia" ]] || break
  done
  exec 3<&- 3>&-
}
pokaz_odpowiedz() { printf '  | %s\n' "$ODP_STATUS"; local l; while IFS= read -r l; do printf '  | %s\n' "$l"; done <<<"${ODP_NAGLOWKI%$'\n'}"; }
urlenc() { local LC_ALL=C s=$1 i c out=""; for ((i=0; i<${#s}; i++)); do c=${s:i:1}; case $c in [a-zA-Z0-9.~_-]) out+=$c;; *) out+=$(printf '%%%02X' "'$c");; esac; done; printf '%s' "$out"; }
json_str() { local s=${1//\\/\\\\}; s=${s//\"/\\\"}; printf '"%s"' "$s"; }     # łańcuch JSON z escapowaniem \ i "
status_2xx() { [[ "$ODP_STATUS" == *" 2"[0-9][0-9]" "* || "$ODP_STATUS" == *" 2"[0-9][0-9] ]]; }
# pierwsza wartość pola "klucz":"..." w ODP_TRESC (puste, gdy brak)
json_pole() { if [[ "$ODP_TRESC" =~ \"$1\":\"([^\"]*)\" ]]; then printf '%s' "${BASH_REMATCH[1]}"; fi; }

# admin METODA ŚCIEŻKA-WZGL [JSON] – wywołanie Admin REST API w realm id-users; przerywa skrypt przy statusie innym niż 2xx,
# chyba że podano dozwolone statusy w DOZWOLONE (np. "409" dla „już istnieje").
DOZWOLONE=""
admin() {
  local metoda=$1 sciezka=$2 json=${3:-}
  if (( SECONDS - TOKEN_CZAS > 30 )); then pobierz_token >/dev/null; fi
  http_zadanie "$metoda" "$ADMIN$sciezka" "$json" application/json || { echo "Brak połączenia z $KC_HOST:$KC_PORT" >&2; exit 1; }
  if ! status_2xx && [[ -z "$DOZWOLONE" || "$ODP_STATUS" != *" $DOZWOLONE "* ]]; then
    echo "Admin REST API: $metoda $ADMIN$sciezka → $ODP_STATUS" >&2; pokaz_odpowiedz >&2
    printf '  | treść: %s\n' "${ODP_TRESC:0:1500}" >&2
    exit 1
  fi
}

# --- Keycloak i token administratora -------------------------------------------------------------------------------------
echo "Sprawdzam Keycloak ID-users: $KC_URL/realms/master"
if ! http_zadanie GET /realms/master; then
  echo "Brak połączenia z $KC_HOST:$KC_PORT – czy środowisko ID-users działa (projekt id: docker compose -f dev/docker-compose.yml ps)?" >&2
  exit 1
fi
pokaz_odpowiedz
if [[ "$ODP_STATUS" != *" 200 "* || "$ODP_TRESC" != *'"realm"'*'"master"'* ]]; then
  echo "Pod $KC_URL nie odpowiada Keycloak (oczekiwano JSON z \"realm\":\"master\"). Treść: ${ODP_TRESC:0:600}" >&2
  echo "Alternatywa: w .env OIDC_PROVIDER_URI_ADMIN=http://wildfly:8180 i docker compose --profile id-users run --rm --network id-users_default id-users-rejestracja" >&2
  exit 1
fi

# Token realm master (admin-cli, password grant) żyje domyślnie 60 s – odświeżany, gdy ma ponad 30 s.
pobierz_token() {
  local dane="grant_type=password&client_id=admin-cli&username=$(urlenc "${KEYCLOAK_ADMIN_USER:-admin}")&password=$(urlenc "${KEYCLOAK_ADMIN_PASSWORD:?brak hasła administratora}")"
  TOKEN=""
  http_zadanie POST /realms/master/protocol/openid-connect/token "$dane" || { echo "Brak połączenia z $KC_HOST:$KC_PORT" >&2; exit 1; }
  if [[ "$ODP_STATUS" != *" 200 "* ]]; then
    echo "Token administratora: odmowa." >&2; pokaz_odpowiedz >&2; printf '  | treść: %s\n' "${ODP_TRESC:0:1500}" >&2
    echo "Sprawdź KEYCLOAK_ADMIN_USER/KEYCLOAK_ADMIN_PASSWORD (dev/.env projektu id) i log: docker compose -f dev/docker-compose.yml logs keycloak --tail 150" >&2
    exit 1
  fi
  TOKEN=$(json_pole access_token)
  [[ -n "$TOKEN" ]] || { echo "Odpowiedź 200 bez access_token: ${ODP_TRESC:0:300}" >&2; exit 1; }
  TOKEN_CZAS=$SECONDS
}
echo "Pobieram token administratora: POST $KC_URL/realms/master/protocol/openid-connect/token"
pobierz_token
pokaz_odpowiedz

# --- pomocnicze operacje Admin REST API ----------------------------------------------------------------------------------
client_uuid() { admin GET "/clients?clientId=$(urlenc "$1")"; json_pole id; }
utworz_klienta() {   # clientId nazwa → UUID (tworzy, jeśli brak)
  local id; id=$(client_uuid "$1")
  if [[ -z "$id" ]]; then
    admin POST /clients "{\"clientId\":$(json_str "$1"),\"name\":$(json_str "$2"),\"enabled\":true}"
    id=$(client_uuid "$1"); echo "Utworzono klienta $1" >&2   # stdout tej funkcji to UUID
  fi
  [[ -n "$id" ]] || { echo "Nie udało się odczytać UUID klienta $1" >&2; exit 1; }
  printf '%s' "$id"
}
rola_klienta() {     # UUID-klienta nazwa-roli – tworzy, jeśli brak (409 = istnieje)
  DOZWOLONE=409 admin POST "/clients/$1/roles" "{\"name\":$(json_str "$2")}"
}
rola_id() {          # UUID-klienta nazwa-roli → id roli
  admin GET "/clients/$1/roles/$(urlenc "$2")"; json_pole id
}
nadaj_role() {       # UUID-użytkownika UUID-klienta rola... – mapowanie ról klienta (idempotentne)
  local uid=$1 cid=$2 r rid lista=""; shift 2
  for r in "$@"; do rid=$(rola_id "$cid" "$r"); [[ -n "$rid" ]] || { echo "Brak roli $r" >&2; exit 1; }; lista+="${lista:+,}{\"id\":\"$rid\",\"name\":$(json_str "$r")}"; done
  admin POST "/users/$uid/role-mappings/clients/$cid" "[$lista]"
}
service_account_uid() { admin GET "/clients/$1/service-account-user"; json_pole id; }
user_uid() { admin GET "/users?username=$(urlenc "$1")&exact=true"; json_pole id; }

# --- klient WWW: eskarga ---------------------------------------------------------------------------------------------
ID=$(utworz_klienta eskarga "eSkarga – Repertorium Ts/Tw")
admin PUT "/clients/$ID" "{
  \"clientId\":\"eskarga\",\"enabled\":true,\"publicClient\":false,\"standardFlowEnabled\":true,\"directAccessGrantsEnabled\":false,
  \"serviceAccountsEnabled\":true,\"fullScopeAllowed\":false,\"secret\":$(json_str "${OIDC_CLIENT_SECRET:?brak OIDC_CLIENT_SECRET}"),
  \"redirectUris\":[$(json_str "$PUBLICZNY/callback")],\"webOrigins\":[$(json_str "${PUBLICZNY%/eskarga}")],
  \"attributes\":{\"pkce.code.challenge.method\":\"S256\",\"backchannel.logout.url\":$(json_str "$BACKCHANNEL"),
    \"backchannel.logout.session.required\":\"true\",\"post.logout.redirect.uris\":$(json_str "$PUBLICZNY/+"),
    \"acr.loa.map\":\"{\\\"haslo\\\":1,\\\"mfa\\\":2,\\\"wysoki\\\":3}\"}
}"
for ROLA in TK_REFERENT TK_ASYSTENT TK_SEDZIA TK_PREZES TK_KIEROWNIK TK_BIURO_PODAWCZE TK_ARCHIWISTA TK_ADMIN eskarga-worker eskarga-docs; do
  rola_klienta "$ID" "$ROLA"
done
# zakresy `roles` (resource_access.eskarga.roles), `acr`, `profile`, `email` jako domyślne zakresy klienta
admin GET /client-scopes
RESZTA=$ODP_TRESC
# mappery wewnątrz zakresów też mają pary "id"/"name" (np. mapper `email` w zakresie `email`) – usuń tablice protocolMappers
while [[ "$RESZTA" == *'"protocolMappers":['* ]]; do RESZTA="${RESZTA%%'"protocolMappers":['*}${RESZTA#*'"protocolMappers":['*]}"; done
while [[ "$RESZTA" =~ \"id\":\"([^\"]+)\",\"name\":\"([^\"]+)\" ]]; do
  SID=${BASH_REMATCH[1]}; NAZWA=${BASH_REMATCH[2]}; RESZTA=${RESZTA#*"${BASH_REMATCH[0]}"}
  case $NAZWA in roles|acr|profile|email) admin PUT "/clients/$ID/default-client-scopes/$SID";; esac
done
# konto serwisowe aplikacji → usługa dokumentów
SA=$(service_account_uid "$ID"); [[ -n "$SA" ]] && nadaj_role "$SA" "$ID" eskarga-docs

# --- klient M2M: eskarga-workers --------------------------------------------------------------------------------------
WID=$(utworz_klienta eskarga-workers "eSkarga – workery integracji")
admin PUT "/clients/$WID" "{
  \"clientId\":\"eskarga-workers\",\"enabled\":true,\"publicClient\":false,\"standardFlowEnabled\":false,\"directAccessGrantsEnabled\":false,
  \"serviceAccountsEnabled\":true,\"fullScopeAllowed\":false,\"secret\":$(json_str "${OIDC_WORKERS_SECRET:?brak OIDC_WORKERS_SECRET}")
}"
# token workerów ma aud=eskarga (aplikacja i usługa dokumentów walidują aud/azp)
admin GET "/clients/$WID/protocol-mappers/models"
if [[ "$ODP_TRESC" != *'"name":"audience eskarga"'* ]]; then
  admin POST "/clients/$WID/protocol-mappers/models" '{"name":"audience eskarga","protocol":"openid-connect","protocolMapper":"oidc-audience-mapper",
    "config":{"included.client.audience":"eskarga","access.token.claim":"true","id.token.claim":"false"}}'
fi
SAW=$(service_account_uid "$WID"); [[ -n "$SAW" ]] && nadaj_role "$SAW" "$ID" eskarga-worker eskarga-docs

# --- role eSkargi dla fikcyjnych użytkowników testowych ID-users (nadpisz zmienną ESKARGA_UZYTKOWNICY: login:ROLA,ROLA;...) --
MAPA="${ESKARGA_UZYTKOWNICY:-anna.nowak:TK_BIURO_PODAWCZE;marek.zielinski:TK_REFERENT;agnieszka.duda:TK_ASYSTENT;adam.kowal:TK_SEDZIA,TK_PREZES;beata.sowa:TK_KIEROWNIK;katarzyna.lis:TK_ARCHIWISTA;marcin.kaminski:TK_ADMIN;admin.wszystko:TK_REFERENT,TK_KIEROWNIK}"
IFS=';' read -ra WPISY <<<"$MAPA"
for wpis in "${WPISY[@]}"; do
  login="${wpis%%:*}"; role="${wpis#*:}"
  UID_U=$(user_uid "$login")
  if [[ -z "$UID_U" ]]; then echo "pomijam $login – brak użytkownika w ID-users"; continue; fi
  IFS=',' read -ra lista <<<"$role"
  nadaj_role "$UID_U" "$ID" "${lista[@]}"
  echo "$login → $role"
done
echo "eSkarga zarejestrowana w ID-users: $PUBLICZNY (callback $PUBLICZNY/callback, back-channel $BACKCHANNEL)"
