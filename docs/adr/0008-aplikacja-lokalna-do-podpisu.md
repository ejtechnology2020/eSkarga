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


## Decyzja 03.11.2026 – YubiKey z certyfikatem niekwalifikowanym

- Sędzia podpisuje kluczem w slocie PIV YubiKey (certyfikat niekwalifikowany wystawiony przez wewnętrzne CA TK). Moduł
  `local-signer` (JAR na stacji sędziego) otwiera token przez SunPKCS11 + `libykcs11`, nasłuchuje tylko na `127.0.0.1:17227`
  i akceptuje CORS wyłącznie z adresu eSkargi. Przeglądarka przekazuje do niego **skrót** (krok 1 z serwera), a zwraca
  wartość podpisu (RSA PKCS#1 v1.5 z DigestInfo albo ECDSA w DER) – dokładnie to, czego wymaga DSS `signDocument` (krok 2).
- Podpis jest zaawansowany (PAdES-B-B, z TSA dev: B-T), nie kwalifikowany; walidacja DSS wymaga dodania CA TK do
  `eskarga.docs.trustStore`, inaczej wynik to INDETERMINATE i ostrzeżenie w aktach.
- Przejście na podpis kwalifikowany (karta + SDK dostawcy) nie zmienia kontraktu: inna implementacja `local-signer`.
- Tryb `--pkcs12` istnieje tylko dla testów automatycznych.
