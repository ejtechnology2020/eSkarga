# Ansible – role dla maszyn wirtualnych (T-053, ADR 0009)

- `site.yml` – pełne środowisko: PostgreSQL 17 + Patroni + etcd (3 węzły), OpenSearch (3 węzły, wtyczka `analysis-stempel`),
  WildFly 41 z dystrybucją Operaton (standalone-ha, 2 węzły) z WAR-ami aplikacji i usługi dokumentów, workery (systemd), HAProxy.
- Inwentarze: `inventories/preprod`, `inventories/prod` (adresy do uzupełnienia przez zespół infrastruktury TK).
- Sekrety: `group_vars/all/vault.yml` (szyfrowany `ansible-vault`), wzór w `vault.yml.example`. Nic z sekretów nie trafia do repozytorium.
- Pakiety i artefakty z Nexusa TK (`nexus_base_url`); wersje w `group_vars/all/main.yml` są te same, co w obrazach Docker.
- Migracje schematu wykonuje rola `eskarga_services` przez Flyway CLI rolą migracji przed wdrożeniem WAR (ADR 0016).

```bash
ansible-playbook -i inventories/preprod/hosts.yml site.yml --ask-vault-pass
ansible-playbook -i inventories/preprod/hosts.yml site.yml --tags eskarga_services -e eskarga_version=1.0.1   # nowa wersja
```

Weryfikacja na świeżych VM (Vagrant/libvirt albo środowisko testowe TK) – do wykonania przez zespół infrastruktury; w sesji
deweloperskiej bez VM playbooki zostały sprawdzone tylko składniowo (`ansible-playbook --syntax-check`, jeśli dostępne).
