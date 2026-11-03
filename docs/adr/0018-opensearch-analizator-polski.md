# ADR 0018 – OpenSearch z wtyczką analysis-stempel; indeks odbudowywalny, uprawnienia poza indeksem

Status: propozycja

## Decyzja
1. Indeks `eskarga-akta` w OpenSearch 2.x z analizatorem własnym `pl` (tokenizer standard, `lowercase`, `polish_stem` z wtyczki `analysis-stempel`).
   Obrazy i role Ansible instalują wtyczkę.
2. Dokument indeksu zawiera tylko dane potrzebne do wyszukania (sygnatura, tytuł, strony, tekst OCR, karty, data). Uprawnienia sprawdza
   `SearchService` po stronie aplikacji (`CaseAccessPolicy`), nigdy indeks – brak wyników ze spraw bez dostępu.
3. Dokumenty niejawne i notatki wewnętrzne nie są indeksowane. Indeks jest odbudowywalny z bazy (`SearchService.rebuild`).
4. Bez `eskarga.search.url` działa indeks w pamięci (dev, demo, testy) – ta sama umowa `SearchIndex`.

## Konsekwencje
- Tekst OCR trafia do indeksu po przetworzeniu dokumentu przez usługę dokumentów (worker `dokument-przetworz`).
- Wymóg NFR-08 (≤ 2 s) weryfikować na danych testowych z prawdziwym OpenSearch (profil `it` – do dodania test z Testcontainers).
