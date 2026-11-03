# ADR 0014 – Zdarzenia odmowy w dzienniku zapisywane w osobnej transakcji

Status: propozycja (do potwierdzenia po zakończeniu prac)

## Kontekst

BR-10 i D4 wymagają, by każda odmowa (brak upoważnienia do czynności Prezesa TK, akceptacja korekty bez roli
kierownika, odczyt akt bez przydziału) zostawiła ślad w dzienniku zdarzeń. Odmowa jest sygnalizowana wyjątkiem
`SecurityException`, który wycofuje transakcję biznesową – a z nią zwykły wpis audytu.

## Decyzja

`AuditService.recordDenial(...)` działa w `REQUIRES_NEW` i jest używane wyłącznie dla odmów. Pozostałe zdarzenia
(`record`) pozostają w transakcji czynności: gdy czynność się nie powiedzie, jej wpis audytu także znika, co
odzwierciedla stan faktyczny. Łańcuch skrótów (NFR-02) pozostaje ciągły, bo singleton z blokadą zapisu czyta ostatni
skrót pod blokadą w każdej transakcji.

## Konsekwencje

- W testach bez kontenera `recordDenial` jest nadpisywane tak, by użyć osobnego `EntityManager` (odpowiednik
  `REQUIRES_NEW`), patrz `ServiceTestSupport`.
- Wysyłka do SIEM (T-015) korzysta z tej samej tabeli jako outboxu, więc odmowy trafią do SIEM nawet przy wycofanej
  czynności.
