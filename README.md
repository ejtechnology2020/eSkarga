# eSkarga – Repertorium Ts/Tw

System repertoryjno-biurowy Trybunału Konstytucyjnego do obsługi skarg konstytucyjnych (Ts) i wniosków (Tw)
na etapie wstępnej kontroli (art. 61 ustawy o organizacji i trybie postępowania przed TK).

| | |
| --- | --- |
| Platforma | Java 21, Jakarta EE 11, WildFly 41 (dystrybucja Operaton 2.2), PostgreSQL 17 |
| Interfejs | Jakarta Faces 4.1 + PrimeFaces 15 – 27 ekranów według makiet „Podgląd akt sprawy Ts/Tw — warianty makiet”, strona „eSkarga” |
| Proces | BPMN „Wstępna kontrola Ts/Tw” (`app/src/main/resources/bpmn/wstepna-kontrola.bpmn`) z testami każdej ścieżki |
| Dokumentacja | `doc/CLAUDE.md` (zasady), `doc/TASKS.md` (zadania i notatki), `docs/adr/` (decyzje) |

## Budowanie

```bash
mvn -B verify
```

Wynik: `app/target/eskarga.war`, `docs-service/target/eskarga-docs-service.war`, `workers/target/eskarga-workers.jar`.

## Środowisko deweloperskie

Opis w `infra/docker/README.md`. Aplikacja: `http://localhost:8080/eskarga/`.

## Ekrany (strona „eSkarga” makiet)

| ID | Ekran | Ścieżka |
| --- | --- | --- |
| S-01 | Pulpit zadań (warianty ról) | `/pulpit.xhtml` |
| S-02 | Wyszukiwarka spraw i dokumentów | `/wyszukiwarka.xhtml` |
| S-03 – S-05 | Rejestracja wpływu, dziennik wpływu, etykieta | `/wplyw/*.xhtml` |
| S-06 – S-09 | Kolejka pism, założenie sprawy, repertorium, korekta | `/sprawy/*.xhtml` |
| S-10 – S-13, S-27 | Ekspedycja, zażalenia, pisma ogólne, wgląd, publikacja | `/rejestry/*.xhtml` |
| S-14 | Podgląd akt sprawy | `/akta/podglad.xhtml?sygnatura=Ts 312/26` |
| S-15 – S-18 | Edytor pisma, podpis, wyznaczenie sędziego, skład | `/czynnosci/*.xhtml` |
| S-19 – S-21 | Monitor terminów, raporty, archiwum | `/nadzor/*.xhtml` |
| S-22 – S-25 | Użytkownicy, upoważnienia, słowniki, dziennik zdarzeń | `/admin/*.xhtml` |
| S-26 | Ekrany systemowe | `/blad/*.xhtml` |
| – | Arkusz komponentów (dev) | `/dev/komponenty.xhtml` |

Dane w interfejsie są wyłącznie fikcyjne (brief §9); „dziś” w trybie demo to 03.11.2026.
