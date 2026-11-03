<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" lang="pl">
<head>
  <meta charset="UTF-8" />
  <title>Postanowienie o odmowie nadania dalszego biegu – ${sygnatura}</title>
  <style>
    @page { size: A4; margin: 25mm 20mm 25mm 25mm; @bottom-center { content: "Strona " counter(page) " z " counter(pages); font-family: "Liberation Serif", serif; font-size: 9pt; } }
    body { font-family: "Liberation Serif", serif; font-size: 12pt; line-height: 1.4; }
    h1 { text-align: center; font-size: 14pt; letter-spacing: .08em; margin: 24pt 0 12pt; }
    .r { text-align: right; } .c { text-align: center; font-weight: bold; } .sign { margin-top: 36pt; text-align: right; }
    .sklad { margin: 12pt 0; } .sklad li { list-style: none; } ol { padding-left: 18pt; } p { margin: 6pt 0; text-align: justify; }
  </style>
</head>
<body>
  <p class="r">${miejscowosc}, dnia ${dataPostanowienia} r.</p>
  <p>Sygn. akt ${sygnatura}</p>
  <h1>POSTANOWIENIE</h1>
  <p>Trybunał Konstytucyjny w składzie: sędzia TK ${sedzia}, po wstępnym rozpoznaniu na posiedzeniu niejawnym
     ${rodzajPisma} ${skarzacy} w sprawie zgodności ${przedmiot} z ${wzorzec},</p>
  <p class="c">postanawia:</p>
  <p>odmówić nadania dalszego biegu ${(rodzajPismaMianownik == "skarga konstytucyjna")?then("skardze konstytucyjnej", "wnioskowi")}.</p>
  <h1>UZASADNIENIE</h1>
  <#list uzasadnienie as akapit>
  <p>${akapit}</p>
  </#list>
  <p>Podstawą odmowy jest art. 61 ust. 4 pkt ${podstawaOdmowy} ustawy z dnia 30 listopada 2016 r. o organizacji i trybie postępowania
     przed Trybunałem Konstytucyjnym (Dz. U. z 2019 r. poz. 2393):
     <#switch podstawaOdmowy>
       <#case 1>skarga nie spełnia wymagań określonych w ustawie (braki nieusuwalne);<#break>
       <#case 2>skarga jest oczywiście bezzasadna;<#break>
       <#default>braki formalne nie zostały usunięte w terminie.
     </#switch>
  </p>
  <p>Na niniejsze postanowienie przysługuje zażalenie do Trybunału Konstytucyjnego w terminie 7 dni od dnia jego doręczenia
     (art. 61 ust. 5 ustawy).</p>
  <p class="sign">${sedzia}<br />sędzia Trybunału Konstytucyjnego</p>
</body>
</html>
