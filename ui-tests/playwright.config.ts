import { defineConfig } from '@playwright/test';
// ESKARGA_URL wskazuje bazowy adres aplikacji (domyślnie środowisko dev z infra/docker: :8082).
// Projekt „widget”: widżet pdf.js z repozytorium pod CSP z web.xml, bez WildFly (także w CI). Projekt „demo”: ekrany w trybie demo (?rola=, ?stan=) i audyt axe. Projekt „oidc”: procesy od końca do końca przez ID-users
// (tests/e2e, włączane ESKARGA_E2E=1 – zmienne w tests/e2e/srodowisko.ts); kroki po kolei, więc jeden worker.
export default defineConfig({
  testDir: './tests',
  timeout: 60_000,
  retries: 0,
  use: {
    baseURL: process.env.ESKARGA_URL || 'http://localhost:8082/eskarga',
    viewport: { width: 1366, height: 768 },
    locale: 'pl-PL',
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
    // ESKARGA_CHROMIUM: ścieżka do gotowego Chromium (np. gdy wersja przeglądarki Playwright różni się od zainstalowanej)
    launchOptions: process.env.ESKARGA_CHROMIUM ? { executablePath: process.env.ESKARGA_CHROMIUM } : {},
  },
  projects: [
    { name: 'widget', testMatch: /tests\/widget\/.*\.spec\.ts/ },   // widżet PDF w Chromium bez aplikacji (serwer testowy w teście)
    { name: 'demo', testIgnore: /tests\/(e2e|widget)\// },
    { name: 'oidc', testMatch: /tests\/e2e\/.*\.spec\.ts/, fullyParallel: false },
  ],
  reporter: [['list'], ['html', { open: 'never' }]],
});
