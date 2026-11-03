# ADR 0017 – Jedna umowa danych widoków (`ViewData`) z implementacją demo i produkcyjną

Status: propozycja (do potwierdzenia po zakończeniu prac)

## Kontekst

Ekrany powstały na danych przykładowych (`DemoDataset`, ADR 0004) i musiały zostać podłączone do usług domenowych bez
utraty trybu demo potrzebnego do pracy nad interfejsem bez ID-users i bazy.

## Decyzja

1. Interfejs `web.ViewData` zbiera wszystkie odczyty, których potrzebują beany widoków i Facelets (`#{dane.…}`).
2. Implementacje: `DemoDataset` (tryb `eskarga.security.mode=demo`) i `JpaViewData` (usługi domenowe na JPA i Operaton,
   każdy odczyt sprawy przez `CaseAccessPolicy`). Wybór robi `ViewDataProducer` na czas żądania.
3. Akcje zapisu (rejestracja, kwalifikacja, założenie sprawy, korekta, zwrotka, wyznaczenie sędziego, projekt pisma,
   podpis, archiwizacja, upoważnienia) beany wykonują przez usługi domenowe; w trybie demo pozostaje symulacja
   komunikatów bez zapisu.
4. Rekordy widoków (`web.model.*`) pozostają jedynym typem danych w Facelets – encje JPA nie trafiają do warstwy UI.

## Konsekwencje

- Nowy ekran = nowa metoda w `ViewData` z obiema implementacjami; test `JpaViewDataTest` sprawdza mapowania na H2.
- Wyszukiwanie w `JpaViewData` jest proste (JPQL); indeks OpenSearch (T-032) podłącza `SearchService` w tej samej metodzie.
