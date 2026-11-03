# ADR 0008 – Aplikacja lokalna do podpisu: własna na PKCS#11 + DSS (propozycja, T-040 [decyzja])

- Status: **propozycja do zatwierdzenia**; do czasu decyzji interfejs `LocalSignerClient` z implementacją testową
- Data: 3.11.2026

## Decyzja tymczasowa
1. Serwer liczy dane do podpisu (EU DSS 6.5.1, `PAdESService.getDataToSign`), przeglądarka przekazuje skrót do aplikacji lokalnej
   (`https://localhost:<port>/sign`, CORS tylko dla domeny aplikacji), aplikacja podpisuje skrót kartą (PKCS#11) i zwraca wartość podpisu
   oraz certyfikat; serwer składa podpis (`signDocument`), dodaje znacznik czasu (PAdES-B-T, TSA z konfiguracji) i waliduje wynik.
2. Aplikacja lokalna: własna, w Javie 21 (jpackage), na PKCS#11 kart kwalifikowanych używanych w TK; alternatywą jest SDK dostawcy kart,
   jeśli jest już wdrożone na stanowiskach sędziów – decyzja po inwentaryzacji stanowisk.
3. Kontrakt `LocalSignerClient`: `SigningRequest(hash, algorithm, documentTitle)` → `SigningResponse(signatureValue, certificateChain)`;
   implementacja testowa podpisuje kluczem z PKCS#12 / SoftHSM2.
4. Skład 3 sędziów: kolejne podpisy PAdES na tym samym dokumencie (DSS obsługuje wielokrotne podpisy), dokument „gotowy” po trzecim.

## Konsekwencje
Dystrybucja aplikacji lokalnej i czytników na stanowiskach, polityka CORS, obsługa błędów z ekranu S-16 (brak aplikacji, brak karty,
błędny PIN, certyfikat nieważny, przerwane połączenie, odrzucenie przy weryfikacji).
