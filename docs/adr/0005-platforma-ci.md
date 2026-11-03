# ADR 0005 – Platforma CI: GitHub Actions (propozycja, T-006 [decyzja])

- Status: **propozycja do zatwierdzenia** (decyzja po zakończeniu prac)
- Data: 3.11.2026

## Kontekst
T-006 wymaga potoku, który buduje i testuje każdą zmianę, skanuje zależności i publikuje artefakty z sumami kontrolnymi.
Do wyboru: GitLab CI, Jenkins, GitHub Actions. Repozytorium kodu jest obecnie na GitHubie (`ejtechnology2020/eSkarga`).

## Decyzja tymczasowa
GitHub Actions z workflow `.github/workflows/ci.yml`: `mvn -B verify`, skan OWASP Dependency-Check (`-Psecurity`,
build przerywany przy CVSS ≥ 7), artefakty WAR/JAR z `SHA256SUMS`. Testy integracyjne (`-Pit`) na runnerze z Dockerem.

## Alternatywy
- **GitLab CI** – właściwy wybór, jeśli Trybunał utrzymuje własną instancję GitLab w sieci wewnętrznej; potok przenosi się 1:1
  (ten sam `mvn -B verify`), plik `.gitlab-ci.yml` powstaje w T-006 po decyzji.
- **Jenkins** – gdy w TK działa już Jenkins; wymaga `Jenkinsfile` i agentów z Dockerem.

## Konsekwencje
Kroki potoku są niezależne od platformy (Maven, Docker Compose), więc zmiana platformy to tylko plik definicji potoku.
