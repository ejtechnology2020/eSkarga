#!/usr/bin/env python3
"""ERD modelu fizycznego jako samodzielna strona HTML z własnym rysunkiem SVG (bez bibliotek), do druku na PDF.
Wejście: pliki docs/erd/*.mmd z erd-fizyczny.py (składnia Mermaid erDiagram). Wyjście: docs/erd/model-fizyczny.html.
PDF: node infra/scripts/erd-pdf.mjs (Playwright/Chromium: A3 poziomo, jedna grupa na stronę)."""
import pathlib, re, html, datetime

ROOT = pathlib.Path(__file__).resolve().parents[2]
ERD = ROOT / 'docs' / 'erd'
GROUPS = [('przeglad', 'Przegląd: tabele, klucze i relacje'), ('wplyw-i-repertorium', 'Wpływ pism i repertorium'),
          ('akta-i-czynnosci', 'Akta, czynności i podpisy'), ('doreczenia-terminy-zazalenia', 'Doręczenia, terminy, zażalenia, archiwum'),
          ('dostep-i-slowniki', 'Dostęp, osoby, słowniki, audyt')]
CHAR_W, ROW_H, HEAD_H, PAD = 7.2, 17, 26, 10
FONT = 12


def parse(mmd):
    tables, rels, cur = {}, [], None
    for line in mmd.splitlines():
        s = line.strip()
        m = re.match(r'^(\w+)\s*\{$', s)
        if m:
            cur = m.group(1)
            tables[cur] = []
            continue
        if s == '}':
            cur = None
            continue
        if cur:
            m = re.match(r'^(\S+)\s+(\S+)(?:\s+([A-Z,]+))?(?:\s+"([^"]*)")?$', s)
            if m:
                tables[cur].append((m.group(1), m.group(2), m.group(3) or '', m.group(4) or ''))
            continue
        m = re.match(r'^(\w+)\s+\|\|--(o\{|o\|)\s+(\w+)\s*:\s*"([^"]*)"$', s)
        if m:
            rels.append((m.group(1), m.group(3), m.group(4), m.group(2) == 'o|'))
    return tables, rels


def box_size(name, cols):
    w = max([len(name) + 2] + [len(f'{t} {c} {k}') + (len(n) + 3 if n else 0) for t, c, k, n in cols]) * CHAR_W + 2 * PAD
    return max(170, min(w, 560)), HEAD_H + ROW_H * max(1, len(cols)) + PAD


def layout(tables, rels, max_cols, max_height=1700):
    """Kolumny według głębokości relacji: tabele bez rodziców (w tej grupie) po lewej, dzieci na prawo od rodziców – linie FK biegną
    w prawo, zwykle między sąsiednimi kolumnami. Zbyt wysoka warstwa jest dzielona na kilka kolumn; w kolumnie kolejność po
    średniej pozycji rodziców (mniej przecięć)."""
    parents_of = {t: set() for t in tables}
    for p, c, col, one in rels:
        if p in tables and c in tables and p != c:
            parents_of[c].add(p)
    depth = {}

    def d(t, seen=()):
        if t in depth:
            return depth[t]
        if t in seen:
            return 0
        depth[t] = 0 if not parents_of[t] else 1 + max(d(p, seen + (t,)) for p in parents_of[t])
        return depth[t]
    for t in tables:
        d(t)
    layers = {}
    for t in tables:
        layers.setdefault(depth[t], []).append(t)
    pos, x, total_h = {}, 20, 0
    centre = {}
    for lvl in sorted(layers):
        names = layers[lvl]
        # kolejność: barycentrum rodziców (już ułożonych), potem liczba dzieci malejąco, potem nazwa
        names.sort(key=lambda t: (sum(centre[p] for p in parents_of[t] if p in centre) / max(1, len([p for p in parents_of[t] if p in centre])) if parents_of[t] else 0,
                                  -sum(1 for p, c, col, one in rels if p == t), t))
        cols, cur, h = [], [], 0
        for n in names:
            bh = box_size(n, tables[n])[1] + 36
            if cur and h + bh > max_height:
                cols.append(cur)
                cur, h = [], 0
            cur.append(n)
            h += bh
        if cur:
            cols.append(cur)
        for col in cols:
            colw = max(box_size(n, tables[n])[0] for n in col)
            y = 20
            for n in col:
                w, h = box_size(n, tables[n])
                pos[n] = (x, y, w, h)
                centre[n] = y + h / 2
                y += h + 36
            total_h = max(total_h, y)
            x += colw + 110
    return pos, x, total_h


def svg(tables, rels, max_cols, max_height=1700):
    pos, W, H = layout(tables, rels, max_cols, max_height)
    out = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W:.0f} {H:.0f}" width="{W:.0f}" height="{H:.0f}" font-family="IBM Plex Mono, Consolas, monospace" font-size="{FONT}">',
           '<defs><marker id="crow" markerWidth="12" markerHeight="12" refX="11" refY="6" orient="auto" markerUnits="userSpaceOnUse">'
           '<path d="M1,1 L11,6 L1,11 M1,6 L11,6" fill="none" stroke="#3b4a56" stroke-width="1.3"/></marker>'
           '<marker id="one" markerWidth="10" markerHeight="12" refX="9" refY="6" orient="auto" markerUnits="userSpaceOnUse">'
           '<path d="M4,1 L4,11 M1,6 L9,6" fill="none" stroke="#3b4a56" stroke-width="1.3"/></marker></defs>']
    rowy = {}
    for n, (x, y, w, h) in pos.items():
        out.append(f'<g><rect x="{x}" y="{y}" width="{w}" height="{h}" rx="6" fill="#ffffff" stroke="#3b4a56" stroke-width="1.2"/>'
                   f'<rect x="{x}" y="{y}" width="{w}" height="{HEAD_H}" rx="6" fill="#dfe9e4" stroke="#3b4a56" stroke-width="1.2"/>'
                   f'<rect x="{x}" y="{y + HEAD_H - 6}" width="{w}" height="6" fill="#dfe9e4"/>'
                   f'<text x="{x + w / 2}" y="{y + 18}" text-anchor="middle" font-weight="700" font-family="IBM Plex Sans, Segoe UI, sans-serif" font-size="13">{html.escape(n)}</text>')
        cy = y + HEAD_H + 13
        for t, c, k, note in tables[n]:
            rowy[(n, c)] = cy - 5
            key = f' <tspan font-weight="700" fill="#1f5e4b">{html.escape(k)}</tspan>' if k else ''
            nn = f' <tspan fill="#6b7680">{html.escape(note)}</tspan>' if note else ''
            out.append(f'<text x="{x + PAD}" y="{cy}"><tspan fill="#6b7680">{html.escape(t)}</tspan> {html.escape(c)}{key}{nn}</text>')
            cy += ROW_H
        out.append('</g>')
    edges = []
    for p, c, col, one in rels:
        if p not in pos or c not in pos:
            continue
        px, py, pw, ph = pos[p]
        cx, cy, cw, ch = pos[c]
        y1 = rowy.get((c, col), cy + HEAD_H / 2)
        y2 = py + HEAD_H / 2
        if px + pw <= cx:         # rodzic po lewej (układ warstwowy)
            x1, x2 = cx, px + pw
            xm = x2 + 40 if cx - (px + pw) > 60 else (x1 + x2) / 2
        elif cx + cw <= px:       # rodzic po prawej
            x1, x2 = cx + cw, px
            xm = x1 + 40
        else:                     # ta sama kolumna: łuk z prawej strony
            x1, x2 = cx + cw, px + pw
            xm = max(x1, x2) + 24
        edges.append(f'<path d="M{x1},{y1} L{xm},{y1} L{xm},{y2} L{x2},{y2}" fill="none" stroke="#3b4a56" stroke-opacity="0.75" stroke-width="1.2" marker-start="url(#crow)" marker-end="url(#one)"/>')
    out = out[:2] + edges + out[2:]   # linie pod ramkami
    out.append('</svg>')
    return '\n'.join(out)


pages = []
for key, title in GROUPS:
    tables, rels = parse((ERD / f'{key}.mmd').read_text(encoding='utf-8'))
    detailed = key != 'przeglad'
    s = svg(tables, rels, 6 if not detailed else 4, 1500 if not detailed else 1300)
    n_fk = len(rels)
    pages.append(f'<section class="page"><h2>{html.escape(title)}</h2><p class="meta">{len(tables)} tabel · {n_fk} relacji{"" if detailed else " · tylko klucze (PK, FK, UK)"}</p><div class="dia">{s}</div></section>')
md = (ERD / 'model-fizyczny.md').read_text(encoding='utf-8')
idx = md[md.index('## Tabele'):].splitlines()[2:]
rows = ''.join('<tr>' + ''.join(f'<td>{html.escape(c.strip().strip("`"))}</td>' for c in l.strip('|').split('|')) + '</tr>' for l in idx if l.startswith('| `'))
doc = f'''<!doctype html><html lang="pl"><head><meta charset="utf-8"><title>ERD eSkarga – model fizyczny</title>
<style>
@page {{ size: A3 landscape; margin: 12mm; }}
body {{ font-family: "IBM Plex Sans", "Segoe UI", system-ui, sans-serif; color: #1d2430; margin: 0; }}
.page {{ page-break-after: always; break-after: page; }}
.cover {{ padding: 40mm 10mm; }}
h1 {{ font-size: 30px; margin: 0 0 8px; }} h2 {{ font-size: 20px; margin: 0 0 2px; }}
.meta {{ color: #5b6672; margin: 0 0 8px; font-size: 13px; }}
.dia svg {{ width: 100%; height: auto; max-height: 255mm; }}
table {{ border-collapse: collapse; font-size: 11px; width: 100%; }} th, td {{ border-bottom: 1px solid #d6dbd3; padding: 3px 6px; text-align: left; }}
td:nth-child(2), td:nth-child(4) {{ text-align: right; }}
.legend {{ font-size: 12px; color: #5b6672; }}
</style></head><body>
<section class="page cover"><h1>eSkarga – Repertorium Ts/Tw<br>Model fizyczny bazy danych (ERD)</h1>
<p class="meta">Schemat <b>repertorium</b> w PostgreSQL po migracjach Flyway V1–V12.1 · stan z {datetime.date.today().strftime("%d.%m.%Y")} · wygenerowano z katalogu bazy (<code>infra/scripts/erd-fizyczny.py</code> → <code>erd-pdf.py</code>)</p>
<p class="legend">Oznaczenia: <b>PK</b> klucz główny · <b>FK</b> klucz obcy · <b>UK</b> unikalny · linia z „kurzą stopką” przy tabeli podrzędnej (wiele) i kreską przy nadrzędnej (jeden) · <code>timestamptz</code> = timestamp with time zone · w nawiasach długości typów.</p>
<p class="legend">Strony: 1) przegląd kluczy i relacji wszystkich tabel, 2) wpływ pism i repertorium, 3) akta, czynności i podpisy, 4) doręczenia, terminy, zażalenia, archiwum, 5) dostęp, osoby, słowniki, audyt, 6) indeks tabel.</p>
</section>
{''.join(pages)}
<section><h2>Indeks tabel</h2><table><thead><tr><th>Tabela</th><th>Kolumny</th><th>Klucze obce (wychodzące)</th><th>Indeksy</th></tr></thead><tbody>{rows}</tbody></table></section>
</body></html>'''
(ERD / 'model-fizyczny.html').write_text(doc, encoding='utf-8')
print('docs/erd/model-fizyczny.html')
