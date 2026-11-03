<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" lang="pl">
<head>
  <meta charset="UTF-8" />
  <title>Postanowienie o pozostawieniu zażalenia bez rozpoznania – ${sygnatura}</title>
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
  <p>Trybunał Konstytucyjny w składzie:</p>
  <ul class="sklad">
    <#list sklad as s><li>${s}<#if s == przewodniczacy> – przewodniczący</#if></li></#list>
  </ul>
  <p>po rozpoznaniu na posiedzeniu niejawnym zażalenia ${skarzacy} na postanowienie o odmowie nadania dalszego biegu ${rodzajPisma},</p>
  <p class="c">postanawia:</p>
  <p>pozostawić zażalenie bez rozpoznania.</p>
  <h1>UZASADNIENIE</h1>
  <p>Postanowienie o odmowie nadania dalszego biegu doręczono w dniu ${dataDoreczenia} r. Zażalenie wniesiono po upływie
     siedmiodniowego terminu z art. 61 ust. 5 ustawy z dnia 30 listopada 2016 r. o organizacji i trybie postępowania przed
     Trybunałem Konstytucyjnym (Dz. U. z 2019 r. poz. 2393).</p>
  <#list uzasadnienie as akapit>
  <p>${akapit}</p>
  </#list>
  <p class="sign">Skład orzekający</p>
</body>
</html>
