#!/usr/bin/env bash
# Aktualizacja dystrybucji Operaton dla WildFly (T-056). Użycie: update-operaton.sh <wersja> | --rollback <wersja>
set -euo pipefail
NEXUS="${NEXUS_BASE_URL:-https://nexus.tk.internal/repository}"
HOME_DIR="${WILDFLY_HOME:-/opt/operaton}"
DB_URL="${ESKARGA_DB_URL:-postgresql://eskarga_app@127.0.0.1:5432/eskarga}"
mode="update"; [[ "${1:-}" == "--rollback" ]] && { mode="rollback"; shift; }
version="${1:?podaj wersję}"
target="/opt/operaton-${version}"
systemctl stop wildfly
if [[ "$mode" == "rollback" ]]; then
  [[ -d "$target" ]] || { echo "brak katalogu $target"; exit 1; }
  ln -sfn "$target" "$HOME_DIR"; systemctl start wildfly; echo "wycofano do $version"; exit 0
fi
tmp=$(mktemp -d)
curl -fsSL "$NEXUS/operaton/operaton-bpm-wildfly-${version}.tar.gz" -o "$tmp/dist.tar.gz"
curl -fsSL "$NEXUS/operaton/operaton-bpm-wildfly-${version}.tar.gz.sha256" -o "$tmp/dist.sha256"
(cd "$tmp" && echo "$(cat dist.sha256 | awk '{print $1}')  dist.tar.gz" | sha256sum -c -)
# archiwum operaton-bpm-wildfly: bez katalogu nadrzędnego – serwer w server/wildfly-<wersja WildFly>/, skrypty SQL w sql/
mkdir -p "$tmp/dist" && tar -xzf "$tmp/dist.tar.gz" -C "$tmp/dist"
[[ -e "$target" ]] && { echo "katalog $target już istnieje"; exit 1; }
mv "$tmp/dist"/server/wildfly-* "$target"
rm -f "$target"/standalone/deployments/operaton-example-invoice-*.war
# skrypty upgrade schematu operaton (między kolejnymi wersjami) – właścicielem schematu jest rola aplikacji
current=$(readlink -f "$HOME_DIR" | sed 's#.*operaton-##')
for f in "$tmp/dist"/sql/upgrade/postgres_engine_${current}_to_*.sql "$tmp/dist"/sql/upgrade/postgres_identity_${current}_to_*.sql; do
  [[ -f "$f" ]] && psql "$DB_URL" -v ON_ERROR_STOP=1 -c "SET search_path TO operaton" -f "$f"
done
cp -a "$HOME_DIR/standalone/configuration/." "$target/standalone/configuration/"
cp -a "$HOME_DIR/standalone/deployments/." "$target/standalone/deployments/"
cp -a "$HOME_DIR/modules/org/postgresql" "$target/modules/org/" 2>/dev/null || true
chown -R eskarga:eskarga "$target"
ln -sfn "$target" "$HOME_DIR"
systemctl start wildfly
for i in $(seq 1 30); do curl -fsS http://127.0.0.1:8080/eskarga/health >/dev/null 2>&1 && { echo "OK: Operaton ${version}"; exit 0; }; sleep 5; done
echo "aplikacja nie odpowiada – rozważ --rollback ${current}"; exit 1
