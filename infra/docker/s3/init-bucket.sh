#!/bin/sh
# Kubełek akt z włączonym Object Lock i wersjonowaniem (D5, ADR 0022) – obraz amazon/aws-cli, idempotentnie.
# Tryb COMPLIANCE ustawia aplikacja per obiekt (podpisane czynności, paczki archiwalne); tu kontrola, czy serwer go egzekwuje.
set -e
export AWS_ACCESS_KEY_ID="$S3_ACCESS_KEY" AWS_SECRET_ACCESS_KEY="$S3_SECRET_KEY" AWS_DEFAULT_REGION=us-east-1
# sumy kontrolne CRC32 i aws-chunked (domyślne w AWS CLI 2.23+) odrzuca część serwerów zgodnych z S3
export AWS_REQUEST_CHECKSUM_CALCULATION=when_required AWS_RESPONSE_CHECKSUM_VALIDATION=when_required
S3="aws --endpoint-url ${S3_ENDPOINT:-http://s3:9000} s3api"
BUCKET="${S3_BUCKET:-eskarga-akta}"

until $S3 list-buckets >/dev/null 2>&1; do echo "czekam na S3…"; sleep 2; done
if $S3 head-bucket --bucket "$BUCKET" >/dev/null 2>&1; then
  echo "S3: kubełek $BUCKET istnieje"
else
  $S3 create-bucket --bucket "$BUCKET" --object-lock-enabled-for-bucket >/dev/null
  echo "S3: utworzono kubełek $BUCKET (Object Lock włączony)"
fi
$S3 put-bucket-versioning --bucket "$BUCKET" --versioning-configuration Status=Enabled
$S3 get-object-lock-configuration --bucket "$BUCKET" | grep -q '"ObjectLockEnabled": "Enabled"' \
  || { echo "BŁĄD: Object Lock nie jest włączony na kubełku $BUCKET" >&2; exit 1; }

# Kontrola egzekwowania: obiekt z blokadą COMPLIANCE do jutra nie może zostać usunięty (ani jego wersja).
KLUCZ="_kontrola/object-lock-$(date +%Y%m%d)"
DO=$(date -u -d '+1 day' +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -v+1d +%Y-%m-%dT%H:%M:%SZ)
echo "kontrola Object Lock $(date -u +%FT%TZ)" > /tmp/kontrola.txt
$S3 put-object --bucket "$BUCKET" --key "$KLUCZ" --body /tmp/kontrola.txt \
  --object-lock-mode COMPLIANCE --object-lock-retain-until-date "$DO" >/dev/null
WERSJA=$($S3 head-object --bucket "$BUCKET" --key "$KLUCZ" | sed -n 's/.*"VersionId": "\([^"]*\)".*/\1/p')
if $S3 delete-object --bucket "$BUCKET" --key "$KLUCZ" ${WERSJA:+--version-id "$WERSJA"} >/dev/null 2>&1 \
   && ! $S3 head-object --bucket "$BUCKET" --key "$KLUCZ" ${WERSJA:+--version-id "$WERSJA"} >/dev/null 2>&1; then
  echo "BŁĄD: serwer S3 pozwolił usunąć obiekt z blokadą COMPLIANCE – Object Lock nie jest egzekwowany (D5, ADR 0022)" >&2
  exit 1
fi
echo "S3: kubełek $BUCKET gotowy – wersjonowanie i Object Lock (COMPLIANCE) egzekwowane"
