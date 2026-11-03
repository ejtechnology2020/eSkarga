# ADR 0004 – Beany widoków zasilane danymi przykładowymi do czasu usług domenowych

- Status: przejściowe (do zakończenia T-010 – T-016)
- Data: 3.11.2026

## Decyzja

Front (27 ekranów z makiet strony „eSkarga”) powstał przed modelem JPA i usługami domenowymi. Beany `@ViewScoped`
w `web.view` czytają rekordy widoku z `web.demo.DemoDataset` (dane fikcyjne z briefu §9, „dziś” = 03.11.2026).
Rekordy w `web.model` są kontraktem między ekranem a przyszłymi usługami: zadania (`TaskRow`), sprawy (`CaseSummary`),
terminy (`DeadlineView`), spis akt (`RecordPage`) itd.

## Konsekwencje

- Zadania T-010 – T-016 podmieniają źródło danych w beanach bez zmian w Facelets.
- `DemoDataset` zostaje jako fixture testów UI (Playwright) w trybie `eskarga.security.mode=demo`.
- Parametry `?stan=`, `?widok=`, `?rola=` pokazują warianty ekranów z makiet (formularz pusty, błąd walidacji,
  potwierdzenie, widok referenta, wersja zanonimizowana, sprawa zakończona); są aktywne tylko w trybie demo.
