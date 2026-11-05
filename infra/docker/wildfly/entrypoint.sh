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
