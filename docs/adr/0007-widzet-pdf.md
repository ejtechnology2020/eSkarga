# ADR 0007 – Widżet PDF: własny komponent na pdfjs-dist zamiast pe:documentViewer (propozycja, T-034 [decyzja])

- Status: **propozycja do zatwierdzenia**
- Data: 3.11.2026

## Porównanie
| Kryterium | `pe:documentViewer` (PrimeFaces Extensions) | własny komponent `tk:pdfViewer` (pdfjs-dist, webjar) |
| --- | --- | --- |
| Widok dzielony (dwa dokumenty) | brak; dwie instancje bez wspólnej nawigacji | tak, dwie instancje jednego widżetu z osobnym stanem |
| Nakładka zaznaczeń i komentarzy (FR-24, T-035) | brak API | warstwa `highlights` na canvasie, JSON ze współrzędnymi |
| Numer karty akt obok numeru strony | brak | tak |
| Skróty z briefu (J/K, N/P, \, Ctrl+F) | częściowo | tak |
| Worker z własnej domeny, `isEvalSupported:false` (CSP) | konfiguracja viewer.html | tak, bez `viewer.html` |
| Utrzymanie | gotowe | ~600 linii JS, testy Playwright |

## Decyzja tymczasowa
Własny komponent kompozytowy `tk:pdfViewer` (`resources/tk/pdfViewer.xhtml` + `resources/js/pdf-viewer.js`), biblioteka
`org.webjars.npm:pdfjs-dist` serwowana przez JSF resource handler, dokumenty strumieniowane z `/akta/{caseId}/dokument/{docId}` (HTTP Range, `no-store`).
`pe:documentViewer` pozostaje możliwy dla prostego podglądu (np. etykieta), bez widoku dzielonego.
