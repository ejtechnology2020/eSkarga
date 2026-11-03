import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

// Audyt axe-core bez błędów krytycznych (T-005, T-033): ekrany krytyczne w trybie demo (?rola=).
const screens = [
  ['/pulpit.xhtml?rola=ref', 'S-01 Pulpit'],
  ['/wplyw/rejestracja.xhtml?rola=bp', 'S-03 Rejestracja wpływu'],
  ['/akta/podglad.xhtml?rola=asy&sygnatura=Ts%20312/26', 'S-14 Podgląd akt'],
  ['/czynnosci/podpis.xhtml?rola=sed&stan=1', 'S-16 Podpis'],
];
for (const [path, name] of screens) {
  test(`@a11y ${name} bez błędów krytycznych axe`, async ({ page }) => {
    await page.goto(path);
    const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa']).analyze();
    const critical = results.violations.filter(v => v.impact === 'critical' || v.impact === 'serious');
    expect(critical, JSON.stringify(critical.map(v => ({ id: v.id, nodes: v.nodes.length })), null, 2)).toHaveLength(0);
  });
}
