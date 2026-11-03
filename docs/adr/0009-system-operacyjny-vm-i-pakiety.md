# ADR 0009 – System operacyjny VM i dystrybucja pakietów (propozycja, T-053 [decyzja])

- Status: **propozycja do zatwierdzenia**
- Data: 3.11.2026

## Decyzja tymczasowa
- System: Ubuntu Server 24.04 LTS (wsparcie do 2029, pakiety PostgreSQL 17 i Patroni z repozytoriów PGDG/apt), alternatywnie RHEL 9 /
  Rocky Linux 9, jeśli TK ma standard RHEL.
- Dystrybucja oprogramowania: WildFly z dystrybucją Operaton, OpenSearch i artefakty aplikacji jako archiwa z wewnętrznego repozytorium
  artefaktów (Nexus/Artifactory) z sumami SHA-256 sprawdzanymi przez Ansible; pakiety systemowe z lustrzanego repozytorium apt w sieci TK.
- Konfiguracja wyłącznie przez Ansible (`infra/ansible`), sekrety w Ansible Vault poza repozytorium kodu.
- Wersje pakietów przypięte i zgodne z obrazami Docker (`infra/docker`).

## Konsekwencje
Role `wildfly_operaton`, `haproxy`, `postgresql_patroni`, `opensearch`, `eskarga_services`; inwentarze `preprod` i `prod`.
Walidacja playbooka na świeżych VM (Vagrant albo środowisko TK) jest kryterium akceptacji T-053.
