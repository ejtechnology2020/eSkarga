#!/usr/bin/env python3
"""Diagram ER (Mermaid) z encji JPA (T-010): tabele, kolumny i klucze obce z adnotacji @Table/@Column/@JoinColumn. Wynik: docs/model-er.md"""
import re, pathlib, sys
root = pathlib.Path(__file__).resolve().parents[2]
src = root / 'app/src/main/java/pl/ejtechnology/tk/eSkarga'
entities = {}
for f in list((src / 'domain').rglob('*.java')) + list((src / 'audit').rglob('*.java')):
    s = f.read_text(encoding='utf-8')
    if '@Entity' not in s:
        continue
    table = re.search(r'@Table\(name\s*=\s*"(\w+)"', s)
    name = table.group(1) if table else re.sub(r'(?<!^)(?=[A-Z])', '_', f.stem).lower()
    cols, fks = [], []
    for m in re.finditer(r'@Column\(name\s*=\s*"(\w+)"[^)]*\)\s*(?:@\w+(?:\([^)]*\))?\s*)*private\s+([\w<>.]+)\s+(\w+)', s):
        cols.append((m.group(1), m.group(2).split('.')[-1]))
    for m in re.finditer(r'@JoinColumn\(name\s*=\s*"(\w+)"[^)]*\)\s*(?:@\w+(?:\([^)]*\))?\s*)*private\s+(\w+)\s+(\w+)', s):
        fks.append((m.group(1), m.group(2)))
    entities[f.stem] = (name, cols, fks)
class_to_table = {k: v[0] for k, v in entities.items()}
out = ['# Model danych – diagram ER (generowany: `infra/scripts/er-diagram.py`)', '', 'Schemat `repertorium` (Flyway V1–V5). Wpisy repertorium są wersjonowane (`case_file_version`), korekty decydowane osobnym wierszem (`correction_decision`).', '', '```mermaid', 'erDiagram']
for cls, (table, cols, fks) in sorted(entities.items()):
    out.append(f'  {table} {{')
    out.append('    bigint id PK')
    for c, t in cols[:14]:
        out.append(f'    {t.lower()} {c}')
    for c, target in fks:
        out.append(f'    bigint {c} FK')
    out.append('  }')
for cls, (table, cols, fks) in sorted(entities.items()):
    for c, target in fks:
        if target in class_to_table:
            out.append(f'  {class_to_table[target]} ||--o{{ {table} : "{c}"')
out.append('```')
(root / 'docs/model-er.md').write_text('\n'.join(out) + '\n', encoding='utf-8')
print(f'{len(entities)} encji → docs/model-er.md')
