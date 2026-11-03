# ADR 0015 – Wymogi formalne skargi i wniosku w tabeli decyzyjnej DMN

Status: zaakceptowana 03.11.2026 (tabela DMN (treść „na razie ok”))

## Kontekst

UC-06 wymaga listy kontrolnej wymogów formalnych zależnej od typu sprawy (skarga: art. 53; wniosek: art. 47–49),
zmienianej bez wdrożenia WAR (FR-11, FR-13). BR-05 nakłada obowiązek zastępstwa przez adwokata lub radcę prawnego
(art. 44 ust. 1) z wyjątkami zawodowymi.

## Decyzja

1. Lista wymogów to tabela DMN `bpmn/wymogi-formalne.dmn` (decyzja `Dec_WymogiFormalne`, polityka COLLECT): wejścia
   `rodzajPisma`, `pelnomocnikZawodowy`, `skarzacyZwolniony`; wyjścia `kod`, `wymog`, `podstawa`. Tabela jest wdrażana
   razem z WAR przez process application, ale nową wersję można wdrożyć osobno (Cockpit → Deploy) bez zmian w kodzie.
2. `FormalRequirementsService` ocenia tabelę przez `DecisionService` i zawiera regułę BR-05 (`representationSatisfied`).
3. Treść wymogów w tabeli jest **projektem** opartym na brzmieniu ustawy; przed produkcją potwierdza ją Biuro Służby
   Prawnej. Zmiana treści nie wymaga zmiany kodu ani testów logiki (testy sprawdzają kody i podstawy prawne).

## Konsekwencje

- Lista kontrolna w S-08 ładuje wymogi z DMN, a stan (spełniony / brak / do sprawdzenia) i komentarze zapisuje w aktach
  jako czynność referenta; braki trafiają do treści zarządzenia (T-026).
- HTTL definicji decyzji jest aktualizowany razem z procesami (`HistoryTimeToLive`).
