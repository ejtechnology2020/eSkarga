# ADR 0003 – Teksty interfejsu: pakiet komunikatów a szablony Facelets

- Status: przyjęte 3.11.2026
- Data: 3.11.2026

## Kontekst

Konwencja z `CLAUDE.md`: „Teksty UI trzymasz po polsku w `messages_pl.properties`, nigdy w kodzie”.
Interfejs jest wyłącznie polski (brak drugiego języka), a 27 ekranów zawiera setki zdań opisowych.

## Decyzja

- Kod Java nie zawiera tekstów interfejsu: etykiety statusów, flag, terminów, ról, ekranów, grup menu, komunikaty
  walidacji i ekranów systemowych są w `pl/ejtechnology/tk/eSkarga/web/messages_pl.properties` i czytane przez
  `#{msg[...]}` oraz `Fmt.msg(...)`. Logika odmiany („za 6 dni”, „jutro”, „po terminie”) używa wzorców z pakietu.
- Szablony Facelets (`.xhtml`) są warstwą prezentacji i mogą zawierać stałą polską treść ekranu (nagłówki sekcji,
  zdania celu ekranu, podpowiedzi), tak jak makiety. Teksty powtarzalne i współdzielone między ekranami idą do pakietu.
- Dane przykładowe (nazwiska fikcyjne, sygnatury, daty) w `DemoDataset` są danymi, nie tekstami UI.

## Konsekwencje

- Zmiana brzmienia statusu albo roli to jedna linia w pakiecie komunikatów.
- Gdyby pojawił się drugi język (np. portal dla uczestników), stałe teksty ekranów trzeba przenieść do pakietu.
