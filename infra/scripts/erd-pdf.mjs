// PDF z docs/erd/model-fizyczny.html (po erd-pdf.py) przez Playwright/Chromium: node infra/scripts/erd-pdf.mjs [wyjście.pdf]
import { createRequire } from 'node:module';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { resolve, dirname } from 'node:path';
const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
// Playwright z ui-tests/node_modules (ten sam, co testy GUI) albo z instalacji globalnej
let chromium;
try { ({ chromium } = createRequire(resolve(root, 'ui-tests/package.json'))('playwright')); } catch { ({ chromium } = createRequire(import.meta.url)('playwright')); }
const html = resolve(root, 'docs/erd/model-fizyczny.html');
const out = process.argv[2] ?? resolve(root, 'docs/erd/ERD-eSkarga.pdf');
const browser = await chromium.launch(process.env.ESKARGA_CHROMIUM ? { executablePath: process.env.ESKARGA_CHROMIUM } : {});
const page = await browser.newPage();
await page.goto(pathToFileURL(html).href, { waitUntil: 'load' });
await page.pdf({ path: out, format: 'A3', landscape: true, printBackground: true, margin: { top: '12mm', bottom: '12mm', left: '12mm', right: '12mm' } });
await browser.close();
console.log(out);
