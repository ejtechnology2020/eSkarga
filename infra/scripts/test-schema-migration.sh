#!/usr/bin/env bash
# Test migracji schematu Operaton na kopii danych (T-056): odtwarza dump w tymczasowym PostgreSQL 17 (Docker) i wykonuje skrypty upgrade.
set -euo pipefail
dump="${1:?dump pg_dump -Fc}"; version="${2:?nowa wersja Operaton}"; current="${3:-2.2.0}"
NEXUS="${NEXUS_BASE_URL:-https://nexus.tk.internal/repository}"
name="eskarga-migtest-$$"
docker run -d --name "$name" -e POSTGRES_PASSWORD=test -e POSTGRES_DB=eskarga postgres:17-alpine >/dev/null
trap 'docker rm -f "$name" >/dev/null' EXIT
sleep 8
docker cp "$dump" "$name:/tmp/eskarga.dump"
docker exec "$name" pg_restore -U postgres -d eskarga --no-owner /tmp/eskarga.dump
tmp=$(mktemp -d); curl -fsSL "$NEXUS/operaton/operaton-bpm-wildfly-${version}.tar.gz" | tar -xz -C "$tmp" --strip-components=1
for f in "$tmp"/sql/upgrade/postgres_engine_${current}_to_*.sql "$tmp"/sql/upgrade/postgres_identity_${current}_to_*.sql; do
  [[ -f "$f" ]] || continue
  docker cp "$f" "$name:/tmp/up.sql"
  docker exec "$name" psql -U postgres -d eskarga -v ON_ERROR_STOP=1 -c "SET search_path TO operaton" -f /tmp/up.sql
done
echo "Migracja schematu operaton ${current} → ${version} przeszła na kopii danych."
