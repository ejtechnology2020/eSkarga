# ADR 0026: Dokumenty Word (DOC/DOCX) w aktach – konwersja do PDF/A przez osobny konwerter

Data: 07.11.2026. Status: zaakceptowany.

## Kontekst

Pisma od pełnomocników i z e-Doręczeń bywają dokumentami Word. Akta e-sprawy to wyłącznie PDF/A-2b z sumą SHA-256 (D13, NFR),
a widżet podglądu akt to pdf.js. Dotąd rejestracja wpływu i dołączanie do akt przyjmowały tylko PDF (i TIFF jako skan wpływu),
więc dokument Word nie trafiał do systemu.

## Decyzja

1. Dokument Word jest rozpoznawany po sygnaturze pliku (DOCX = ZIP z wpisem `word/`, DOC = kontener OLE2), nigdy po rozszerzeniu
   (`FileTypes` w aplikacji, `OfficeFormat` w usłudze dokumentów).
2. Do akt trafia **oryginał** (klucz w S3, SHA-256), a dokument czeka na przetworzenie w zwykłym cyklu workera `dokument-przetworz`.
   Usługa dokumentów w `/dokumenty/przetworz` rozpoznaje format, **konwertuje do PDF przez konwerter**, pomija OCR (dokument ma
   warstwę tekstową) i dalej traktuje wynik jak każdy PDF: podpisy, PDF/A-2b (veraPDF), tekst do wyszukiwarki.
3. Po konwersji **kartą akt jest PDF/A**; oryginał zostaje w repozytorium pod `source_storage_key` (do pobrania z adnotacją
   w dzienniku zdarzeń), a `source_content_type` i `source_file_name` dokumentują pochodzenie. W dzienniku zdarzeń: „z DOCX”.
4. Konwerter to **osobny składnik**: Gotenberg (LibreOffice za API HTTP, `POST /forms/libreoffice/convert`, `pdfa=PDF/A-2b`),
   za interfejsem `DocumentConverter`. Dev: kontener `konwerter` w Compose, tylko w sieci Compose. Prod: ten sam obraz jako
   kontener Podman w systemd na węźle aplikacji, nasłuch wyłącznie na `127.0.0.1:3000` (rola `konwerter_dokumentow`).
   Adres: `eskarga.docs.converterUrl` / `ESKARGA_DOCS_CONVERTERURL`; bez adresu konwersja kończy się czytelnym błędem konfiguracji.
5. Plik Word jest **danymi niezaufanymi**: idzie wyłącznie do odizolowanego procesu konwertera (bez dostępu do bazy, S3 i sieci
   wewnętrznej), z limitem rozmiaru (50 MB) i czasu; wynik przechodzi veraPDF jak każde pismo.
6. Przed konwersją dokument nie ma podglądu – widok akt pokazuje planszę „czeka na konwersję” i oryginał do pobrania; TIFF nadal
   pozostaje tylko skanem wpływu (bez karty akt).

## Odrzucone

- **Apache Tika**: wyciąga tekst i prostą strukturę, nie odwzorowuje układu pisma; do indeksowania zbędna, bo PDF/A po konwersji
  ma warstwę tekstową.
- **LibreOffice `soffice --headless` wewnątrz kontenera WildFly**: brak izolacji niezaufanego pliku od serwera aplikacji.
- **Apache POI / docx4j → XHTML → OpenHTMLtoPDF**: nowa zależność, uboższa wierność, brak starego `.doc`.

## Konsekwencje

- Nowy składnik do utrzymania na VM (obraz z lustra Nexusa, aktualizacje wersji razem z `infra/docker/.env.example`).
- Migracja V10 (trzy kolumny `case_document`), rola aplikacji bez nowych uprawnień.
- Testy: `OfficeFormatTest`, `GotenbergConverterTest` (atrapa HTTP), `DocumentsResourceOfficeTest`, `FileTypesTest`,
  `OfficeDocumentsTest`, GUI UC-01/UC-03 z plikiem DOCX.
