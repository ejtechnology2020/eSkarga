# Dane wejściowe do oceny skutków dla ochrony danych (DPIA) – eSkarga

## Kategorie danych i podstawa

| Kategoria | Przykłady | Źródło | Podstawa / cel |
| --- | --- | --- | --- |
| Dane identyfikacyjne stron | imię i nazwisko, adres, adres do e-Doręczeń | pisma wpływające | art. 6 ust. 1 lit. e RODO – zadanie publiczne TK (ustawa o organizacji i trybie postępowania przed TK) |
| Dane pełnomocników | tytuł zawodowy, numer wpisu | pisma, pełnomocnictwa | jak wyżej (BR-05, art. 44 ustawy) |
| Treść skarg i załączników | opis stanu faktycznego, orzeczenia | skany PDF (OCR) | jak wyżej; dane szczególnych kategorii mogą wystąpić w treści – art. 9 ust. 2 lit. f/g |
| Dane pracowników TK | identyfikator, rola, czynności | ID-users, dziennik zdarzeń | art. 6 ust. 1 lit. c/e – rozliczalność (NFR-02) |

## Środki ochrony zaimplementowane w systemie

- Minimalizacja: zmienne procesu zawierają wyłącznie identyfikatory (D8); dokumenty niejawne – tylko metadane (NFR-06).
- Kontrola dostępu trzypoziomowa (D4): role ogólne, przydział do sprawy, upoważnienia; odmowy w dzienniku zdarzeń.
- Pseudonimizacja/anonimizacja: wersje zanonimizowane dla osób trzecich (BR-09), redakcja rastrowa (`PdfRedactor`), flaga art. 80.
- Szyfrowanie: TLS od brzegu, SSE w repozytorium S3, klucze w HSM (D12), PubSec dla wpływu zaszyfrowanego.
- Integralność i rozliczalność: łańcuch skrótów dziennika zdarzeń z podpisem HSM, Object Lock na podpisanych czynnościach i paczkach archiwalnych.
- Retencja: kategorie archiwalne w konfiguracji (ADR 0012), paczka archiwalna z metadanymi; dane w indeksie wyszukiwania odbudowywalne z bazy.
- Transfery: brak przekazywania do państw trzecich; integracje (e-Doręczenia, poczta, repertoria SK/K/U) przez workery w sieci wewnętrznej z kluczami idempotencji.

## Ryzyka do oceny przez IOD

1. Treść OCR w indeksie OpenSearch (kopia danych) – mitygacja: dostęp tylko z aplikacji, filtr uprawnień po stronie aplikacji, szyfrowanie dysków.
2. Znak wodny z nazwiskiem pracownika na pobranym dokumencie – uzasadnienie: rozliczalność; informacja dla pracowników.
3. Notatki wewnętrzne zespołu sędziego – ograniczone do zespołu, wyłączone z eksportu i archiwum (FR-24).
4. Logi JSON w centralnym systemie – komunikaty bez danych osobowych; identyfikatory użytkowników jako pseudonimy.
