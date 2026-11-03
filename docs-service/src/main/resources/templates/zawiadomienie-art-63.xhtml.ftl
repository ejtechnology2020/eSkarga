<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" lang="pl">
<head>
  <meta charset="UTF-8" />
  <title>Zawiadomienie o nadaniu dalszego biegu – ${sygnatura}</title>
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
  <p>Sygn. akt ${sygnatura}<br />Sygn. akt po nadaniu biegu: ${sygnaturaPowiazana}</p>
  <h1>ZAWIADOMIENIE</h1>
  <p>Na podstawie art. 63 ustawy z dnia 30 listopada 2016 r. o organizacji i trybie postępowania przed Trybunałem Konstytucyjnym
     (Dz. U. z 2019 r. poz. 2393) Trybunał Konstytucyjny zawiadamia o nadaniu dalszego biegu ${rodzajPisma} ${skarzacy}
     w sprawie zgodności ${przedmiot} z ${wzorzec}.</p>
  <p>Zawiadomienie otrzymują:</p>
  <ol>
    <#list odbiorcyZawiadomienia as o><li>${o}</li></#list>
  </ol>
  <p>Uczestnicy postępowania mogą zgłosić udział w postępowaniu i przedstawić stanowisko w sprawie.</p>
  <p class="sign">Prezes Trybunału Konstytucyjnego</p>
</body>
</html>
