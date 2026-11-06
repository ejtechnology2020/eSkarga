import { createHmac } from 'node:crypto';
import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { expect, request, type Page } from '@playwright/test';

/**
 * Środowisko testów GUI w trybie OIDC (ADR 0020, T-058): eSkarga na WildFly obok ID-users (Keycloak :8180).
 * Konto testowe przychodzi ze zmiennych środowiskowych – żadnych haseł ani kluczy TOTP w repozytorium.
 *
 *  ESKARGA_URL                 bazowy adres aplikacji (domyślnie http://localhost:8082/eskarga)
 *  ESKARGA_E2E=1               włącza testy OIDC (bez tej zmiennej są pomijane)
 *  ESKARGA_E2E_LOGIN           login w ID-users (domyślnie e2e.eskarga, gdy konto zakłada test; inaczej np. admin.wszystko)
 *  ESKARGA_E2E_HASLO           hasło konta (dev: DEV_USER_PASSWORD z id/dev/.env)
 *  ESKARGA_E2E_TOTP            klucz TOTP w Base32 (z ekranu „Nie możesz zeskanować?” Keycloaka) albo
 *  ESKARGA_E2E_TOTP_PLIK       plik z kluczem (domyślnie <ID_USERS_KLUCZE_DIR>/totp-<login>.txt – zapisuje go
 *                              narzędzie rejestracji eSkargi w ID-users i ten test po pierwszej konfiguracji OTP)
 *  ESKARGA_E2E_KEYCLOAK_URL    Keycloak ID-users (domyślnie http://localhost:8180)
 *  ESKARGA_E2E_PODPIS=1        na stacji działa aplikacja lokalna do podpisu z kluczem testowym (infra\windows\podpis.cmd) – scenariusz
 *                              wyznaczenia sędziego podpisuje zarządzenie na S-16; bez tej zmiennej kończy się na projekcie w aktach
 *  ESKARGA_E2E_KC_ADMIN_HASLO  hasło administratora realm master – gdy ustawione, test zakłada świeże konto
 *                              testowe z rolami klienta eskarga (biuro podawcze, referent, asystent, administrator)
 *                              i konfiguruje OTP przy pierwszym logowaniu; bez niego używa istniejącego konta
 */
export const ESKARGA = process.env['ESKARGA_URL'] ?? 'http://localhost:8082/eskarga';
export const KEYCLOAK = process.env['ESKARGA_E2E_KEYCLOAK_URL'] ?? 'http://localhost:8180';
export const REALM = 'id-users';
export const KLIENT = process.env['ESKARGA_E2E_KLIENT'] ?? 'eskarga';
export const WLACZONE = process.env['ESKARGA_E2E'] === '1';
export const ZAKLADA_KONTO = !!process.env['ESKARGA_E2E_KC_ADMIN_HASLO'];
export const LOGIN = process.env['ESKARGA_E2E_LOGIN'] ?? (ZAKLADA_KONTO ? 'e2e.eskarga' : 'admin.wszystko');
export const HASLO = process.env['ESKARGA_E2E_HASLO'] ?? (ZAKLADA_KONTO ? 'E2e-Haslo-Testowe-2026' : '');
const KLUCZE_DIR = process.env['ID_USERS_KLUCZE_DIR'] ?? resolve(__dirname, '../../../../id/frontend/narzedzia/wyniki/eskarga');
export const PLIK_TOTP = process.env['ESKARGA_E2E_TOTP_PLIK'] ?? resolve(KLUCZE_DIR, `totp-${LOGIN}.txt`);

/** Role klienta eskarga nadawane kontu zakładanemu przez test – ścieżka od biura podawczego do akt. */
export const ROLE_TESTOWE = ['TK_BIURO_PODAWCZE', 'TK_REFERENT', 'TK_ASYSTENT', 'TK_SEDZIA', 'TK_PREZES', 'TK_ADMIN'];   // od biura podawczego po wyznaczenie sędziego i listę kontrolną

let kluczTotp: string | undefined = process.env['ESKARGA_E2E_TOTP'];

/** Kod TOTP (RFC 6238, HmacSHA1, 6 cyfr, 30 s) dla klucza w Base32 – jak w testach e2e ID-users. */
export function totp(kluczBase32: string, czas = Date.now()): string {
  const alfabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
  const czysty = kluczBase32.replace(/[\s=]/g, '').toUpperCase();
  let bity = '';
  for (const z of czysty) bity += alfabet.indexOf(z).toString(2).padStart(5, '0');
  const bajty = Buffer.from(bity.match(/.{8}/g)!.map((b) => parseInt(b, 2)));
  const licznik = Buffer.alloc(8);
  licznik.writeBigUInt64BE(BigInt(Math.floor(czas / 30_000)));
  const h = createHmac('sha1', bajty).update(licznik).digest();
  const o = h[h.length - 1] & 0xf;
  return ((h.readUInt32BE(o) & 0x7fffffff) % 1_000_000).toString().padStart(6, '0');
}

function kluczTotpZPliku(): string | undefined {
  if (kluczTotp) return kluczTotp;
  if (existsSync(PLIK_TOTP)) kluczTotp = readFileSync(PLIK_TOTP, 'utf8').trim();
  return kluczTotp;
}

/** Zapamiętuje klucz TOTP skonfigurowany w trakcie testu (tylko w pamięci procesu – nie zapisujemy sekretów na dysk). */
export function zapamietajKluczTotp(klucz: string): void {
  kluczTotp = klucz;
}

/** Klient Admin API Keycloaka (realm master) – tylko do założenia konta testowego. */
async function adminKeycloak(): Promise<{ api: Awaited<ReturnType<typeof request.newContext>>; naglowki: Record<string, string> }> {
  const api = await request.newContext();
  const odp = await api.post(`${KEYCLOAK}/realms/master/protocol/openid-connect/token`, {
    form: { grant_type: 'password', client_id: 'admin-cli', username: process.env['ESKARGA_E2E_KC_ADMIN'] ?? 'admin', password: process.env['ESKARGA_E2E_KC_ADMIN_HASLO']! },
  });
  expect(odp.ok(), 'token administratora Keycloaka (ESKARGA_E2E_KC_ADMIN_HASLO)').toBeTruthy();
  const { access_token } = (await odp.json()) as { access_token: string };
  return { api, naglowki: { Authorization: `Bearer ${access_token}` } };
}

/**
 * Świeże konto testowe (usuwa poprzednie o tym loginie) z hasłem, akcją CONFIGURE_TOTP i rolami klienta eskarga.
 * Dane fikcyjne (Ewa Endtoend). Zwraca identyfikator konta.
 */
export async function kontoTestowe(login = LOGIN, role = ROLE_TESTOWE): Promise<string> {
  const { api, naglowki } = await adminKeycloak();
  const baza = `${KEYCLOAK}/admin/realms/${REALM}`;
  const istniejace = (await (await api.get(`${baza}/users?exact=true&username=${login}`, { headers: naglowki })).json()) as { id: string }[];
  for (const u of istniejace) await api.delete(`${baza}/users/${u.id}`, { headers: naglowki });
  const odp = await api.post(`${baza}/users`, {
    headers: naglowki,
    data: {
      username: login, email: `${login}@example.com`, firstName: 'Ewa', lastName: 'Endtoend', enabled: true, emailVerified: true,
      requiredActions: ['CONFIGURE_TOTP'], credentials: [{ type: 'password', value: HASLO, temporary: false }],
    },
  });
  expect(odp.status(), 'utworzenie konta testowego').toBe(201);
  const id = odp.headers()['location']!.split('/').pop()!;
  const [klient] = (await (await api.get(`${baza}/clients?clientId=${KLIENT}`, { headers: naglowki })).json()) as { id: string }[];
  expect(klient, `klient ${KLIENT} w realm ${REALM} (zarejestruj eSkargę w ID-users)`).toBeTruthy();
  const dostepne = (await (await api.get(`${baza}/clients/${klient!.id}/roles`, { headers: naglowki })).json()) as { id: string; name: string }[];
  const wybrane = dostepne.filter((r) => role.includes(r.name));
  expect(wybrane.map((r) => r.name).sort(), `role klienta ${KLIENT} opublikowane z katalogu ID-users`).toEqual([...role].sort());
  const nadanie = await api.post(`${baza}/users/${id}/role-mappings/clients/${klient!.id}`, { headers: naglowki, data: wybrane });
  expect(nadanie.ok(), 'nadanie ról klienta eskarga').toBeTruthy();
  kluczTotp = undefined; // nowe konto – klucz powstanie przy pierwszym logowaniu
  return id;
}

/** Czy strona jest już w eSkardze (po powrocie z Keycloaka). */
export function wEskardze(page: Page): boolean {
  return page.url().startsWith(ESKARGA);
}

/**
 * Logowanie przez ID-users: formularz Keycloaka (login, hasło), ekrany pośrednie (konfiguracja OTP przy pierwszym
 * logowaniu, kod OTP, ponowne hasło, klauzule) aż do powrotu do eSkargi.
 */
export async function zaloguj(page: Page, cel = '/pulpit.xhtml', login = LOGIN, haslo = HASLO): Promise<void> {
  expect(haslo, 'ESKARGA_E2E_HASLO – hasło konta testowego').not.toBe('');
  await page.goto(cel);
  await ekranyPosrednie(page, login, haslo);
  await expect(page, `powrót do eSkargi po zalogowaniu ${login}`).toHaveURL(new RegExp('^' + ESKARGA.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')), { timeout: 30_000 });
}

async function ekranyPosrednie(page: Page, login: string, haslo: string): Promise<void> {
  for (let krok = 0; krok < 10 && !wEskardze(page); krok++) {
    await page.waitForLoadState('domcontentloaded');
    const widoczne = async (sel: string) => page.locator(sel).first().isVisible().catch(() => false);
    if (await widoczne('#saveTOTPBtn')) {
      // pierwsza konfiguracja OTP (akcja CONFIGURE_TOTP) – klucz widoczny w trybie ręcznym
      if (await widoczne('#mode-manual')) await page.click('#mode-manual');
      await expect(page.locator('#kc-totp-secret-key'), 'klucz TOTP (tryb ręczny)').toBeVisible();
      const klucz = (await page.locator('#kc-totp-secret-key').textContent())!.trim();
      zapamietajKluczTotp(klucz);
      await page.fill('#totp', totp(klucz));
      if (await widoczne('#userLabel')) await page.fill('#userLabel', 'Testy GUI eSkargi');
      await page.click('#saveTOTPBtn');
    } else if (await widoczne('#otp')) {
      const klucz = kluczTotpZPliku();
      expect(klucz, `klucz TOTP konta ${login}: ESKARGA_E2E_TOTP albo plik ${PLIK_TOTP}`).toBeTruthy();
      await page.fill('#otp', totp(klucz!));
      await page.click('#kc-login');
    } else if (await widoczne('#username')) {
      await page.fill('#username', login);
      await page.fill('#password', haslo);
      await page.click('#kc-login');
    } else if (await widoczne('#password')) {
      await page.fill('#password', haslo);
      await page.click('#kc-login');
    } else if (await page.getByRole('button', { name: 'Zapisz i przejdź dalej' }).isVisible().catch(() => false)) {
      for (const pole of await page.getByRole('checkbox', { name: 'Akceptuję' }).all()) await pole.check();
      await page.getByRole('button', { name: 'Zapisz i przejdź dalej' }).click();
    } else {
      await page.waitForURL((u) => u.toString().startsWith(ESKARGA) || /login-actions|openid-connect/.test(u.toString()), { timeout: 15_000 }).catch(() => undefined);
    }
  }
}

/** Dzisiejsza data w Warszawie jako DD.MM.RRRR (pola dat formularzy). */
export function dzis(): string {
  const [d, m, r] = new Intl.DateTimeFormat('pl-PL', { timeZone: 'Europe/Warsaw', day: '2-digit', month: '2-digit', year: 'numeric' })
    .format(new Date()).split('.');
  return `${d}.${m}.${r}`;
}

/** Minimalny, poprawny PDF (jedna strona A4 z tekstem) – skan pisma do rejestracji; pdf.js go otwiera. */
export function pdfTestowy(tekst = 'Skarga konstytucyjna - test GUI'): Buffer {
  const strumien = `BT /F1 18 Tf 72 760 Td (${tekst.replace(/[()\\]/g, '')}) Tj ET`;
  const obiekty = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>',
    `<< /Length ${strumien.length} >>\nstream\n${strumien}\nendstream`,
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
  ];
  let tresc = '%PDF-1.4\n';
  const offsety: number[] = [];
  obiekty.forEach((o, i) => {
    offsety.push(Buffer.byteLength(tresc));
    tresc += `${i + 1} 0 obj\n${o}\nendobj\n`;
  });
  const xref = Buffer.byteLength(tresc);
  tresc += `xref\n0 ${obiekty.length + 1}\n0000000000 65535 f \n`;
  for (const o of offsety) tresc += `${o.toString().padStart(10, '0')} 00000 n \n`;
  tresc += `trailer\n<< /Size ${obiekty.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`;
  return Buffer.from(tresc, 'latin1');
}

/** Identyfikator JSF z formularzem (prependId): [id="rej:f-nad"]. */
export function jsf(id: string): string {
  return `[id="${id}"]`;
}
