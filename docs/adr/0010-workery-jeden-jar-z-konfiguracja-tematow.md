# ADR 0010 – Workery integracji: jeden JAR z konfiguracją tematów (T-042)

- Status: przyjęte 3.11.2026
- Data: 3.11.2026

## Decyzja
Jeden artefakt `eskarga-workers.jar` obsługuje wszystkie tematy external tasks; lista tematów dla instancji pochodzi ze zmiennej
`ESKARGA_WORKER_TOPICS`. Na produkcji uruchamia się osobne usługi systemd na integrację (np. `eskarga-workers@doreczenia`),
każda z własną listą tematów, więc awaria albo wolne API jednego systemu nie blokuje pozostałych, a jest jeden potok budowania.

## Odrzucone
Osobny JAR na temat – więcej artefaktów i duplikacja kodu klienta, ponawiania i uwierzytelnienia bez korzyści w izolacji
(izolację daje osobny proces systemd z konfiguracją).
