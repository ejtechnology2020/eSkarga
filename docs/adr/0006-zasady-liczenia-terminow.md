# ADR 0006 – Zasady liczenia terminów 7-dniowych i 3-miesięcznych (propozycja, T-025 [decyzja])

- Status: **propozycja do potwierdzenia przez Biuro Służby Prawnej**
- Data: 3.11.2026

## Decyzja tymczasowa (zaimplementowana w `DeadlineCalculator`, 40 testów)
1. Termin w dniach (braki, zażalenie – art. 61 ust. 3 i 5) biegnie od dnia następnego po doręczeniu; dnia doręczenia nie wlicza się.
2. Koniec przypadający na sobotę, niedzielę, święto ustawowe albo dzień wolny w Trybunale przesuwa się na najbliższy dzień roboczy
   (odpowiednio art. 115 k.c. i art. 165 § 1 k.p.c. przez art. 36 ustawy o TK).
3. Termin 3 miesięcy (art. 77 ust. 1) kończy się w dniu odpowiadającym dacie doręczenia orzeczenia; gdy w miesiącu nie ma takiego dnia –
   w ostatnim dniu miesiąca; zasada z pkt 2 stosuje się odpowiednio.
4. Wniosek o pełnomocnika z urzędu (art. 44 ust. 3) wstrzymuje bieg terminu z art. 77 od dnia złożenia do dnia doręczenia rozstrzygnięcia
   sądu; dni wstrzymania są doliczane.
5. Data wniesienia = data nadania u operatora wyznaczonego albo data UPO (art. 165 § 2 k.p.c. odpowiednio), nie data wpływu.
6. Doręczenie zastępcze (awizo): data skutku doręczenia = 14. dzień od pierwszego awiza, chyba że odbiór nastąpił wcześniej.
7. Okres oczekiwania na pisma nadane pocztą przed uznaniem terminu za bezskuteczny: 7 dni (konfigurowalny `eskarga.deadline.postalGraceDays`).
8. Kalendarz: święta ustawowe liczone w kodzie (`PolishHolidays`), dni wolne w Trybunale z tabeli `holiday` (S-24).

## Do potwierdzenia
Punkty 2, 6 i 7 oraz praktyka TK przy terminie z art. 77 (czy system w ogóle ma go sygnalizować poza listą kontrolną).
System wyłącznie sygnalizuje termin; ocena należy do sędziego (D6, FR-12).
