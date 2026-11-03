import { defineConfig } from '@playwright/test';
// Aplikacja w trybie demo: mvn -pl app ... albo compose; ESKARGA_URL wskazuje bazowy adres (domyślnie dev).
export default defineConfig({
  testDir: './tests',
  timeout: 60_000,
  retries: 0,
  use: {
    baseURL: process.env.ESKARGA_URL || 'http://localhost:8080/eskarga',
    viewport: { width: 1366, height: 768 },
    locale: 'pl-PL',
    screenshot: 'only-on-failure',
  },
  reporter: [['list'], ['html', { open: 'never' }]],
});
