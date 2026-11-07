import http from 'node:http';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { expect, test, type Page } from '@playwright/test';
import { pdfTestowy } from '../e2e/srodowisko';

/**
 * Widżet PDF eSkargi (tk:pdfViewer + resources/js/pdf-viewer.js, T-034) w prawdziwym Chromium, bez WildFly:
 * serwer testowy podaje skrypt widżetu z repozytorium, pdf.js w tej samej wersji co webjar (pdfjs-dist 6.4.299),
 * skan przez HTTP Range jak IncomingScanServlet – z nagłówkiem CSP wziętym wprost z web.xml (plus nonce dopisany
 * na końcu jak robi PrimeFaces), X-Content-Type-Options: nosniff i typem text/javascript dla .mjs (mime-mapping z web.xml).
 * Sprawdza oba sposoby pojawienia się widżetu: w HTML strony oraz wstawiony do DOM po odpowiedzi AJAX PrimeFaces
 * (kolejka S-06 po wyborze pisma, dziennik S-04, akta S-14) – ten drugi nie montował się przed poprawką z 05.11.2026.
 */
const APP = resolve(__dirname, '../../../app/src/main/webapp');
const PDFJS = resolve(__dirname, '../../node_modules/pdfjs-dist/build');
const PDFJS_WERSJA = '6.4.299';

function cspZWebXml(): string {
  const webXml = readFileSync(resolve(APP, 'WEB-INF/web.xml'), 'utf8');
  const m = /<param-name>primefaces\.CSP_POLICY<\/param-name>[\s\S]*?<param-value>([^<]+)<\/param-value>/.exec(webXml);
  expect(m, 'primefaces.CSP_POLICY w web.xml').toBeTruthy();
  return m![1]!.trim() + " 'nonce-abc123';"; // CspPhaseListener.initCsp dokleja nonce na końcu polityki
}

/** Znacznik widżetu tak, jak wyrenderuje go Facelets dla skanu /wplyw/skan/1. */
function widgetHtml(): string {
  const xhtml = readFileSync(resolve(APP, 'resources/tk/pdfViewer.xhtml'), 'utf8');
  const impl = /<cc:implementation>([\s\S]*?)<h:outputScript/.exec(xhtml)![1]!;
  return impl
    .replace(/#\{cc\.clientId\}/g, 'pdfv1').replace(/#\{cc\.attrs\.src\}/g, '/eskarga/wplyw/skan/1').replace(/#\{cc\.attrs\.pageFrom\}/g, '1')
    .replace(/#\{cc\.attrs\.highlights\}/g, '[]').replace(/#\{cc\.attrs\.canHighlight\}/g, 'false').replace(/#\{cc\.attrs\.title\}/g, 'Skan')
    .replace(/#\{request\.contextPath\}/g, '/eskarga').replace(/<h:outputLink[\s\S]*?<\/h:outputLink>/g, '')
    // przełącznik dokumentów to znaczniki JSF (ui:repeat) – w teście statycznym zastępujemy go własnymi chipami dwóch skanów
    .replace(/<h:panelGroup rendered="#\{not empty cc\.attrs\.docs[\s\S]*?(?=<div class="pdfv-bar")/,
      '<div class="pdfv-docs" role="tablist"><button type="button" class="pdfv-doc" role="tab" data-src="/eskarga/wplyw/skan/1" data-download="/eskarga/wplyw/skan/1?pobierz=1">1. skarga.pdf</button>'
      + '<button type="button" class="pdfv-doc" role="tab" data-src="/eskarga/wplyw/skan/2" data-download="/eskarga/wplyw/skan/2?pobierz=1">2. zalacznik.pdf</button></div>');
}

async function serwer(): Promise<{ url: string; zamknij: () => void }> {
  const csp = cspZWebXml();
  const widget = widgetHtml();
  const pdfy: Record<string, Buffer> = { '1': pdfTestowy('Skarga konstytucyjna - test widzetu'), '2': pdfTestowy('Drugi dokument pisma - zalacznik') };
  const srv = http.createServer((req, res) => {
    const url = new URL(req.url!, 'http://x');
    res.setHeader('X-Content-Type-Options', 'nosniff');
    res.setHeader('Content-Security-Policy', csp);
    if (url.pathname === '/eskarga/resources/css/eskarga.css') {
      res.writeHead(200, { 'Content-Type': 'text/css' });
      return res.end(readFileSync(resolve(APP, 'resources/css/eskarga.css')));
    }
    if (url.pathname === '/eskarga/resources/js/pdf-viewer.js') {
      res.writeHead(200, { 'Content-Type': 'text/javascript' });
      return res.end(readFileSync(resolve(APP, 'resources/js/pdf-viewer.js')));
    }
    if (url.pathname.startsWith(`/eskarga/webjars/pdfjs-dist/${PDFJS_WERSJA}/build/`)) {
      res.writeHead(200, { 'Content-Type': 'text/javascript' }); // web.xml: mime-mapping mjs → text/javascript
      return res.end(readFileSync(resolve(PDFJS, url.pathname.split('/').pop()!)));
    }
    const skan = /^\/eskarga\/wplyw\/skan\/(\d)$/.exec(url.pathname);
    if (skan && pdfy[skan[1]!]) {
      const pdf = pdfy[skan[1]!]!;
      res.setHeader('Accept-Ranges', 'bytes');
      const r = /bytes=(\d+)-(\d*)/.exec(req.headers.range ?? '');
      if (r) {
        const a = Number(r[1]);
        const b = r[2] ? Math.min(Number(r[2]), pdf.length - 1) : pdf.length - 1;
        res.writeHead(206, { 'Content-Type': 'application/pdf', 'Content-Range': `bytes ${a}-${b}/${pdf.length}`, 'Content-Length': b - a + 1 });
        return res.end(pdf.subarray(a, b + 1));
      }
      res.writeHead(200, { 'Content-Type': 'application/pdf', 'Content-Length': pdf.length });
      return res.end(pdf);
    }
    if (url.pathname === '/eskarga/strona.html') {
      const ajax = url.searchParams.get('tryb') === 'ajax';
      const szer = url.searchParams.get('szer') ?? '720';
      const body = ajax ? '<div id="cel"></div>' : widget;
      const wstaw = ajax ? `<script nonce="abc123">setTimeout(function () { document.getElementById('cel').innerHTML = ${JSON.stringify(widget)}; }, 300);</script>` : '';
      res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
      return res.end(`<!doctype html><html lang="pl"><head><meta charset="utf-8"><title>widżet PDF</title><link rel="stylesheet" href="/eskarga/resources/css/eskarga.css"></head><body><div class="viewer" style="width: ${szer}px">${body}</div><script src="/eskarga/resources/js/pdf-viewer.js"></script>${wstaw}</body></html>`);
    }
    if (url.pathname === '/favicon.ico') {
      res.writeHead(204);
      return res.end();
    }
    console.log('serwer testowy: 404 ' + url.pathname);
    res.writeHead(404);
    res.end();
  });
  await new Promise<void>((ok) => srv.listen(0, '127.0.0.1', () => ok()));
  const port = (srv.address() as { port: number }).port;
  return { url: `http://127.0.0.1:${port}/eskarga/strona.html`, zamknij: () => srv.close() };
}

async function sprawdzRender(page: Page): Promise<void> {
  const bledy: string[] = [];
  page.on('console', (m) => { if (m.type() === 'error') bledy.push(m.text()); });
  page.on('pageerror', (e) => bledy.push(e.message));
  page.on('response', (r) => { if (r.status() >= 400) bledy.push(`HTTP ${r.status()} ${r.url()}`); });
  await expect(page.locator('.pdfv-count'), 'pdf.js policzył strony').toHaveText('1', { timeout: 20_000 });
  await expect(page.locator('.pdfv-text'), 'warstwa tekstowa z treścią skanu').toContainText('Skarga', { timeout: 20_000 });
  const ciemne = await page.evaluate(() => {
    const c = document.querySelector<HTMLCanvasElement>('.pdfv-pg canvas')!;
    const d = c.getContext('2d')!.getImageData(0, 0, c.width, c.height).data;
    let n = 0;
    for (let i = 0; i < d.length; i += 4) if (d[i]! < 128 && d[i + 3]! > 0) n++;
    return n;
  });
  expect(ciemne, 'strona narysowana na canvas (ciemne piksele tekstu)').toBeGreaterThan(100);
  // dopasowanie do panelu: strona wypełnia szerokość obszaru przewijania (nie wychodzi poza panel, nie jest miniaturą),
  // a widżet ma ograniczoną wysokość – strony przewijają się w nim, nie rozciągają całej strony
  const miary = await page.evaluate(() => {
    const c = document.querySelector<HTMLCanvasElement>('.pdfv-pg canvas')!;
    const s = document.querySelector<HTMLElement>('.pdfv-scroll')!;
    const w = document.querySelector<HTMLElement>('.pdfv')!;
    return { canvas: c.getBoundingClientRect().width, scroll: s.clientWidth, widget: w.getBoundingClientRect().height, okno: window.innerHeight, przewija: s.scrollHeight > s.clientHeight };
  });
  expect(miary.canvas, 'strona nie szersza niż panel').toBeLessThanOrEqual(miary.scroll);
  if (miary.scroll < 800) {
    expect(miary.canvas, 'strona dopasowana do szerokości panelu').toBeGreaterThan(miary.scroll * 0.85);
  }
  expect(miary.widget, 'widżet nie wyższy niż okno').toBeLessThanOrEqual(miary.okno);
  expect(miary.przewija, 'strona A4 przewija się w widżecie').toBeTruthy();
  expect(bledy, 'bez błędów CSP/MIME w konsoli').toEqual([]);
}

test.describe('Widżet PDF (pdf.js, CSP z web.xml)', () => {
  let s: Awaited<ReturnType<typeof serwer>>;
  test.beforeAll(async () => { s = await serwer(); });
  test.afterAll(() => s.zamknij());

  test('renderuje skan osadzony w stronie', async ({ page }) => {
    await page.goto(s.url);
    await sprawdzRender(page);
  });

  test('tekst da się zaznaczyć i skopiować z cytatem (sygnatura, karta)', async ({ page, context }) => {
    await context.grantPermissions(['clipboard-read', 'clipboard-write']);
    await page.goto(s.url);
    await expect(page.locator('.pdfv-text')).toContainText('Skarga', { timeout: 20_000 });
    // spany warstwy tekstowej mają rozmiar (pdf.js 6 liczy go z --scale-factor i reguł CSS) – inaczej nie ma czego zaznaczyć
    const rozmiar = await page.locator('.pdfv-text span').first().boundingBox();
    expect(rozmiar?.width ?? 0, 'span warstwy tekstowej ma szerokość').toBeGreaterThan(20);
    expect(rozmiar?.height ?? 0, 'span warstwy tekstowej ma wysokość').toBeGreaterThan(8);
    await page.locator('.pdfv-text span').first().click({ clickCount: 3 });
    const zaznaczenie = await page.evaluate(() => window.getSelection()?.toString() ?? '');
    expect(zaznaczenie, 'potrójne kliknięcie zaznacza tekst dokumentu').toContain('Skarga');
    await page.locator('.pdfv').evaluate((el) => el.setAttribute('data-citation', 'Ts 312/26'));
    await page.getByRole('button', { name: /kopiuj z cytatem/i }).click();
    await expect(page.locator('.pdfv-found')).toHaveText(/skopiowano z cytatem/);
    const schowek = await page.evaluate(() => navigator.clipboard.readText());
    expect(schowek).toContain('Skarga');
    expect(schowek, 'cytat: sygnatura i karta').toMatch(/\(Ts 312\/26, k\. 1\)$/);
  });

  test('renderuje skan wstawiony do DOM po odpowiedzi AJAX (kolejka, dziennik, akta)', async ({ page }) => {
    await page.goto(s.url + '?tryb=ajax');
    await sprawdzRender(page);
  });

  test('przełącznik dokumentów pisma otwiera drugi skan w tym samym widżecie', async ({ page }) => {
    await page.goto(s.url);
    await sprawdzRender(page);
    await expect(page.locator('.pdfv-doc').first()).toHaveClass(/on/);
    await page.locator('.pdfv-doc', { hasText: 'zalacznik' }).click();
    await expect(page.locator('.pdfv-doc', { hasText: 'zalacznik' })).toHaveClass(/on/);
    await expect(page.locator('.pdfv-count')).toHaveText('1', { timeout: 20_000 });
    await expect(page.locator('.pdfv-text')).toContainText('Drugi dokument', { timeout: 20_000 });
    await expect(page.locator('.pdfv-text')).not.toContainText('Skarga konstytucyjna');
    expect(await page.locator('.pdfv').getAttribute('data-src')).toBe('/eskarga/wplyw/skan/2');
  });

  test('dopasowanie automatyczne nie przekracza 100 %, ręczne powiększenie tak', async ({ page }) => {
    await page.setViewportSize({ width: 1600, height: 900 });
    await page.goto(s.url + '?szer=1400');
    await sprawdzRender(page);
    await expect(page.locator('.pdfv-zoom')).toHaveText('100 %');
    const szerokosc = await page.locator('.pdfv-pg canvas').first().evaluate((c) => (c as HTMLCanvasElement).width);
    expect(szerokosc, 'A4 w skali 1:1 (595 pt × 1.33)').toBeLessThanOrEqual(795);
    expect(szerokosc).toBeGreaterThanOrEqual(785);
    await page.locator('[data-act="zoom-in"]').click();
    await expect(page.locator('.pdfv-zoom')).toHaveText('115 %');
    await page.locator('[data-act="fit"]').click();
    await expect(page.locator('.pdfv-zoom')).toHaveText('100 %');
  });
});
