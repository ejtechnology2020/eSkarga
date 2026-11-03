# ADR 0023 – Znacznik czasu (TSA): serwer deweloperski RFC 3161, produkcyjnie kwalifikowany dostawca

Status: propozycja (odpowiedź na „zaproponuj coś”)

## Propozycja
1. **Dev/preprod:** własny serwer RFC 3161 w kontenerze `tsa` (`infra/docker/tsa`: OpenSSL `ts` za minimalnym HTTP, certyfikat
   samopodpisany z `extendedKeyUsage=timeStamping`). Usługa dokumentów dostaje `ESKARGA_DOCS_TSAURL=http://tsa:3180/` i podpisuje
   PAdES-B-T; certyfikat TSA (`GET /tsa.crt`) trzeba dodać do magazynu zaufania (`eskarga.docs.trustStore`), razem z CA YubiKey.
2. **Produkcja:** kwalifikowany TSA od polskiego dostawcy usług zaufania (do wyboru przez TK, np. KIR Szafir, Certum, Sigillum,
   EuroCert) – ten sam parametr `eskarga.docs.tsaUrl` plus uwierzytelnienie wymagane przez dostawcę (zwykle TLS klienta albo konto).
   Z kwalifikowanym TSA podpis sędziego (nawet niekwalifikowany) ma wiarygodny czas; przy przejściu na podpis kwalifikowany
   konfiguracja się nie zmienia.
3. Brak TSA = podpis B-B (dopuszczalny w dev); walidacja DSS raportuje poziom w `signatureReport`.

## Decyzja do podjęcia
Wybór dostawcy kwalifikowanego TSA i tryb jego uwierzytelnienia – do czasu decyzji pozostaje serwer deweloperski.
