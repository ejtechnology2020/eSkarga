# ADR 0019 – Metryki Prometheus, panel Grafana, logi JSON

Status: propozycja

## Decyzja
- Aplikacja wystawia `/eskarga/health` (JSON, 503 gdy baza albo silnik nie odpowiada) i `/eskarga/metrics` (format Prometheus) bez bibliotek
  zewnętrznych: JVM, zadania/incydenty/external tasks Operaton, kolejka OCR, terminy po upływie, doręczenia bez zwrotki, licznik dziennika zdarzeń.
  Dostęp do `/metrics` ograniczony listą adresów (`eskarga.metrics.allow`).
- Workery wystawiają `/metrics` i `/health` na porcie 9404 (liczniki zadań per temat).
- Reguły alertów i panel Grafana w `infra/monitoring`; w dev profil Compose `monitoring`.
- Logi WildFly w JSON (`logging-json.cli`) do centralnego systemu; komunikaty bez danych osobowych.

## Konsekwencje
- Alert „terminy po upływie bez czynności” opiera się na tabeli `deadline`; weryfikacja łańcucha audytu jest zadaniem administratora (S-25) – alert
  wykrywa brak nowych wpisów.
