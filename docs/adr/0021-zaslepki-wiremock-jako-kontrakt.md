# ADR 0021 – Zaślepki WireMock jako tymczasowy kontrakt integracji zewnętrznych

Status: zaakceptowana 03.11.2026 („działamy na zaślepkach”)

## Decyzja
Do czasu udostępnienia API e-Doręczeń/ePUAP, operatora pocztowego i repertoriów SK/K/U kontraktem są zaślepki w
`infra/docker/wiremock/mappings` (opis w `infra/docker/wiremock/README.md`). Workery implementują ten kontrakt; klucz
idempotencji przechodzi nagłówkiem `Idempotency-Key`. Zmiana na API docelowe = nowe mapowanie pól w handlerze, bez zmian w aplikacji.

## Konsekwencje
- Testy kontraktowe workerów (`DeliveryHandlerTest`, `MeritTransferHandlerTest`) są jedynym źródłem prawdy o polach.
- Idempotencja po stronie zaślepki nie jest pełna (WireMock bez stanu); zapewnia ją aplikacja (`confirmDispatch`, `markTransferred`).
