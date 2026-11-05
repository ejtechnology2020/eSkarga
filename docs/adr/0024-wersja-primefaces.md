# ADR 0024 – PrimeFaces 15 teraz, migracja do 16 jako osobne zadanie

- Status: zatwierdzone 05.11.2026 (zamawiający), spisane 05.11.2026
- Powiązane: ADR 0001 (D2 – JSF + PrimeFaces 15 `jakarta`), ADR 0002 (Elytron OIDC, CSP), ADR 0007 (widżet PDF)

## Kontekst

Stos ustalono 03.11.2026 (ADR 0001) na najnowszej wtedy stabilnej linii PrimeFaces 15 z klasyfikatorem `jakarta`.
Stan Maven Central sprawdzony 05.11.2026:

| Linia | Najnowsza wersja | Data publikacji |
| --- | --- | --- |
| 15.x | 15.0.19 (używana) | 2026 |
| 16.x | 16.0.1 | 03.11.2026 (16.0.0 krótko wcześniej) |
| 17.x | brak | – |

PrimeFaces 16 porzuca wariant `javax` – artefakt bez klasyfikatora `jakarta` – i wprowadza zmiany łamiące opisane
w przewodniku migracji. Zgodna wersja PrimeFaces Extensions nie została potwierdzona (Maven Central ograniczył
zapytania). Projekt jest w trakcie uruchamiania środowiska dev z ID-users (logowanie Elytron OIDC, CSP PrimeFaces);
już w linii 15 natrafiliśmy na zmiany API komponentów (`p:fileUpload` bez `allowTypes`/`sizeLimit` → `p:validateFile`).

## Decyzja

1. Pozostajemy przy PrimeFaces 15 na ostatniej poprawce (15.0.19, aktualizowanej w obrębie 15.0.x) do czasu
   zakończenia weryfikacji środowiska z ID-users (`[~]` w `doc/TASKS.md`). Migracja biblioteki UI w trakcie diagnostyki
   logowania mieszałaby przyczyny błędów.
2. Migracja do PrimeFaces 16 to osobne zadanie backlogu (T-057), wykonywane po zamknięciu bieżących problemów:
   - `pom.xml`: `primefaces.version` → 16.x, usunięcie klasyfikatora `jakarta` z `primefaces` i `primefaces-extensions`
     (parent i `app/pom.xml`), PrimeFaces Extensions w wersji zgodnej z 16 (do potwierdzenia przed startem);
   - przejście po przewodniku migracji PrimeFaces 15 → 16 (atrybuty i komponenty usunięte lub przemianowane,
     tryb CSP, motyw `saga`, `p:fileUpload`/`p:validateFile`, `p:dataTable`);
   - weryfikacja: `mvn -B verify`, `ui-tests` (Playwright + axe) na wszystkich ekranach S-01…S-26, ręczny test
     logowania, wylogowania i wysyłki plików na środowisku dev;
   - aktualizacja ADR 0001 (D2) i `doc/CLAUDE.md`.
3. Nowe linie (17+) oceniamy tą samą ścieżką: osobne zadanie, nie zmiana „przy okazji”.

## Konsekwencje

- Do czasu T-057 błędy wynikające ze zmian w PrimeFaces naprawiamy w obrębie 15.0.x.
- Zależności z klasyfikatorem `jakarta` pozostają w POM-ach do migracji; po niej klasyfikator znika.
- Poprawki bezpieczeństwa PrimeFaces 15.0.x przyjmujemy na bieżąco (skan `-Psecurity`).
