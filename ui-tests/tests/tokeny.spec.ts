import { test, expect } from '@playwright/test';

// T-004: żaden token nie trafia do przeglądarki – tylko ciasteczko sesji (HttpOnly). Wymaga trybu oidc z mock OIDC.
test('po zalogowaniu przeglądarka ma tylko ciasteczko sesji', async ({ page, context }) => {
  test.skip(!process.env.ESKARGA_OIDC_TEST, 'wymaga mock OIDC (ESKARGA_OIDC_TEST=1)');
  await page.goto('/pulpit.xhtml');
  await page.getByLabel('Nazwa użytkownika').fill('jtestowy');
  await page.getByLabel('Hasło').fill(process.env.ESKARGA_TEST_PASSWORD || 'test');
  await page.getByRole('button', { name: /zaloguj/i }).click();
  await expect(page).toHaveURL(/pulpit/);
  const cookies = await context.cookies();
  for (const c of cookies) {
    expect(c.httpOnly, `ciasteczko ${c.name} musi być HttpOnly`).toBeTruthy();
    expect(c.value).not.toMatch(/^eyJ/); // JWT nigdy w ciasteczku
  }
  const storage = await page.evaluate(() => JSON.stringify({ ...localStorage, ...sessionStorage }));
  expect(storage).not.toMatch(/eyJ[A-Za-z0-9_-]{10,}/);
});
