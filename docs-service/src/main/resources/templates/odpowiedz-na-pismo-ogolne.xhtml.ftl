<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" lang="pl">
<head>
  <meta charset="UTF-8" />
  <title>Odpowiedź na pismo – ${numerDziennika}</title>
  <style>
    @page { size: A4; margin: 25mm 20mm 25mm 25mm; @bottom-center { content: "Strona " counter(page) " z " counter(pages); font-family: "Liberation Serif", serif; font-size: 9pt; } }
    body { font-family: "Liberation Serif", serif; font-size: 12pt; line-height: 1.4; }
    h1 { text-align: center; font-size: 14pt; letter-spacing: .08em; margin: 24pt 0 12pt; }
    .r { text-align: right; } .c { text-align: center; font-weight: bold; } .sign { margin-top: 36pt; text-align: right; }
    .sklad { margin: 12pt 0; } .sklad li { list-style: none; } ol { padding-left: 18pt; } p { margin: 6pt 0; text-align: justify; }
  </style>
</head>
<body>
  <p class="r">${miejscowosc}, dnia ${data} r.</p>
  <p>Nr ${numerDziennika}</p>
  <p>${adresatMianownik}<br />${adresAdresata}</p>
  <p>W odpowiedzi na pismo z dnia ${dataWplywu} r. (${tematPisma}) Trybunał Konstytucyjny informuje, że pismo nie stanowi
     skargi konstytucyjnej w rozumieniu art. 79 Konstytucji i nie podlega rozpoznaniu w postępowaniu przed Trybunałem.</p>
  <p>Skarga konstytucyjna może być wniesiona po wyczerpaniu drogi prawnej, w terminie 3 miesięcy od doręczenia skarżącemu
     prawomocnego wyroku, ostatecznej decyzji lub innego ostatecznego rozstrzygnięcia (art. 77 ust. 1 ustawy z dnia 30 listopada
     2016 r. o organizacji i trybie postępowania przed Trybunałem Konstytucyjnym). Skargę sporządza adwokat lub radca prawny
     (art. 44 ust. 1 ustawy), a jej wymogi określa art. 53 ustawy.</p>
  <p class="sign">${referent}<br />Wydział Weryfikacyjny Biura Służby Prawnej</p>
</body>
</html>
