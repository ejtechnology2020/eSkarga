# Zaślepki systemów zewnętrznych (WireMock) – kontrakt tymczasowy (ADR 0021)

| Ścieżka | Nagłówki | Odpowiedź | Worker |
| --- | --- | --- | --- |
| `POST /edoreczenia/przesylki` {adresat, adresEDoreczen, tytul, dokument(b64)} | `Idempotency-Key: DOR-…` | 201 `{identyfikator, status, kluczIdempotencji}` | `DeliveryHandler` (EDORECZENIA/EPUAP) |
| `POST /poczta/przesylki` {adresat, adres, rodzaj, tytul} | `Idempotency-Key: DOR-…` | 201 `{numerNadania, status, kluczIdempotencji}` | `DeliveryHandler` (POCZTA_POLECONY_ZPO) |
| `POST /repertorium-sk/sprawy` (dane sprawy z `/api/sprawy/{syg}/przekazanie`) | `Idempotency-Key: PRZEKAZ-…` | 201 `{sygnatura, kluczIdempotencji}` | `MeritTransferHandler` |

Uwaga: WireMock w trybie szablonów (`--global-response-templating`) nie pamięta kluczy idempotencji między wywołaniami – ponowienie
daje nowy `numerNadania`/`sygnatura`. Idempotencję po stronie aplikacji zapewniają `confirmDispatch` (ten sam numer → neutralne) i
`markTransferred` (brak drugiej wersji). Docelowe API zastąpią zaślepki bez zmian w workerach, jeśli zachowają te pola.
