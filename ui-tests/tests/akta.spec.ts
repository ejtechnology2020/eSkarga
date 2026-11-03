import { test, expect } from '@playwright/test';

// S-14: sygnatura, status i termin widoczne bez przewijania w 1366×768 (T-033); widok dzielony (T-034).
test('nagłówek sprawy widoczny bez przewijania', async ({ page }) => {
  await page.goto('/akta/podglad.xhtml?rola=asy&sygnatura=Ts%20312/26');
  const header = page.locator('.case-h, header.case').first();
  await expect(header).toBeVisible();
  const box = await header.boundingBox();
  expect(box && box.y + box.height).toBeLessThan(768);
  await expect(page.getByText('Ts 312/26').first()).toBeVisible();
});

test('widok dzielony pokazuje dwa dokumenty', async ({ page }) => {
  await page.goto('/akta/podglad.xhtml?rola=asy&sygnatura=Ts%20312/26&widok=dzielony');
  await expect(page.locator('.pane-h')).toHaveCount(2);
});
