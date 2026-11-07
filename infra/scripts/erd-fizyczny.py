#!/usr/bin/env python3
"""Diagram ERD modelu fizycznego (schemat `repertorium` w PostgreSQL) z katalogu systemowego bazy – tabele, kolumny (typ, NOT NULL,
PK/FK/UK, domyślne) i klucze obce, w notacji Mermaid erDiagram. Wynik: docs/erd/model-fizyczny.md (+ pliki .mmd do renderowania).

Uruchomienie (baza po migracjach Flyway, np. środowisko testowe PostgreSQL z ADR 0011):
  PGHOST=/tmp PGPORT=5434 PGUSER=eskarga_migration PGPASSWORD=… PGDATABASE=eskarga_it python3 infra/scripts/erd-fizyczny.py
GitHub renderuje bloki ```mermaid w Markdown; SVG (opcjonalnie, stacja z mermaid-cli): mmdc -i docs/erd/<nazwa>.mmd -o docs/erd/<nazwa>.svg
"""
import csv, io, os, pathlib, subprocess, sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / 'docs' / 'erd'
SCHEMA = os.environ.get('ESKARGA_SCHEMA', 'repertorium')

# Grupy diagramów szczegółowych (tabela → grupa); tabele spoza listy trafiają do „pozostałe”.
GROUPS = {
    'wplyw-i-repertorium': ('Wpływ pism i repertorium', ['incoming_counter', 'incoming_letter', 'incoming_scan', 'general_letter', 'docket_counter', 'case_file',
                                                         'case_file_version', 'correction_decision', 'party', 'power_of_attorney', 'final_ruling', 'review_subject']),
    'akta-i-czynnosci': ('Akta, czynności i podpisy', ['case_document', 'procedural_act', 'letter_template', 'highlight', 'internal_note', 'checklist_entry', 'formal_requirement']),
    'doreczenia-terminy-zazalenia': ('Doręczenia, terminy, zażalenia, archiwum', ['delivery', 'deadline', 'appeal', 'holiday', 'archive_category', 'archive_transfer', 'access_request', 'publication']),
    'dostep-i-slowniki': ('Dostęp, osoby, słowniki, audyt', ['case_assignment', 'delegation', 'judge', 'app_user_seen', 'audit_event', 'court', 'authority', 'letter_type', 'review_standard']),
}
SKIP = {'flyway_schema_history'}


def psql(sql):
    cmd = ['psql', '-X', '-A', '-t', '--csv', '-v', 'ON_ERROR_STOP=1', '-c', sql]
    res = subprocess.run(cmd, capture_output=True, text=True, env=os.environ)
    if res.returncode != 0:
        sys.exit('psql: ' + res.stderr.strip())
    return list(csv.reader(io.StringIO(res.stdout)))


def mermaid_type(data_type, char_len, num_prec, num_scale):
    t = data_type
    if t == 'character varying':
        return f'varchar({char_len})' if char_len else 'varchar'
    if t == 'character':
        return f'char({char_len})'
    if t == 'timestamp with time zone':
        return 'timestamptz'
    if t == 'timestamp without time zone':
        return 'timestamp'
    if t == 'numeric':
        return f'numeric({num_prec}_{num_scale})' if num_prec else 'numeric'
    if t == 'double precision':
        return 'double'
    return t.replace(' ', '_')


tables = [r[0] for r in psql(f"select table_name from information_schema.tables where table_schema='{SCHEMA}' and table_type='BASE TABLE' order by 1") if r and r[0] not in SKIP]
columns = {}
for t, c, dt, nullable, cl, np_, ns, default in psql(
        f"select table_name, column_name, data_type, is_nullable, coalesce(character_maximum_length::text,''), coalesce(numeric_precision::text,''), coalesce(numeric_scale::text,''), coalesce(column_default,'') "
        f"from information_schema.columns where table_schema='{SCHEMA}' order by table_name, ordinal_position"):
    columns.setdefault(t, []).append((c, mermaid_type(dt, cl, np_, ns), nullable == 'NO', default))
keys = {}   # (table, column) -> set of PK/FK/UK
fks = []    # (child, column, parent, parent_column, constraint)
for con, ctype, t, c, ft, fc in psql(
        f"select tc.constraint_name, tc.constraint_type, tc.table_name, kcu.column_name, coalesce(ccu.table_name,''), coalesce(ccu.column_name,'') "
        f"from information_schema.table_constraints tc join information_schema.key_column_usage kcu on kcu.constraint_name=tc.constraint_name and kcu.table_schema=tc.table_schema "
        f"left join information_schema.constraint_column_usage ccu on ccu.constraint_name=tc.constraint_name and tc.constraint_type='FOREIGN KEY' "
        f"where tc.table_schema='{SCHEMA}' and tc.constraint_type in ('PRIMARY KEY','FOREIGN KEY','UNIQUE') order by tc.table_name, kcu.ordinal_position"):
    tag = {'PRIMARY KEY': 'PK', 'FOREIGN KEY': 'FK', 'UNIQUE': 'UK'}[ctype]
    keys.setdefault((t, c), []).append(tag)
    if tag == 'FK':
        fks.append((t, c, ft, fc, con))
fks = sorted(set(fks))
indexes = {r[0]: int(r[1]) for r in psql(f"select tablename, count(*) from pg_indexes where schemaname='{SCHEMA}' group by tablename")}


def entity(t, detailed=True):
    out = [f'  {t} {{']
    for c, typ, notnull, default in columns.get(t, []):
        tags = keys.get((t, c), [])
        if not detailed and not tags:
            continue
        key = (' ' + ','.join(dict.fromkeys(tags))) if tags else ''
        notes = []
        if notnull and 'PK' not in tags:
            notes.append('NOT NULL')
        if default and 'nextval' not in default and 'identity' not in default.lower():
            notes.append('default ' + default.replace('"', "'").replace('::', ' '))
        comment = f' "{"; ".join(notes)}"' if (detailed and notes) else ''
        out.append(f'    {typ} {c}{key}{comment}')
    out.append('  }')
    return out


def relations(subset):
    out = []
    for child, col, parent, pcol, con in fks:
        if child in subset and parent in subset:
            # unikalny FK = relacja 1:1 (np. correction_decision.version_id)
            one = 'UK' in keys.get((child, col), [])
            out.append(f'  {parent} ||--{"o|" if one else "o{"} {child} : "{col}"')
    return out


def diagram(subset, detailed):
    lines = ['erDiagram']
    for t in subset:
        lines += entity(t, detailed)
    lines += relations(set(subset))
    return '\n'.join(lines) + '\n'


OUT.mkdir(parents=True, exist_ok=True)
grouped = {g: [t for t in ts if t in tables] for g, (_, ts) in GROUPS.items()}
rest = [t for t in tables if not any(t in ts for ts in grouped.values())]
if rest:
    GROUPS['pozostale'] = ('Pozostałe', rest)
    grouped['pozostale'] = rest

md = ['# Model fizyczny – diagram ERD (generowany: `infra/scripts/erd-fizyczny.py`)', '',
      f'Schemat `{SCHEMA}` w PostgreSQL po migracjach Flyway (`app/src/main/resources/db/migration`, uprawnienia roli aplikacji w `migration-postgresql`). '
      f'{len(tables)} tabel, {len(fks)} kluczy obcych. Typy wg katalogu bazy (`timestamptz` = `timestamp with time zone`). '
      'Relacja `||--o|` to klucz obcy z ograniczeniem UNIQUE (1:1), `||--o{` – 1:N. Diagram przeglądowy pokazuje tylko klucze; diagramy grup – wszystkie kolumny.', '',
      '## Przegląd (klucze i relacje)', '', '```mermaid', diagram(tables, False).rstrip(), '```', '']
(OUT / 'przeglad.mmd').write_text(diagram(tables, False), encoding='utf-8')
for g, (title, _) in GROUPS.items():
    ts = grouped[g]
    if not ts:
        continue
    (OUT / f'{g}.mmd').write_text(diagram(ts, True), encoding='utf-8')
    md += [f'## {title}', '', f'Tabele: ' + ', '.join(f'`{t}`' for t in ts), '', '```mermaid', diagram(ts, True).rstrip(), '```', '']
md += ['## Tabele', '', '| Tabela | Kolumny | Klucze obce (wychodzące) | Indeksy |', '| --- | ---: | --- | ---: |']
for t in tables:
    outgoing = ', '.join(f'{c} → {p}' for ch, c, p, pc, con in fks if ch == t)
    md.append(f'| `{t}` | {len(columns.get(t, []))} | {outgoing or "—"} | {indexes.get(t, 0)} |')
(OUT / 'model-fizyczny.md').write_text('\n'.join(md) + '\n', encoding='utf-8')
print(f'{len(tables)} tabel, {len(fks)} FK → {OUT.relative_to(ROOT)}/model-fizyczny.md (+ {1 + len([g for g in grouped if grouped[g]])} plików .mmd)')
