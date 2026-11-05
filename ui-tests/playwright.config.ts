import { defineConfig } from '@playwright/test';
// ESKARGA_URL wskazuje bazowy adres aplikacji (domyślnie środowisko dev z infra/docker: :8082).
// Projekt „demo”: ekrany w trybie demo (?rola=, ?stan=) i audyt axe. Projekt „oidc”: procesy od końca do końca przez ID-users
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
  },
  projects: [
    { name: 'demo', testIgnore: /tests\/e2e\// },
    { name: 'oidc', testMatch: /tests\/e2e\/.*\.spec\.ts/, fullyParallel: false },
  ],
  reporter: [['list'], ['html', { open: 'never' }]],
});
