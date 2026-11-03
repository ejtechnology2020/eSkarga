# System repertoryjno-biurowy Ts/Tw – decyzje architektoniczne (stan: 3.11.2026)

Dokumenty źródłowe:
- Analiza wymagań i przypadków użycia: https://claude.ai/artifact/PbiX9GMeFWy9iSGmPRV4k2
- Brief dla Claude Design – podgląd akt: https://claude.ai/artifact/Y6WjxBhX4nX3o9V4S7sX6z
- Brief dla Claude Design – wszystkie makiety (27 ekranów, 4 paczki, prompty): https://claude.ai/artifact/VcpiQ9emQKcpQ4o4EjL5bm
- Architektura rozwiązania: https://claude.ai/artifact/Urf7aJfJ7myMULA7yvLvvz
- Pakiet dla Claude Code: claude-code/CLAUDE.md i claude-code/TASKS.md (42 zadania w 6 etapach)
- Model BPMN wstępnej kontroli: plik tk_wstepna_kontrola_Ts_Tw.bpmn (przekazany w rozmowie)

Konwencja kodu: pakiet bazowy `pl.ejtechnology.tk.eSkarga`, groupId `pl.ejtechnology.tk`, artifactId `eskarga-*` (3.11.2026).

Decyzje:
- D1 Operaton 2.2 osadzony w aplikacji, wspólna baza PostgreSQL i transakcja z repertorium.
- D2 Frontend: JSF/CDI/EJB + PrimeFaces 15 (zatwierdzone 3.11.2026). PrimeNG odrzucony m.in. z powodu licencji PrimeUI od v22.
- D3 ID-users jako dostawca OIDC (Authorization Code + PKCE), aplikacja jako klient poufny, tokeny tylko na serwerze.
- D4 Role ogólne z ID-users; uprawnienia do spraw i upoważnienia w aplikacji.
- D5 Dokumenty w repozytorium S3 (Object Lock), PDF/A, OCR, weryfikacja podpisów EU DSS po stronie serwera.
- D6 Terminy liczy domena; timery BPMN tylko przypominają i eskalują.
- D7 Integracje jako external tasks obsługiwane przez osobne workery.
- D8 Zmienne procesu bez danych osobowych.
- D9 WildFly 41 z dystrybucją Operaton (silnik współdzielony), aplikacja jako process application (3.11.2026).
- D10 Podpis kwalifikowany sędziów kartą przez aplikację lokalną, podpis dwuetapowy DSS (3.11.2026).
- D11 Produkcja i przedprodukcja na VM (Ansible), dev i test w Docker Compose; jeden WAR wszędzie (3.11.2026).
- D12 Nowe repozytorium S3 z Object Lock (propozycja Ceph RGW; MinIO CE odrzucone – zarchiwizowane) i nowa para HSM z PKCS#11, CC EN 419 221-5 (3.11.2026).
- D13 Pisma z szablonów: Apache FreeMarker 2.3.x (XHTML) → OpenHTMLtoPDF (io.github.openhtmltopdf) → PDF/A-2b → veraPDF, w usłudze dokumentów; szablony w bazie z wersjami, FreeMarker w trybie piaskownicy (3.11.2026).

Otwarte: kontrakt OIDC ID-users, aplikacja lokalna do podpisu (SDK dostawcy czy własna), wybór produktów S3 i HSM, API e-Doręczeń i operatora pocztowego, interfejs repertoriów SK/K/U, kategorie archiwalne, czcionka urzędowa do pism (licencja na osadzanie).
