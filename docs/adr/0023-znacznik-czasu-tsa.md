# ADR 0023 – Znacznik czasu (TSA): serwer deweloperski RFC 3161, produkcyjnie kwalifikowany dostawca

Status: zaakceptowana 03.11.2026 – produkcyjnie KIR Szafir (kwalifikowany znacznik czasu)

## Propozycja
1. **Dev/preprod:** własny serwer RFC 3161 w kontenerze `tsa` (`infra/docker/tsa`: OpenSSL `ts` za minimalnym HTTP, certyfikat
   samopodpisany z `extendedKeyUsage=timeStamping`). Usługa dokumentów dostaje `ESKARGA_DOCS_TSAURL=http://tsa:3180/` i podpisuje
   PAdES-B-T; certyfikat TSA (`GET /tsa.crt`) trzeba dodać do magazynu zaufania (`eskarga.docs.trustStore`), razem z CA YubiKey.
2. **Produkcja:** kwalifikowany TSA od polskiego dostawcy usług zaufania (do wyboru przez TK, np. KIR Szafir, Certum, Sigillum,
   EuroCert) – ten sam parametr `eskarga.docs.tsaUrl` plus uwierzytelnienie wymagane przez dostawcę (zwykle TLS klienta albo konto).
   Z kwalifikowanym TSA podpis sędziego (nawet niekwalifikowany) ma wiarygodny czas; przy przejściu na podpis kwalifikowany
   konfiguracja się nie zmienia.
3. Brak TSA = podpis B-B (dopuszczalny w dev); walidacja DSS raportuje poziom w `signatureReport`.

## Decyzja (03.11.2026)
Dostawcą znacznika czasu na produkcji i preprodukcji jest **KIR Szafir** (usługa kwalifikowana). Konfiguracja:
`eskarga.docs.tsaUrl` = adres usługi TSA KIR z umowy (publiczny adres usługi KIR to `http://www.ts.kir.com.pl/HttpTspServer`;
TK używa adresu i trybu dostępu z umowy), `eskarga.docs.tsaUser`/`eskarga.docs.tsaPassword` (HTTP Basic) albo certyfikat
klienta w `eskarga.docs.tsaKeyStore` – zależnie od sposobu uwierzytelnienia konta KIR. Łańcuch certyfikatów TSA KIR (Narodowe
Centrum Certyfikacji → KIR Szafir TSA) trafia do magazynu zaufania usługi dokumentów (`eskarga.docs.trustStore`), żeby walidacja
DSS potwierdzała znacznik (PAdES-B-T, a po dodaniu dowodów walidacji – B-LT).

Do uzupełnienia przez TK: umowa z KIR (konto / certyfikat dostępu, limity wywołań), adres w `infra/ansible/group_vars/all/main.yml` (`tsa_url`).
Dev pozostaje na serwerze `tsa` z Compose.
