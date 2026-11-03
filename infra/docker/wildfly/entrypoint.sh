#!/bin/sh
# Konfiguruje źródło danych EskargaDS (schematy repertorium + operaton w jednej bazie, D1) i startuje WildFly.
set -e
cd "$OPERATON_HOME"
if [ ! -f standalone/configuration/.eskarga-configured ]; then
  bin/jboss-cli.sh --file=/opt/operaton/eskarga-datasource.cli \
    -Ddb.host="${DB_HOST}" -Ddb.name="${DB_NAME}" -Ddb.user="${DB_USER_APP}" -Ddb.password="${DB_PASSWORD_APP}"
  touch standalone/configuration/.eskarga-configured
fi
exec bin/standalone.sh -b 0.0.0.0 -bmanagement 0.0.0.0 \
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
  -Deskarga.demo.today="${ESKARGA_DEMO_TODAY}"
