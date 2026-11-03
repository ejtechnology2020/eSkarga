<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" lang="pl">
<head>
  <meta charset="UTF-8" />
  <title>Zarządzenie o usunięciu braków – ${sygnatura}</title>
  <style>
    @page { size: A4; margin: 25mm 20mm 25mm 25mm; @bottom-center { content: "Strona " counter(page) " z " counter(pages); font-family: "Liberation Serif", serif; font-size: 9pt; } }
    body { font-family: "Liberation Serif", serif; font-size: 12pt; line-height: 1.4; }
    h1 { text-align: center; font-size: 14pt; letter-spacing: .08em; margin: 24pt 0 12pt; }
    .r { text-align: right; } .c { text-align: center; font-weight: bold; } .sign { margin-top: 36pt; text-align: right; }
    ol { padding-left: 18pt; }
  </style>
</head>
<body>
  <p class="r">Warszawa, dnia ${dataZarzadzenia} r.</p>
  <p>Sygn. akt ${sygnatura}</p>
  <h1>ZARZĄDZENIE</h1>
  <p>Sędzia Trybunału Konstytucyjnego ${sedzia}, na podstawie art. 61 ust. 3 ustawy z dnia 30 listopada 2016 r.
     o organizacji i trybie postępowania przed Trybunałem Konstytucyjnym (Dz. U. z 2019 r. poz. 2393),</p>
  <p class="c">zarządza:</p>
  <p>wezwać ${adresat} do usunięcia, w terminie 7 dni od dnia doręczenia niniejszego zarządzenia, następujących braków formalnych
     skargi konstytucyjnej ${skarzacy}:</p>
  <ol>
    <#list braki as brak>
    <li>${brak}</li>
    </#list>
  </ol>
  <p>Nieusunięcie braków w terminie spowoduje odmowę nadania skardze dalszego biegu (art. 61 ust. 4 pkt 3 ustawy).</p>
  <p class="sign">${sedzia}<br />sędzia Trybunału Konstytucyjnego</p>
</body>
</html>
