# ADR 0022 – MinIO jako repozytorium S3 w środowisku deweloperskim (Object Lock)

Status: zaakceptowana 03.11.2026 (polecenie „znajdź obraz Dockera z S3”)

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
