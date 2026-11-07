# ADR 0027: Słownik sędziów jako źródło wyznaczeń i składów

Data: 07.11.2026. Status: zaakceptowany.

## Kontekst

Listę sędziów do wyznaczenia (S-17) budował rejestr osób, które zalogowały się do eSkargi z rolą `TK_SEDZIA` z ID-users (S-22).
Nie mówił on nic o kadencji ani o funkcji (Prezes, Wiceprezes), a skład rozpoznający zażalenie (S-18) był makietą. Rola w ID-users
oznacza uprawnienie do pracy w systemie, nie fakt sprawowania urzędu.

## Decyzja

1. **Słownik sędziów** (`judge`, S-24 → „Sędziowie”, tylko TK_ADMIN): login z ID-users (stały), imię i nazwisko do pism, funkcja
   (`PREZES`, `WICEPREZES`, `SEDZIA`), kadencja od–do (9 lat, art. 194 ust. 1 Konstytucji; koniec może być nieznany), stan aktywny.
   Wpis wycofuje się, nie usuwa (sprawy i zarządzenia odwołują się do loginu). Każda zmiana w dzienniku zdarzeń.
2. **Jedyne źródło kandydatów**: S-17 i S-18 pokazują wyłącznie sędziów z aktywnym wpisem i w kadencji w dniu czynności;
   `JudgeAssignmentService.draftOrder` i `AppealService.referToPanel` odrzucają login spoza słownika, wycofany albo poza kadencją
   (czytelny komunikat). Nazwa do zarządzenia pochodzi ze słownika („sędzia TK …”, „Prezes TK …”).
3. **Skład** (UC-11): 3 różnych sędziów ze słownika, sędzia odmowy wykluczony (BR-07), przewodniczący i sprawozdawca spośród
   wybranych (kolejność w `appeal.panel_user_ids`: przewodniczący, sprawozdawca, członek); Prezes/Wiceprezes podpowiadany jako
   przewodniczący. Skierowanie wymaga upoważnienia (BR-10) – niezmienione.
4. Funkcja Prezesa w słowniku **nie zastępuje upoważnień** z S-23 (BR-10): kto działa „za Prezesa”, nadal wynika z upoważnienia.

## Konsekwencje

- Migracje V12 i V12_1; `JudgeDictionaryService`, `JudgeRow`, `AccountRow` (podpowiedź loginu z rejestru S-22).
- Nowe środowisko wymaga wpisania sędziów do słownika, zanim Prezes wyznaczy pierwszą sprawę; S-17 pokazuje wskazówkę.
- Testy seedują sędziów testowych (`ServiceTestSupport.seedJudges`); GUI: UC-24 (słownik) przed UC-08.
