<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" lang="pl">
<head>
  <meta charset="UTF-8" />
  <title>Zarządzenie o wyznaczeniu sędziego – ${sygnatura}</title>
  <style>
    @page { size: A4; margin: 25mm 20mm 25mm 25mm; @bottom-center { content: "Strona " counter(page) " z " counter(pages); font-family: "Liberation Serif", serif; font-size: 9pt; } }
    body { font-family: "Liberation Serif", serif; font-size: 12pt; line-height: 1.4; }
    h1 { text-align: center; font-size: 14pt; letter-spacing: .08em; margin: 24pt 0 12pt; }
    .r { text-align: right; } .c { text-align: center; font-weight: bold; } .sign { margin-top: 36pt; text-align: right; }
    .sklad { margin: 12pt 0; } .sklad li { list-style: none; } ol { padding-left: 18pt; } p { margin: 6pt 0; text-align: justify; }
  </style>
</head>
<body>
  <p class="r">${miejscowosc}, dnia ${dataZarzadzenia} r.</p>
  <p>Sygn. akt ${sygnatura}</p>
  <h1>ZARZĄDZENIE</h1>
  <p>Prezes Trybunału Konstytucyjnego, na podstawie art. 61 ust. 1 ustawy z dnia 30 listopada 2016 r. o organizacji i trybie
     postępowania przed Trybunałem Konstytucyjnym (Dz. U. z 2019 r. poz. 2393),</p>
  <p class="c">zarządza:</p>
  <p>wyznaczyć sędziego Trybunału Konstytucyjnego ${sedzia} do przeprowadzenia wstępnej kontroli ${rodzajPisma}
     w sprawie o sygn. akt ${sygnatura}.</p>
  <p class="sign">Prezes Trybunału Konstytucyjnego</p>
</body>
</html>
