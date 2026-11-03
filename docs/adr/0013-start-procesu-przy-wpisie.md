# ADR 0013 – Start procesu w chwili zatwierdzenia wpisu; zadania rejestracyjne domykane przez adapter

Status: zaakceptowana 03.11.2026 (start procesu przy wpisie)

## Kontekst

Model `wstepna-kontrola.bpmn` zaczyna się zadaniami „Zarejestruj wpływ pisma” (T_Wplyw) i „Wpisz sprawę do
repertorium” (T_Wpis). Decyzja D1 wymaga, by nadanie sygnatury, wpis i start procesu były jedną transakcją JTA –
proces nie może więc istnieć przed wpisem, a gdy już istnieje, oba pierwsze zadania są faktycznie wykonane.

## Decyzja

1. `PreliminaryReviewProcess.start` startuje instancję kluczem biznesowym = sygnatura i natychmiast domyka zadania
   T_Wplyw i T_Wpis (`TaskService.complete`) w tej samej transakcji. Instancja czeka na T_Wyznacz.
2. Model BPMN zostaje bez zmian (zgodny z `doc/wstepna-kontrola.bpmn` i dokumentacją procesu), dzięki czemu historia
   procesu nadal pokazuje pełną ścieżkę od wpływu.
3. Komunikat „Pismo uzupełniające braki” jest korelowany `correlateAllWithResult()` – zwykłe pismo do sprawy,
   gdy proces nie czeka na uzupełnienie, nie przerywa transakcji (metoda zwraca `false`).

## Konsekwencje

- Pismo ogólne (ścieżka T_Ogolne) nie przechodzi przez `CaseFileService`; obsługuje je `GeneralLetterService` bez
  instancji procesu (proces dotyczy tylko spraw Ts/Tw).
- Alternatywa odrzucona: przeniesienie zdarzenia startowego za T_Wpis – wymagałaby zmiany modelu uzgodnionego z TK.
