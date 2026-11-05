# ADR 0022 – Repozytorium S3 z Object Lock w środowisku deweloperskim (MinIO → RustFS)

Status: zaakceptowana 03.11.2026 (MinIO, polecenie „znajdź obraz Dockera z S3”); **zmieniona 05.11.2026 – RustFS** (propozycja do potwierdzenia po pierwszym uruchomieniu)

## Kontekst
D5 wymaga S3 z Object Lock (tryb compliance dla podpisanych czynności i paczek archiwalnych). Dotychczasowy SeaweedFS obsługuje
S3, ale Object Lock pojawił się w nim późno i jest słabiej przetestowany; produkcyjne repozytorium (Ceph RGW) jest do zakupu.

## Decyzja
- Dev/test: **MinIO** (`minio/minio` + `minio/mc`), kubełek `eskarga-akta` tworzony z `--with-lock` i wersjonowaniem przez usługę `s3-init`.
  API na :9000, konsola :9001. Obraz oznaczony `latest` do czasu pierwszego uruchomienia – wtedy przypinamy konkretny `RELEASE.*`.
- Kod aplikacji pozostaje pod czyste API S3 (AWS SDK v2, path-style, SSE, `ObjectLockMode.COMPLIANCE`), bez zależności od dostawcy.

## Konsekwencje
- Licencja MinIO (AGPL) dotyczy tylko środowiska deweloperskiego; produkcja na Ceph RGW albo innym S3 z Object Lock.
- Test `S3DocumentStore` na Testcontainers (MinIO) – do dodania w profilu `it`.

## Zmiana 05.11.2026 – MinIO niedostępne, zamiennik RustFS

Przy pierwszym `docker compose up` obrazy `minio/minio` i `minio/mc` nie istnieją już na Docker Hub („pull access denied …
repository does not exist”; API Docker Hub zwraca 404 dla obu repozytoriów). Rozważone zamienniki z obrazami na Docker Hub
(stan 05.11.2026, sprawdzone w dokumentacji projektów, nie w działającym środowisku):

| Kandydat | Object Lock | Wersjonowanie | Uwagi |
| --- | --- | --- | --- |
| **RustFS** `rustfs/rustfs:1.0.4` (Apache 2.0) | „Available – shipped and covered by CI gates” (WORM) | dostępne | zamiennik MinIO: te same porty 9000/9001, `/data`, konsola; jeden wolumen wymaga `RUSTFS_UNSAFE_BYPASS_DISK_CHECK=true` (dev) |
| Versity S3 Gateway `versity/versitygw:v1.10.0` (Apache 2.0) | na backendzie POSIX „Conditional” | eksperymentalne (`--versioning-dir`) | lekki, xattr na systemie plików |
| LocalStack `localstack/localstack` | tak | tak | trwałość danych tylko w wersji Pro |
| Zenko CloudServer | tak | tak | obrazy z 2022–2023 |

Decyzja (propozycja): **RustFS** w dev, kubełek zakłada `amazon/aws-cli:2.39.4` (`create-bucket --object-lock-enabled-for-bucket`,
`put-bucket-versioning`). Skrypt `infra/docker/s3/init-bucket.sh` sprawdza przy każdym starcie, czy serwer egzekwuje blokadę
COMPLIANCE (zapis obiektu kontrolnego z `RetainUntilDate` +1 dzień i próba usunięcia wersji musi się nie powieść) – w razie
niepowodzenia `s3-init` kończy się błędem i start środowiska się zatrzymuje. Kod aplikacji nadal pod czyste API S3; przy tej
okazji `S3DocumentStore` dostał brakującą datę `RetainUntilDate` (S3 wymaga jej razem z trybem; okres `eskarga.s3.lockRetentionYears`,
domyślnie 10 lat – **do decyzji**), przełącznik SSE-S3 (`eskarga.s3.sse`, w dev wyłączone) i sumy kontrolne `WHEN_REQUIRED`
(domyślne CRC32/aws-chunked z AWS SDK 2.30+ odrzuca część serwerów zgodnych z S3).

Konsekwencje: licencja Apache 2.0 zamiast AGPL w dev; produkcja bez zmian (Ceph RGW lub inny S3 z Object Lock). Jeśli kontrola
egzekwowania Object Lock w RustFS nie przejdzie, następny kandydat to Versity S3 Gateway, a test `S3DocumentStore` w profilu `it`
(Testcontainers) ma używać tego samego obrazu co Compose.
