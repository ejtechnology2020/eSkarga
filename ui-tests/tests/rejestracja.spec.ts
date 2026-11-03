import { test, expect } from '@playwright/test';

// UC-01: rejestracja pisma w mniej niż 3 minuty – ścieżka główna i błędy walidacji (T-011).
test('rejestracja pisma nadaje numer dziennika', async ({ page }) => {
  await page.goto('/wplyw/rejestracja.xhtml?rola=bp&stan=wypelniony');
  await page.getByRole('button', { name: /zarejestruj/i }).first().click();
  await expect(page.getByText(/DW 2026\//)).toBeVisible();
});

test('pusty formularz pokazuje błędy pól', async ({ page }) => {
  await page.goto('/wplyw/rejestracja.xhtml?rola=bp&stan=pusty');
  await page.getByRole('button', { name: /zarejestruj/i }).first().click();
  await expect(page.getByText(/Popraw/)).toBeVisible();
});
