import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { expect, test, type Page } from '@playwright/test';
import { ESKARGA, HASLO, LOGIN, WLACZONE, ZAKLADA_KONTO, dzis, jsf, kontoTestowe, pdfTestowy, zaloguj } from './srodowisko';

/**
 * Procesy eSkargi od końca do końca w trybie OIDC (ID-users + Operaton + PostgreSQL + S3) – T-058, ADR 0020:
 * logowanie z kodem jednorazowym, rejestracja pisma ze skanem (S-03, UC-01), kolejka kwalifikacji z podglądem
 * skanu (S-06), założenie sprawy z sygnaturą bez luk (S-07, BR-01), akta (S-14), repertorium (S-08),
 * dziennik wpływu (S-04), przełącznik roli, wylogowanie (koniec sesji SSO). Jeden kontekst przeglądarki,
 * kroki po kolei – każdy korzysta z danych poprzedniego (numer DW, sygnatura).
 *
 * Uruchomienie: ESKARGA_E2E=1 ESKARGA_E2E_HASLO=… [ESKARGA_E2E_TOTP=…|ESKARGA_E2E_KC_ADMIN_HASLO=…] npx playwright test --project oidc
 */
test.describe.serial('Procesy eSkargi (OIDC)', () => {
  test.skip(!WLACZONE, 'ESKARGA_E2E=1 włącza testy w trybie OIDC (aplikacja na WildFly obok ID-users)');
  test.describe.configure({ timeout: 120_000 });

  let page: Page;
  let numerDw = '';
  let sygnatura = '';
  const skarzacy = 'Jan Przykładowy';

  test.beforeAll(async ({ browser }) => {
    if (ZAKLADA_KONTO) await kontoTestowe();
    page = await (await browser.newContext({ locale: 'pl-PL', viewport: { width: 1366, height: 768 } })).newPage();
  });

  test.afterAll(async () => {
    await page?.context().close();
  });

  test('UC-00 logowanie przez ID-users z kodem jednorazowym; żaden token nie trafia do przeglądarki', async () => {
    await zaloguj(page, '/pulpit.xhtml', LOGIN, HASLO);
    await expect(page).toHaveURL(/pulpit\.xhtml/);
    await expect(page.locator('.e-user .who b')).not.toHaveText(/anonim/i);
    await expect(page.getByRole('link', { name: /wyloguj/i })).toBeVisible();
    // T-004: tylko ciasteczka HttpOnly, bez JWT w ciasteczkach i magazynach przeglądarki
    for (const c of (await page.context().cookies()).filter((c) => c.domain.includes(new URL(ESKARGA).hostname) && c.path.startsWith(new URL(ESKARGA).pathname))) {
      expect(c.httpOnly, `ciasteczko ${c.name} musi być HttpOnly`).toBeTruthy();
      expect(c.value).not.toMatch(/^eyJ/);
    }
    const magazyn = await page.evaluate(() => JSON.stringify({ ...localStorage, ...sessionStorage }));
    expect(magazyn).not.toMatch(/eyJ[A-Za-z0-9_-]{10,}/);
  });

  test('UC-01 S-03 rejestracja pisma ze skanem PDF nadaje numer dziennika', async () => {
    await page.goto('/wplyw/rejestracja.xhtml');
    await page.locator('#f-kanal-fs label', { hasText: 'poczta' }).click();
    await page.fill(jsf('rej:f-wp'), `${dzis()} 09:15`);
    await page.fill(jsf('rej:f-wn'), dzis());
    await page.fill(jsf('rej:f-nad'), skarzacy);
    await page.fill(jsf('rej:f-zal'), '2');
    await page.locator('#rej input[type=file]').setInputFiles({ name: 'skarga.pdf', mimeType: 'application/pdf', buffer: pdfTestowy() });
    await expect(page.locator('#rej .thumbs, #rej [id$="skany"]').first()).toContainText('skarga.pdf', { timeout: 30_000 });
    // ADR 0026: dokument Word przyjęty po sygnaturze pliku; do akt trafi jako oryginał i zostanie skonwertowany do PDF/A przez workera
    await page.locator('#rej input[type=file]').setInputFiles({ name: 'pismo.docx', mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
      buffer: readFileSync(resolve(__dirname, 'fixtures/pismo.docx')) });
    await expect(page.locator('#rej .thumbs, #rej [id$="skany"]').first()).toContainText('pismo.docx', { timeout: 30_000 });
    await page.getByRole('button', { name: /zarejestruj i drukuj/i }).click();
    const numer = page.locator('.fs28');
    await expect(numer).toHaveText(/DW \d{4}\/\d{5}/);
    numerDw = (await numer.textContent())!.trim();
    await expect(page.locator('#rej')).toContainText(skarzacy);
  });

  test('UC-02 S-06 kolejka: podgląd faktycznego skanu i kwalifikacja jako nowa skarga Ts', async () => {
    expect(numerDw, 'numer DW z rejestracji').toMatch(/DW/);
    await page.goto('/sprawy/kolejka.xhtml');
    await page.locator('li[role=option]', { hasText: numerDw }).first().locator('a').click();
    await expect(page.locator('#h-skan')).toContainText(numerDw);
    const viewer = page.locator('.pdfv');
    await expect(viewer, 'podgląd PDF (pdf.js) zarejestrowanego skanu').toBeVisible();
    await expect(viewer.locator('.pdfv-count'), 'pdf.js policzył strony skanu').toHaveText(/^\s*1\s*$/, { timeout: 30_000 });
    await page.locator('label.opt', { hasText: 'Nowa skarga konstytucyjna (Ts)' }).click();
    await page.getByRole('button', { name: /zakwalifikuj/i }).click();
    await expect(page).toHaveURL(/sprawy\/zalozenie\.xhtml\?.*pismo=/);
  });

  test('UC-03 S-07 założenie sprawy: kreator, okno potwierdzenia, sygnatura i przejście do akt', async () => {
    const nowa = page.getByRole('button', { name: /to nowa skarga/i });
    if (await nowa.isVisible().catch(() => false)) await nowa.click();
    await expect(page.locator(jsf('zf:s-sk'))).toBeVisible();
    await page.fill(jsf('zf:s-sk'), skarzacy);
    await page.fill(jsf('zf:s-adr'), 'ul. Przykładowa 1, 00-001 Przykładów');
    await page.getByRole('button', { name: /^dalej$/i }).click();
    await expect(page.locator(jsf('zf:p-prz'))).toBeVisible();
    await page.fill(jsf('zf:p-prz'), 'art. 1 ust. 1 ustawy przykładowej');
    await page.fill(jsf('zf:p-wz'), 'art. 2 i art. 45 ust. 1 Konstytucji');
    await page.fill(jsf('zf:p-dor_input'), '20.07.2026');
    await page.keyboard.press('Escape');
    await page.getByRole('button', { name: /^dalej$/i }).click();
    await page.getByRole('button', { name: /zatwierdź wpis/i }).click();
    const zatwierdz = page.getByRole('button', { name: /zatwierdź i nadaj (Ts \d+\/\d{2})/i });
    await expect(zatwierdz).toBeVisible();
    sygnatura = /Ts \d+\/\d{2}/.exec((await zatwierdz.textContent())!)![0];
    expect(sygnatura, 'licznik repertorium nadaje numer > 0').not.toMatch(/Ts 0\//);
    await zatwierdz.click();
    await expect(page).toHaveURL(/akta\/podglad\.xhtml\?.*sygnatura=/, { timeout: 60_000 });
    await expect(page.locator('h1')).toHaveText(sygnatura);
    await expect(page.locator('#rej, .growl, body')).not.toContainText('Nie udało się założyć sprawy');
  });

  test('UC-04 S-14 akta sprawy: spis dokumentów ze skanem pisma i podgląd PDF', async () => {
    expect(sygnatura).toMatch(/Ts/);
    await page.goto(`/akta/podglad.xhtml?sygnatura=${encodeURIComponent(sygnatura)}`);
    await expect(page.locator('h1')).toHaveText(sygnatura);
    const dokumenty = page.locator('ol.tl.doc li');
    await expect(dokumenty.first()).toBeVisible();
    await dokumenty.first().locator('a.case.link').click();
    await expect(page.locator('.pdfv')).toBeVisible();
    await expect(page.locator('.pdfv .pdfv-count')).toHaveText(/^\s*1\s*$/, { timeout: 30_000 });
    // załącznik DOCX (ADR 0026): zaraz po założeniu „oczekuje na konwersję”, po cyklu workera karta akt PDF/A z oryginałem do pobrania
    const docx = page.locator('ol.tl.doc li', { hasText: 'pismo.docx' }).first();
    await expect(docx, 'DOCX z rejestracji wpływu w spisie akt').toBeVisible();
    await expect(docx).toContainText(/oczekuje na konwersję|PDF\/A|oczekuje na przetworzenie/);
    await docx.locator('a.case.link').click();
    await expect(page.locator('body')).toContainText(/czeka na konwersję|pobierz oryginał/i, { timeout: 15_000 });
    await expect
      .poll(async () => (await page.locator('ol.tl.doc li', { hasText: 'pismo.docx' }).first().textContent()) ?? '', {
        message: 'worker dokument-przetworz + konwerter (Gotenberg) zamieniają DOCX na PDF/A',
        timeout: 120_000,
        intervals: [5_000],
      })
      .toMatch(/PDF\/A.*z DOCX/);
    await page.reload();
    await page.locator('ol.tl.doc li', { hasText: 'pismo.docx' }).first().locator('a.case.link').click();
    await expect(page.locator('.pdfv .pdfv-count')).toHaveText(/^\s*\d+\s*$/, { timeout: 30_000 });
    await expect(page.getByRole('link', { name: /pobierz oryginał/i })).toBeVisible();
  });

  test('UC-05 S-08 repertorium Ts zawiera nową sprawę', async () => {
    await page.goto('/sprawy/repertorium.xhtml');
    await expect(page.locator(jsf('rf:tbl'))).toContainText(sygnatura);
    await expect(page.locator(jsf('rf:tbl'))).toContainText(skarzacy);
  });

  test('UC-06 S-04 dziennik wpływu: wpis i podgląd skanu zarejestrowanego pisma', async () => {
    await page.goto('/wplyw/dziennik.xhtml');
    const tabela = page.locator(jsf('dz:tbl'));
    await expect(tabela).toContainText(numerDw);
    await tabela.locator('tr', { hasText: numerDw }).first().click();
    await expect(page.locator('#h-pv')).toHaveText(numerDw);
    await expect(page.locator(jsf('dz:pv')).locator('.pdfv')).toBeVisible();
  });

  test('UC-07 S-23 administrator wpisuje upoważnienie z aktem PDF (BR-10) dla konta testowego', async () => {
    await page.goto('/admin/upowaznienia.xhtml');
    await page.fill(jsf('upf:u-os'), LOGIN);
    await page.fill(jsf('upf:u-od_input'), '01.01.2026');
    await page.fill(jsf('upf:u-do_input'), '31.12.2027');
    await page.keyboard.press('Escape');
    await page.locator('#upf input[type=file]').setInputFiles({ name: 'zarzadzenie-upowaznienie.pdf', mimeType: 'application/pdf', buffer: pdfTestowy('Upowaznienie do wykonywania obowiazkow Prezesa TK') });
    await expect(page.locator('#upf')).toContainText('zarzadzenie-upowaznienie.pdf', { timeout: 30_000 });
    await page.getByRole('button', { name: /zapisz upoważnienie/i }).click();
    await expect(page.locator('#upf')).toContainText(LOGIN, { timeout: 30_000 });
    await expect(page.locator('#upf')).toContainText('zarzadzenie-upowaznienie.pdf');
  });

  test('UC-18 dzwonek powiadomień: Prezes widzi sprawę do wyznaczenia, licznik gaśnie po otwarciu panelu', async () => {
    expect(sygnatura).toMatch(/Ts/);
    await page.goto('/pulpit.xhtml');
    const dzwonek = page.locator('#bell');
    await expect(dzwonek).toBeVisible();
    await expect(dzwonek.locator('.cnt'), 'licznik > 0 – sprawa czeka na wyznaczenie sędziego').toHaveText(/^[1-9]\d*$/);
    await dzwonek.click();
    const panel = page.locator('#bell-panel');
    await expect(panel).toBeVisible({ timeout: 15_000 });
    await expect(panel).toContainText('Do wyznaczenia');
    await expect(panel).toContainText(sygnatura);
    await expect(dzwonek.locator('.cnt'), 'po otwarciu panelu licznik znika').toHaveCount(0);
    await page.reload();
    await expect(page.locator('#bell .cnt'), 'obejrzane pozycje nie wracają w tej sesji').toHaveCount(0);
  });

  test('UC-08 S-17 wyznaczenie sędziego: wybór sprawy i sędziego, zarządzenie z szablonu w aktach', async () => {
    expect(sygnatura).toMatch(/Ts/);
    await page.goto(`/czynnosci/wyznaczenie.xhtml?sygnatura=${encodeURIComponent(sygnatura)}`);
    await expect(page.locator('h1')).toContainText(/wyznaczenie/i);
    // sędzia: konto testowe (rola TK_SEDZIA, zalogowane → rejestr S-22); w tabeli obciążenia wiersz z loginem
    const wiersz = page.locator('tr', { hasText: LOGIN }).first();
    await expect(wiersz, 'konto testowe na liście sędziów').toBeVisible();
    await wiersz.locator('.ui-radiobutton-box, input[type=radio]').first().click();
    await expect(page.locator('.pane-h', { hasText: 'Podgląd zarządzenia' })).toContainText(sygnatura);
    await page.getByRole('button', { name: /wyznacz i podpisz/i }).click();
    // projekt zarządzenia w aktach, podpis na S-16 (dwuetapowy, D10); skutki wyznaczenia dopiero po podpisie
    await expect(page).toHaveURL(/czynnosci\/podpis\.xhtml\?.*dokument=\d+/, { timeout: 60_000 });
    await expect(page.locator('body')).not.toContainText('Nie udało się wyznaczyć sędziego');
    await expect(page.locator('body')).toContainText(sygnatura);
    if (process.env['ESKARGA_E2E_PODPIS'] === '1') {
      // aplikacja lokalna do podpisu działa na stacji testowej (infra\windows\podpis.cmd, klucz testowy w magazynie zaufania)
      await page.getByRole('button', { name: /połącz z aplikacją/i }).click();
      await page.getByRole('button', { name: /podpisz kartą/i }).click();
      await expect(page.getByRole('button', { name: /czekam na aplikację/i })).toBeVisible({ timeout: 30_000 });
      await page.getByRole('button', { name: /czekam na aplikację/i }).click();
      await expect(page.locator('#sign-error')).toHaveText('');
      await expect(page.getByRole('link', { name: /wróć do sprawy/i })).toBeVisible({ timeout: 60_000 });
      await page.getByRole('link', { name: /wróć do sprawy/i }).click();
      await expect(page).toHaveURL(/akta\/podglad\.xhtml/);
      await expect(page.locator('body')).toContainText(/We wstępnej kontroli/i);
    }
    await page.goto(`/akta/podglad.xhtml?sygnatura=${encodeURIComponent(sygnatura)}`);
    const zarzadzenie = page.locator('ol.tl.doc li', { hasText: /wyznaczeniu sędziego/i }).first();
    await expect(zarzadzenie, 'zarządzenie (projekt albo podpisane) w spisie akt').toBeVisible();
    await zarzadzenie.locator('a.case.link').click();
    await expect(page.locator('.pdfv .pdfv-count'), 'PDF zarządzenia z szablonu usługi dokumentów').toHaveText(/^\s*\d+\s*$/, { timeout: 30_000 });
    await expect(page.locator('.pdfv-text')).toContainText(/ZARZĄDZENIE|wyznaczy/i, { timeout: 30_000 });
    if (process.env['ESKARGA_E2E_PODPIS'] !== '1') {
      await page.getByRole('tab', { name: /czynności/i }).click();
      await expect(page.locator('body')).toContainText(/Projekty do podpisu/);
    }
  });

  test('UC-09 S-14 lista kontrolna: ocena wymogu zapisuje się i przetrwa odświeżenie', async () => {
    await page.goto(`/akta/podglad.xhtml?sygnatura=${encodeURIComponent(sygnatura)}`);
    await page.getByRole('tab', { name: /lista kontrolna/i }).click();
    const pozycja = page.locator('li', { has: page.getByRole('button', { name: /^brak$/i }) }).first();
    await expect(pozycja).toBeVisible();
    const wymog = (await pozycja.locator('b').first().textContent())!.trim();
    await pozycja.getByRole('textbox').fill('brak pełnomocnictwa szczególnego – k. 2');
    await pozycja.getByRole('button', { name: /^brak$/i }).click();
    await expect(page.locator('li', { hasText: wymog }).first()).toContainText(/brak pełnomocnictwa szczególnego/, { timeout: 30_000 });
    await expect(page.locator('li', { hasText: wymog }).first().locator('.tag')).toContainText(/brak/);
    await page.reload();
    await page.getByRole('tab', { name: /lista kontrolna/i }).click();
    await expect(page.locator('li', { hasText: wymog }).first().locator('.tag')).toContainText(/brak/);
    await expect(page.locator('body')).toContainText(/Lista kontrolna · 1 brak/);
  });

  test('UC-09 S-14 notatki wewnętrzne: zespół sędziego dodaje notatkę, widoczna po odświeżeniu (FR-24)', async () => {
    await page.goto(`/akta/podglad.xhtml?sygnatura=${encodeURIComponent(sygnatura)}`);
    const zakladka = page.getByRole('tab', { name: /notatki wewnętrzne/i });
    await expect(zakladka).toBeVisible();
    // konto testowe założyło sprawę (referent sprawy) i ma rolę TK_SEDZIA → zespół sędziego (CaseAccessPolicy.canReadInternalNotes)
    await expect(zakladka, 'zakładka aktywna dla zespołu sędziego').toBeEnabled();
    await zakladka.click();
    const tytul = `Uwagi asystenta ${Date.now()}`;
    await page.fill(jsf('af:nt-tytul'), tytul);
    await page.fill(jsf('af:nt-tresc'), 'Pełnomocnictwo szczególne nie obejmuje sporządzenia skargi (k. 2).');
    await page.getByRole('button', { name: /dodaj notatkę/i }).click();
    await expect(page.locator('ol[aria-label="Notatki wewnętrzne"]')).toContainText(tytul, { timeout: 30_000 });
    await expect(page.locator('ol[aria-label="Notatki wewnętrzne"]')).toContainText(/notatka wewnętrzna · /);
    await page.reload();
    await expect(page.getByRole('tab', { name: /notatki wewnętrzne · [1-9]/i })).toBeVisible();
    await page.getByRole('tab', { name: /notatki wewnętrzne/i }).click();
    await expect(page.locator('ol[aria-label="Notatki wewnętrzne"]')).toContainText(tytul);
    // FR-24: notatka nie trafia do spisu akt jawnych
    await page.getByRole('tab', { name: /akta jawne/i }).click();
    await expect(page.locator('ol[aria-label="Dokumenty"]')).not.toContainText(tytul);
  });

  test('UC-24 S-24 szablony pism: podgląd PDF, nowa wersja z edytora, usunięcie projektu', async () => {
    await page.goto('/admin/slowniki.xhtml?zakladka=szablony');
    const lista = page.locator('[id$="sz-lista"]');
    if (!(await lista.locator('tbody tr', { hasText: 'ZARZ_WYZNACZENIE_SEDZIEGO' }).first().isVisible().catch(() => false))) {
      await page.getByRole('button', { name: /załaduj szablony domyślne/i }).click();
    }
    const wiersz = lista.locator('tbody tr', { hasText: 'ZARZ_WYZNACZENIE_SEDZIEGO' }).first();
    await expect(wiersz).toBeVisible({ timeout: 30_000 });
    // podgląd: PDF z usługi dokumentów na danych fikcyjnych
    await wiersz.getByRole('button', { name: /^podgląd$/i }).click();
    await expect(page.locator('.pdfv .pdfv-count')).toHaveText(/^\s*\d+\s*$/, { timeout: 30_000 });
    // edycja aktywnej wersji → zapis tworzy nową wersję w stanie projekt
    const liczbaPrzed = await lista.locator('tbody tr').count();
    await wiersz.getByRole('button', { name: /^edytuj$/i }).click();
    await expect(page.locator('[id$="sz-edytor"]')).toContainText(/Nowa wersja na podstawie/);
    const zrodlo = page.locator('[id$="sz-zrodlo"]');
    await zrodlo.fill((await zrodlo.inputValue()).replace('<h1>ZARZĄDZENIE</h1>', '<h1>ZARZĄDZENIE</h1><p>Wersja testowa GUI.</p>'));
    await page.getByRole('button', { name: /zapisz jako nową wersję/i }).click();
    await expect(lista.locator('tbody tr')).toHaveCount(liczbaPrzed + 1, { timeout: 30_000 });
    const projekt = lista.locator('tbody tr', { hasText: 'ZARZ_WYZNACZENIE_SEDZIEGO' }).filter({ hasText: /projekt/ }).first();
    await expect(projekt).toBeVisible();
    // usunięcie projektu (logiczne) – znika z listy; wersja aktywna zostaje
    page.once('dialog', (d) => d.accept());
    await projekt.getByRole('button', { name: /^usuń$/i }).click();
    await expect(lista.locator('tbody tr')).toHaveCount(liczbaPrzed, { timeout: 30_000 });
    await expect(lista.locator('tbody tr', { hasText: 'ZARZ_WYZNACZENIE_SEDZIEGO' }).first()).toContainText(/aktywna/);
  });

  test('UC-24 S-24 lista kontrolna: administrator dodaje, zmienia i wycofuje wymóg Ts/Tw', async () => {
    await page.goto('/admin/slowniki.xhtml?zakladka=lista');
    const lista = page.locator('[id$="lk-lista"]');
    await expect(lista).toContainText('S-01');
    await page.locator('[id$="lk-tw"]').click();
    await expect(lista).toContainText('W-01', { timeout: 15_000 });
    await expect(lista).not.toContainText('S-01');
    await page.locator('[id$="lk-ts"]').click();
    await expect(lista).toContainText('S-01', { timeout: 15_000 });
    const kod = `T-${String(Date.now()).slice(-6)}`;
    await page.locator('[id$="lk-nowy"]').click();
    await page.fill(jsf('sf:tabs:lk-kod'), kod);
    await page.fill(jsf('sf:tabs:lk-tresc'), 'Wymóg testowy GUI – do wycofania');
    await page.fill(jsf('sf:tabs:lk-podstawa'), 'art. 53 ust. 1 pkt 9');
    await page.fill(jsf('sf:tabs:lk-kolejnosc'), '95');
    await page.locator('[id$="lk-zapisz"]').click();
    const wiersz = lista.locator('tbody tr', { hasText: kod });
    await expect(wiersz).toBeVisible({ timeout: 30_000 });
    await expect(wiersz).toContainText(/aktywny/);
    await wiersz.getByRole('button', { name: /^edytuj$/i }).click();
    await expect(page.locator(jsf('sf:tabs:lk-kod'))).toBeDisabled();
    await page.fill(jsf('sf:tabs:lk-podstawa'), 'art. 53 ust. 1 pkt 9 (po opinii BSP)');
    await page.locator('[id$="lk-zapisz"]').click();
    await expect(lista.locator('tbody tr', { hasText: kod })).toContainText('po opinii BSP', { timeout: 30_000 });
    page.once('dialog', (d) => d.accept());
    await lista.locator('tbody tr', { hasText: kod }).getByRole('button', { name: /^wycofaj$/i }).click();
    await expect(lista.locator('tbody tr', { hasText: kod })).toContainText(/wycofany/, { timeout: 30_000 });
    // lista kontrolna w aktach nie zawiera wycofanego wymogu
    await page.goto(`/akta/podglad.xhtml?sygnatura=${encodeURIComponent(sygnatura)}`);
    await page.getByRole('tab', { name: /lista kontrolna/i }).click();
    await expect(page.locator('body')).toContainText('Określenie kwestionowanego przepisu');
    await expect(page.locator('body')).not.toContainText('Wymóg testowy GUI');
  });

  test('UC-10 przełącznik roli zmienia kontekst menu (gdy konto ma kilka ról)', async () => {
    await page.goto('/pulpit.xhtml');
    const rola = page.locator('#tb-rola');
    test.skip(!(await rola.isVisible().catch(() => false)), 'konto ma jedną rolę – brak przełącznika');
    await rola.click();
    const opcja = page.locator('#tb-rola_panel li', { hasText: 'biuro podawcze' });
    test.skip(!(await opcja.isVisible().catch(() => false)), 'konto nie ma roli biura podawczego');
    await opcja.click();
    await expect(page.locator('.e-user .who small')).toHaveText(/biuro podawcze/, { timeout: 15_000 });
    await expect(page.getByRole('navigation').getByRole('link', { name: /rejestracja/i }).first()).toBeVisible();
  });

  test('UC-11 wylogowanie kończy sesję w eSkardze i w ID-users', async () => {
    await page.goto('/pulpit.xhtml');
    await page.getByRole('link', { name: /wyloguj/i }).click();
    await page.waitForLoadState('domcontentloaded');
    await page.goto('/pulpit.xhtml');
    await expect(page.locator('#username'), 'po wylogowaniu wejście wymaga ponownego logowania w ID-users').toBeVisible({ timeout: 30_000 });
  });
});
