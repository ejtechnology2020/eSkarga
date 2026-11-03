# ADR 0012 – Kategorie archiwalne i okresy retencji w konfiguracji (T-052)

- Status: przyjęte 3.11.2026; wartości do potwierdzenia z archiwum zakładowym TK

## Decyzja
Kategoria archiwalna i okres przechowywania nie są zapisane w kodzie. Tabela `archive_category` (V3) przechowuje mapowanie
sposobu zakończenia sprawy na kategorię (np. odmowa prawomocna → B10, nadanie biegu → A przy aktach SK) i okres w latach.
Wartości początkowe w migracji są propozycją; zmienia je administrator merytoryczny (S-24) z zapisem w dzienniku zdarzeń.
`historyTimeToLive` procesu i retencja repozytorium czytają ten sam słownik.
