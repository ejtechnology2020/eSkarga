# Role, ekrany i uprawnienia (D4)

Uprawnienie w eSkardze to trzy warstwy; rola jest najgrubszą z nich.

| Warstwa | Źródło | Gdzie egzekwowane |
| --- | --- | --- |
| 1. Rola ogólna `TK_*` | token ID-users (`resource_access.eskarga.roles`) | `LoginFilter` (wymagana ≥ 1 rola), `ScreenAccessFilter` (ekrany z inwentarza `Screen`), `@RequiresRole` na usługach EJB (`RoleCheckInterceptor`, odmowa w dzienniku zdarzeń) |
| 2. Przydział do sprawy | `case_assignment` (JUDGE, TEAM, CLERK) | `CaseAccessPolicy.canReadCase` / `canReadInternalNotes` przy każdym odczycie akt, dokumentu, notatek |
| 3. Upoważnienie | `delegation` („wykonuje obowiązki Prezesa”, BR-10) | `DelegationService.requireActingAsPresident` przy wyznaczeniu sędziego i składu |

Reguły niezależne od ról: zasada dwóch osób (aktywacja szablonu, zatwierdzenie korekty), podpis zarządzenia o wyznaczeniu tylko przez autora projektu, brak zmian w sprawie zakończonej.

## Ekrany według ról (`Screen`)

| Ekran | REFERENT | ASYSTENT | SEDZIA | PREZES | KIEROWNIK | BIURO_PODAWCZE | ARCHIWISTA | ADMIN |
| --- | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
| S-01 Pulpit, S-02 Wyszukiwarka | ● | ● | ● | ● | ● | ● | ● | ● |
| S-03 Rejestracja wpływu, S-05 Etykieta | | | | | | ● | | |
| S-04 Dziennik wpływu | ● | | | | ● | ● | | |
| S-06 Kolejka kwalifikacji, S-07 Założenie sprawy | ● | | | | | | | |
| S-08 Repertorium | ● | ● | ● | ● | ● | | | |
| S-09 Korekta wpisu | ● | | | | ● | | | |
| S-14 Akta sprawy | ● | ● | ● | ● | ● | | | |
| S-15 Edytor pisma | | ● | ● | ● | | | | |
| S-16 Podpis | | | ● | ● | | | | |
| S-17 Wyznaczenie sędziego, S-18 Skład | | | | ● | | | | |
| S-10 Ekspedycja, S-12 Pisma ogólne, S-13 Wgląd, S-27 Publikacja | ● | | | | | | | |
| S-11 Zażalenia | ● | | | ● | ● | | | |
| S-19 Monitor | | | | | ● | | | |
| S-20 Raporty | | | | ● | ● | | | |
| S-21 Archiwum | | | | | | | ● | |
| S-22 Użytkownicy, S-24 Słowniki i szablony, S-25 Dziennik zdarzeń | | | | | | | | ● |
| S-23 Upoważnienia | | | | ● | | | | ● |

Dostęp do ekranu nie oznacza dostępu do każdej sprawy: sędzia i asystent widzą tylko sprawy swojego zespołu (warstwa 2), a wyszukiwarka i repertorium filtrują wyniki tą samą polityką. **TK_ADMIN** (administrator techniczny) nie czyta akt ani repertorium – rola sama w sobie nie daje dostępu do żadnej sprawy; jeśli konto ma w ID-users także rolę merytoryczną, liczy się ta druga.

## Usługi EJB (`@RequiresRole`)

- Klasa: domyślnie dowolna rola eSkargi (odczyty). Metoda zmieniająca stan ma własną regułę; `ServiceRoleGuardTest` nie przepuszcza metody bez niej.
- `workers = true` – metoda dostępna także z API workerów (`/api`, token `eskarga-worker`): przetwarzanie dokumentów, potwierdzenia doręczeń, dane sprawy do przekazania.
- Kontrola dotyczy wejścia do warstwy usług (bean, serwlet, API → usługa). Wywołania usługa → usługa w tej samej operacji (podpis → wyznaczenie → zmiana statusu, API workera → termin doręczenia) nie są sprawdzane ponownie.
- `system = true` – wywołanie z infrastruktury bez użytkownika (nasłuch procesu `TransferCompletedListener` → `CaseFileService.markTransferred`).
- Poza kontrolą ról (lista `INTERNAL` w teście): dziennik zdarzeń, liczniki sygnatur, polityka dostępu, rejestr osób, proces, wyszukiwanie – wołane przed ustaleniem ról albo bez użytkownika.

| Operacja | Role |
| --- | --- |
| Rejestracja wpływu, skan pisma | BIURO_PODAWCZE, REFERENT |
| Kwalifikacja pisma, założenie sprawy, dołączenie pisma do akt, przekazanie do SK/K/U | REFERENT (dołączenie także BIURO_PODAWCZE) |
| Korekta wpisu: wniosek | REFERENT, KIEROWNIK; zatwierdzenie/odrzucenie – KIEROWNIK |
| Lista kontrolna | SEDZIA, ASYSTENT, REFERENT, KIEROWNIK (tylko zespół sprawy) |
| Projekt pisma | ASYSTENT, SEDZIA, PREZES |
| Podpis | SEDZIA, PREZES |
| Wyznaczenie sędziego (projekt, zarządzenie) | PREZES, SEDZIA z upoważnieniem (BR-10) |
| Zażalenie: rejestracja – REFERENT; skład – PREZES; rozstrzygnięcie – PREZES, SEDZIA | |
| Doręczenia: ekspedycja, zwrotki, zwroty | REFERENT, BIURO_PODAWCZE, workery; korekta daty doręczenia – REFERENT, KIEROWNIK |
| Notatki wewnętrzne, zaznaczenia | SEDZIA, ASYSTENT, PREZES (tylko zespół sprawy) |
| Wgląd do akt, publikacja | REFERENT, KIEROWNIK |
| Pisma ogólne | REFERENT |
| Archiwizacja | ARCHIWISTA |
| Szablony pism, słowniki, dni wolne | ADMIN |
| Upoważnienia | ADMIN, PREZES |
