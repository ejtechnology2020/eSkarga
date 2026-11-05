#!/bin/sh
# Konfiguruje źródło danych EskargaDS (schematy repertorium + operaton w jednej bazie, D1) i startuje WildFly.
set -e
cd "$OPERATON_HOME"
# WAR-y z katalogów target/ (wolumeny tylko do odczytu) kopiowane do deployments/ przy każdym starcie – restart kontenera
# po buildzie wdraża nową wersję; stare znaczniki skanera (.deployed/.failed/.undeployed) usuwane, żeby wdrożenie ruszyło na pewno.
for war in /opt/eskarga/wary/app/eskarga.war /opt/eskarga/wary/docs/eskarga-docs-service.war; do
  nazwa=$(basename "$war")
  if [ -f "$war" ]; then
    rm -f "standalone/deployments/$nazwa".*
    cp "$war" "standalone/deployments/$nazwa"
    echo "Wdrażam $nazwa ($(stat -c %s "$war") B, $(date -r "$war" +%FT%T))"
  else
    echo "UWAGA: brak $war – zbuduj artefakty (mvn -B package / infra\windows\buduj.cmd)" >&2
  fi
done
# Uwierzytelnienie klienta eskarga w ID-users: rejestracja narzędziem projektu id (aplikacje:eskarga, T-037) daje private_key_jwt –
# klucz PKCS#8 <katalog wyników>/eskarga.key.pem (wolumen /opt/eskarga/klucze). Elytron OIDC potrzebuje PKCS#12, więc budujemy go
# tutaj (openssl) z hasłem losowanym na czas życia kontenera. ESKARGA_OIDC_CLIENTAUTH: auto (klucz, jeśli jest) | jwt | secret.
KLUCZ_PEM=/opt/eskarga/klucze/eskarga.key.pem
TRYB_KLIENTA="${ESKARGA_OIDC_CLIENTAUTH:-auto}"
if [ "$TRYB_KLIENTA" = auto ]; then
  if [ -f "$KLUCZ_PEM" ]; then TRYB_KLIENTA=jwt; else TRYB_KLIENTA=secret; fi
fi
if [ "$TRYB_KLIENTA" = jwt ]; then
  [ -f "$KLUCZ_PEM" ] || { echo "ESKARGA_OIDC_CLIENTAUTH=jwt, ale brak $KLUCZ_PEM (ID_USERS_KLUCZE_DIR w .env → katalog wyników aplikacje:eskarga projektu id)" >&2; exit 1; }
  export ESKARGA_OIDC_KEYSTOREPASSWORD
  ESKARGA_OIDC_KEYSTOREPASSWORD=$(head -c 24 /dev/urandom | base64 | tr -d '/+=')
  openssl req -new -x509 -key "$KLUCZ_PEM" -subj "/CN=eskarga" -days 3650 -out /opt/eskarga/eskarga-klient.crt.pem 2>/dev/null
  openssl pkcs12 -export -inkey "$KLUCZ_PEM" -in /opt/eskarga/eskarga-klient.crt.pem -name eskarga \
    -out /opt/eskarga/eskarga-klient.p12 -passout env:ESKARGA_OIDC_KEYSTOREPASSWORD
  chmod 600 /opt/eskarga/eskarga-klient.p12
  export ESKARGA_OIDC_CLIENTAUTH=jwt ESKARGA_OIDC_KEYSTORE=/opt/eskarga/eskarga-klient.p12 ESKARGA_OIDC_KEYALIAS=eskarga
  # ten sam klucz dla client credentials aplikacja → usługa dokumentów (ClientCredentialsTokenClient.fromSettings)
  export ESKARGA_DOCS_OIDC_PRIVATEKEYPEM="$KLUCZ_PEM"
  echo "Uwierzytelnienie klienta eskarga: private_key_jwt (klucz $KLUCZ_PEM$( [ -f /opt/eskarga/klucze/eskarga.kid ] && printf ', kid %s' "$(cat /opt/eskarga/klucze/eskarga.kid)"))"
else
  export ESKARGA_OIDC_CLIENTAUTH=secret
  echo "Uwierzytelnienie klienta eskarga: client_secret (ESKARGA_OIDC_CLIENTSECRET)"
fi
# Konfiguracja przez CLI przy każdym starcie (skrypt idempotentny, embed-server ~5 s) – zmiany w eskarga-datasource.cli
# (wolumen) działają bez odtwarzania kontenera; błąd CLI zatrzymuje start (set -e).
bin/jboss-cli.sh --file=/opt/operaton/eskarga-datasource.cli \
  -Ddb.host="${DB_HOST}" -Ddb.name="${DB_NAME}" -Ddb.user="${DB_USER_APP}" -Ddb.password="${DB_PASSWORD_APP}"
# ${db.*} w standalone.xml to wyrażenia rozwiązywane przy starcie z właściwości systemowych (hasło nie leży w pliku konfiguracji)
exec bin/standalone.sh -b 0.0.0.0 -bmanagement 0.0.0.0 \
  -Ddb.host="${DB_HOST}" -Ddb.name="${DB_NAME}" -Ddb.user="${DB_USER_APP}" -Ddb.password="${DB_PASSWORD_APP}" \
  -Deskarga.environment="${ESKARGA_ENVIRONMENT:-TEST}" \
  -Deskarga.faces.stage="${ESKARGA_FACES_STAGE:-Production}" \
  -Deskarga.security.mode="${ESKARGA_SECURITY_MODE:-oidc}" \
  -Deskarga.oidc.issuer="${ESKARGA_OIDC_ISSUER}" \
  -Deskarga.oidc.clientId="${ESKARGA_OIDC_CLIENTID}" \
  -Deskarga.oidc.clientSecret="${ESKARGA_OIDC_CLIENTSECRET}" \
  -Deskarga.oidc.rolesClaim="${ESKARGA_OIDC_ROLESCLAIM}" \
  -Deskarga.oidc.mfaClaim="${ESKARGA_OIDC_MFACLAIM}" \
  -Deskarga.oidc.mfaRequiredValue="${ESKARGA_OIDC_MFAREQUIREDVALUE}" \
  -Deskarga.s3.endpoint="${ESKARGA_S3_ENDPOINT}" -Deskarga.s3.bucket="${ESKARGA_S3_BUCKET}" \
  -Deskarga.s3.accessKey="${ESKARGA_S3_ACCESSKEY}" -Deskarga.s3.secretKey="${ESKARGA_S3_SECRETKEY}" \
  -Deskarga.s3.sse="${ESKARGA_S3_SSE:-true}" -Deskarga.s3.lockRetentionYears="${ESKARGA_S3_LOCKRETENTIONYEARS:-10}" \
  -Deskarga.demo.today="${ESKARGA_DEMO_TODAY}"
