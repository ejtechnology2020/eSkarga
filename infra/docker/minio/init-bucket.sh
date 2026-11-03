#!/bin/sh
# Kubełek akt z włączonym Object Lock (D5, ADR 0022) – tryb COMPLIANCE ustawia aplikacja per obiekt (podpisane czynności, paczki archiwalne).
set -e
until mc alias set eskarga http://s3:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null 2>&1; do sleep 2; done
mc mb --ignore-existing --with-lock eskarga/eskarga-akta
mc version enable eskarga/eskarga-akta
echo "MinIO: kubełek eskarga-akta gotowy (Object Lock włączony)"
