# Model fizyczny – diagram ERD (generowany: `infra/scripts/erd-fizyczny.py`)

Schemat `repertorium` w PostgreSQL po migracjach Flyway (`app/src/main/resources/db/migration`, uprawnienia roli aplikacji w `migration-postgresql`). 36 tabel, 26 kluczy obcych. Typy wg katalogu bazy (`timestamptz` = `timestamp with time zone`). Relacja `||--o|` to klucz obcy z ograniczeniem UNIQUE (1:1), `||--o{` – 1:N. Diagram przeglądowy pokazuje tylko klucze; diagramy grup – wszystkie kolumny.

## Przegląd (klucze i relacje)

```mermaid
erDiagram
  access_request {
    bigint id PK
    bigint case_file_id FK
  }
  app_user_seen {
    bigint id PK
    varchar(100) login UK
  }
  appeal {
    bigint id PK
    bigint case_file_id FK
  }
  archive_category {
    varchar(60) closing_mode PK
  }
  archive_transfer {
    bigint id PK
    varchar(30) transfer_no UK
    bigint case_file_id FK
  }
  audit_event {
    bigint id PK
  }
  authority {
    bigint id PK
    varchar(30) code UK
  }
  case_assignment {
    bigint id PK
    bigint case_file_id FK,UK
    varchar(120) user_id UK
    varchar(20) role UK
    date assigned_on UK
  }
  case_document {
    bigint id PK
    bigint case_file_id FK
    bigint template_version_id FK
  }
  case_file {
    bigint id PK
    varchar(3) docket_symbol UK
    integer docket_number UK
    integer docket_year UK
  }
  case_file_version {
    bigint id PK
    bigint case_file_id UK,FK
    integer version UK
  }
  checklist_entry {
    bigint id PK
    bigint case_file_id FK
  }
  correction_decision {
    bigint id PK
    bigint version_id FK
  }
  court {
    bigint id PK
    varchar(30) code UK
  }
  deadline {
    bigint id PK
    bigint case_file_id FK
    bigint delivery_id FK
  }
  delegation {
    bigint id PK
  }
  delivery {
    bigint id PK
    bigint document_id FK
    bigint addressee_party_id FK
    varchar(80) idempotency_key UK
  }
  docket_counter {
    varchar(3) symbol PK
    integer year PK
  }
  final_ruling {
    bigint id PK
    bigint case_file_id FK
  }
  formal_requirement {
    bigint id PK
    varchar(4) symbol UK
    varchar(20) code UK
  }
  general_letter {
    bigint id PK
    bigint letter_id FK
    bigint reply_document_id FK
  }
  highlight {
    bigint id PK
    bigint document_id FK
  }
  holiday {
    date day PK
  }
  incoming_counter {
    integer year PK
  }
  incoming_letter {
    bigint id PK
    varchar(20) register_no UK
    bigint case_file_id FK
  }
  incoming_scan {
    bigint id PK
    bigint letter_id FK
  }
  internal_note {
    bigint id PK
    bigint case_file_id FK
  }
  judge {
    bigint id PK
    varchar(100) user_id UK
  }
  letter_template {
    bigint id PK
    varchar(60) code UK
    integer version UK
  }
  letter_type {
    bigint id PK
    varchar(30) code UK
  }
  party {
    bigint id PK
    bigint case_file_id FK
  }
  power_of_attorney {
    bigint id PK
    bigint party_id FK
  }
  procedural_act {
    bigint id PK
    bigint case_file_id FK
    bigint document_id FK
  }
  publication {
    bigint id PK
    bigint document_id FK
  }
  review_standard {
    bigint id PK
    varchar(30) code UK
  }
  review_subject {
    bigint id PK
    bigint case_file_id FK
  }
  case_file ||--o{ access_request : "case_file_id"
  case_file ||--o{ appeal : "case_file_id"
  case_file ||--o{ archive_transfer : "case_file_id"
  case_file ||--o| case_assignment : "case_file_id"
  case_file ||--o{ case_document : "case_file_id"
  letter_template ||--o{ case_document : "template_version_id"
  case_file ||--o| case_file_version : "case_file_id"
  case_file ||--o{ checklist_entry : "case_file_id"
  case_file_version ||--o{ correction_decision : "version_id"
  case_file ||--o{ deadline : "case_file_id"
  delivery ||--o{ deadline : "delivery_id"
  party ||--o{ delivery : "addressee_party_id"
  case_document ||--o{ delivery : "document_id"
  case_file ||--o{ final_ruling : "case_file_id"
  incoming_letter ||--o{ general_letter : "letter_id"
  case_document ||--o{ general_letter : "reply_document_id"
  case_document ||--o{ highlight : "document_id"
  case_file ||--o{ incoming_letter : "case_file_id"
  incoming_letter ||--o{ incoming_scan : "letter_id"
  case_file ||--o{ internal_note : "case_file_id"
  case_file ||--o{ party : "case_file_id"
  party ||--o{ power_of_attorney : "party_id"
  case_file ||--o{ procedural_act : "case_file_id"
  case_document ||--o{ procedural_act : "document_id"
  case_document ||--o{ publication : "document_id"
  case_file ||--o{ review_subject : "case_file_id"
```

## Wpływ pism i repertorium

Tabele: `incoming_counter`, `incoming_letter`, `incoming_scan`, `general_letter`, `docket_counter`, `case_file`, `case_file_version`, `correction_decision`, `party`, `power_of_attorney`, `final_ruling`, `review_subject`

```mermaid
erDiagram
  incoming_counter {
    integer year PK
    integer last_value "NOT NULL; default 0"
  }
  incoming_letter {
    bigint id PK
    varchar(20) register_no UK "NOT NULL"
    timestamptz received_at "NOT NULL"
    date filed_on
    boolean filed_on_uncertain "NOT NULL; default false"
    varchar(20) channel "NOT NULL"
    varchar(400) sender "NOT NULL"
    integer attachments "NOT NULL; default 0"
    integer copies "NOT NULL; default 0"
    boolean classified "NOT NULL; default false"
    varchar(30) qualification
    bigint case_file_id FK
    varchar(120) created_by "NOT NULL"
    timestamptz created_at "NOT NULL; default now()"
  }
  incoming_scan {
    bigint id PK
    bigint letter_id FK "NOT NULL"
    varchar(255) file_name "NOT NULL"
    varchar(100) content_type "NOT NULL"
    bigint size_bytes "NOT NULL"
    char(64) sha256 "NOT NULL"
    varchar(400) storage_key "NOT NULL"
    varchar(100) uploaded_by "NOT NULL"
    timestamptz uploaded_at "NOT NULL"
  }
  general_letter {
    bigint id PK
    bigint letter_id FK "NOT NULL"
    varchar(600) subject
    date reply_due_on
    date replied_on
    bigint reply_document_id FK
    varchar(60) template_code
  }
  docket_counter {
    varchar(3) symbol PK
    integer year PK
    integer last_value "NOT NULL; default 0"
  }
  case_file {
    bigint id PK
    varchar(3) docket_symbol UK "NOT NULL"
    integer docket_number UK "NOT NULL"
    integer docket_year UK "NOT NULL"
    date entered_on "NOT NULL"
    varchar(64) process_instance_id
    varchar(120) created_by "NOT NULL"
    timestamptz created_at "NOT NULL; default now()"
  }
  case_file_version {
    bigint id PK
    bigint case_file_id UK,FK "NOT NULL"
    integer version UK "NOT NULL"
    varchar(40) status "NOT NULL"
    varchar(120) judge_user_id
    boolean flag_urgent "NOT NULL; default false"
    boolean flag_anonymise "NOT NULL; default false"
    boolean flag_suspended "NOT NULL; default false"
    varchar(1000) review_subject
    varchar(1000) review_standard
    date closed_on
    varchar(60) closing_mode
    varchar(20) related_docket
    varchar(2000) justification
    varchar(120) created_by "NOT NULL"
    timestamptz created_at "NOT NULL; default now()"
    boolean is_correction "NOT NULL; default false"
  }
  correction_decision {
    bigint id PK
    bigint version_id FK "NOT NULL"
    varchar(20) decision "NOT NULL"
    varchar(120) decided_by "NOT NULL"
    timestamptz decided_at "NOT NULL"
    varchar(2000) remarks
  }
  party {
    bigint id PK
    bigint case_file_id FK "NOT NULL"
    varchar(30) role "NOT NULL"
    varchar(400) display_name "NOT NULL"
    varchar(600) delivery_address
    varchar(120) e_delivery_address
    timestamptz created_at "NOT NULL; default now()"
  }
  power_of_attorney {
    bigint id PK
    bigint party_id FK "NOT NULL"
    varchar(30) kind "NOT NULL"
    varchar(30) profession
    varchar(60) registry_no
    date granted_on
    varchar(400) scope
  }
  final_ruling {
    bigint id PK
    bigint case_file_id FK "NOT NULL"
    varchar(400) authority "NOT NULL"
    varchar(120) ruling_ref
    date issued_on
    date delivered_on
    boolean extraordinary_remedy "NOT NULL; default false"
  }
  review_subject {
    bigint id PK
    bigint case_file_id FK "NOT NULL"
    varchar(400) act "NOT NULL"
    varchar(120) unit
    varchar(400) standard
  }
  case_file ||--o| case_file_version : "case_file_id"
  case_file_version ||--o{ correction_decision : "version_id"
  case_file ||--o{ final_ruling : "case_file_id"
  incoming_letter ||--o{ general_letter : "letter_id"
  case_file ||--o{ incoming_letter : "case_file_id"
  incoming_letter ||--o{ incoming_scan : "letter_id"
  case_file ||--o{ party : "case_file_id"
  party ||--o{ power_of_attorney : "party_id"
  case_file ||--o{ review_subject : "case_file_id"
```

## Akta, czynności i podpisy

Tabele: `case_document`, `procedural_act`, `letter_template`, `highlight`, `internal_note`, `checklist_entry`, `formal_requirement`

```mermaid
erDiagram
  case_document {
    bigint id PK
    bigint case_file_id FK "NOT NULL"
    integer volume_no "NOT NULL; default 1"
    integer page_from
    integer page_to
    varchar(400) title "NOT NULL"
    varchar(30) kind "NOT NULL"
    date document_date
    varchar(400) storage_key
    char(64) sha256
    integer version "NOT NULL; default 1"
    boolean classified "NOT NULL; default false"
    boolean internal_note "NOT NULL; default false"
    varchar(400) anonymised_key
    varchar(30) signature_status
    bigint template_version_id FK
    varchar(120) created_by "NOT NULL"
    timestamptz created_at "NOT NULL; default now()"
    boolean pdfa_compliant "NOT NULL; default false"
    boolean ocr_done "NOT NULL; default false"
    varchar(2000) signature_report
    varchar(120) source_content_type
    varchar(400) source_storage_key
    varchar(300) source_file_name
  }
  procedural_act {
    bigint id PK
    bigint case_file_id FK "NOT NULL"
    varchar(40) kind "NOT NULL"
    varchar(60) subtype
    timestamptz acted_on "NOT NULL"
    varchar(120) author_user_id "NOT NULL"
    varchar(400) panel_user_ids
    varchar(200) legal_basis
    bigint document_id FK
    varchar(30) status "NOT NULL"
  }
  letter_template {
    bigint id PK
    varchar(60) code UK "NOT NULL"
    varchar(200) name "NOT NULL"
    integer version UK "NOT NULL"
    varchar(20) status "NOT NULL"
    text source_xhtml "NOT NULL"
    varchar(120) author_user_id "NOT NULL"
    varchar(120) approved_by
    timestamptz created_at "NOT NULL; default now()"
  }
  highlight {
    bigint id PK
    bigint document_id FK "NOT NULL"
    varchar(120) author_user_id "NOT NULL"
    integer page_no "NOT NULL"
    varchar(4000) geometry_json "NOT NULL"
    varchar(2000) comment_text
    timestamptz created_at "NOT NULL; default now()"
  }
  internal_note {
    bigint id PK
    bigint case_file_id FK "NOT NULL"
    varchar(120) author_user_id "NOT NULL"
    varchar(300) title "NOT NULL"
    text body "NOT NULL"
    timestamptz created_at "NOT NULL; default now()"
  }
  checklist_entry {
    bigint id PK
    bigint case_file_id FK "NOT NULL"
    varchar(60) requirement_code "NOT NULL"
    varchar(20) state "NOT NULL"
    varchar(500) note
    varchar(40) page_ref
    varchar(120) decided_by "NOT NULL"
    timestamp decided_at "NOT NULL"
  }
  formal_requirement {
    bigint id PK
    varchar(4) symbol UK "NOT NULL"
    varchar(20) code UK "NOT NULL"
    varchar(600) description "NOT NULL"
    varchar(160) legal_basis "NOT NULL"
    varchar(30) condition_code "NOT NULL"
    integer position "NOT NULL"
    boolean active "NOT NULL; default true"
    varchar(120) created_by "NOT NULL"
    timestamptz created_at "NOT NULL"
    varchar(120) updated_by
    timestamptz updated_at
  }
  letter_template ||--o{ case_document : "template_version_id"
  case_document ||--o{ highlight : "document_id"
  case_document ||--o{ procedural_act : "document_id"
```

## Doręczenia, terminy, zażalenia, archiwum

Tabele: `delivery`, `deadline`, `appeal`, `holiday`, `archive_category`, `archive_transfer`, `access_request`, `publication`

```mermaid
erDiagram
  delivery {
    bigint id PK
    bigint document_id FK "NOT NULL"
    bigint addressee_party_id FK
    varchar(30) channel "NOT NULL"
    varchar(80) dispatch_no
    date sent_on
    date delivered_on
    varchar(20) method
    varchar(80) idempotency_key UK "NOT NULL"
    timestamptz created_at "NOT NULL; default now()"
  }
  deadline {
    bigint id PK
    bigint case_file_id FK "NOT NULL"
    bigint delivery_id FK
    varchar(20) kind "NOT NULL"
    date starts_on "NOT NULL"
    date ends_on "NOT NULL"
    date suspended_from
    date suspended_to
    varchar(20) status "NOT NULL"
    timestamptz created_at "NOT NULL; default now()"
  }
  appeal {
    bigint id PK
    bigint case_file_id FK "NOT NULL"
    date filed_on "NOT NULL"
    boolean in_time
    varchar(400) panel_user_ids
    date session_on
    varchar(40) outcome
    timestamptz created_at "NOT NULL; default now()"
  }
  holiday {
    date day PK
    varchar(200) description "NOT NULL"
    varchar(120) created_by "NOT NULL"
    timestamptz created_at "NOT NULL; default now()"
  }
  archive_category {
    varchar(60) closing_mode PK
    varchar(10) category "NOT NULL"
    integer retention_years
    varchar(120) updated_by
    timestamptz updated_at
  }
  archive_transfer {
    bigint id PK
    varchar(30) transfer_no UK "NOT NULL"
    bigint case_file_id FK "NOT NULL"
    varchar(10) category "NOT NULL"
    integer retention_until
    integer pages "NOT NULL"
    varchar(400) package_key
    char(64) package_sha256
    varchar(120) transferred_by "NOT NULL"
    timestamptz transferred_at "NOT NULL; default now()"
  }
  access_request {
    bigint id PK
    bigint case_file_id FK "NOT NULL"
    varchar(400) requester "NOT NULL"
    boolean participant "NOT NULL"
    varchar(600) scope
    numeric(10_2) fee_pln
    timestamptz reading_room_at
    boolean anonymised_ready "NOT NULL; default false"
    timestamptz created_at "NOT NULL; default now()"
  }
  publication {
    bigint id PK
    bigint document_id FK "NOT NULL"
    varchar(400) anonymised_key
    integer replacements "NOT NULL; default 0"
    varchar(120) approved_by
    timestamptz approved_at
    timestamptz published_at
    varchar(20) status "NOT NULL"
  }
  delivery ||--o{ deadline : "delivery_id"
```

## Dostęp, osoby, słowniki, audyt

Tabele: `case_assignment`, `delegation`, `judge`, `app_user_seen`, `audit_event`, `court`, `authority`, `letter_type`, `review_standard`

```mermaid
erDiagram
  case_assignment {
    bigint id PK
    bigint case_file_id FK,UK "NOT NULL"
    varchar(120) user_id UK "NOT NULL"
    varchar(20) role UK "NOT NULL"
    date assigned_on UK "NOT NULL"
    date released_on
  }
  delegation {
    bigint id PK
    varchar(120) user_id "NOT NULL"
    varchar(60) function_code "NOT NULL"
    date valid_from "NOT NULL"
    date valid_to "NOT NULL"
    varchar(200) basis_doc_ref
    timestamptz revoked_at
    varchar(120) created_by "NOT NULL"
    timestamptz created_at "NOT NULL; default now()"
    varchar(300) basis_doc_key
    varchar(64) basis_doc_sha256
  }
  judge {
    bigint id PK
    varchar(100) user_id UK "NOT NULL"
    varchar(200) display_name "NOT NULL"
    varchar(20) function_code "NOT NULL"
    date term_from "NOT NULL"
    date term_to
    boolean active "NOT NULL; default true"
    varchar(120) created_by "NOT NULL"
    timestamptz created_at "NOT NULL"
    varchar(120) updated_by
    timestamptz updated_at
  }
  app_user_seen {
    bigint id PK
    varchar(100) login UK "NOT NULL"
    varchar(200) display_name
    varchar(400) roles "NOT NULL"
    timestamptz first_seen_at "NOT NULL"
    timestamptz last_login_at "NOT NULL"
    integer login_count "NOT NULL; default 1"
  }
  audit_event {
    bigint id PK
    timestamptz occurred_at "NOT NULL"
    varchar(120) user_id "NOT NULL"
    varchar(60) user_role
    varchar(80) action "NOT NULL"
    varchar(200) object_ref
    varchar(20) case_ref
    varchar(45) remote_addr
    char(64) prev_hash "NOT NULL"
    char(64) hash "NOT NULL"
  }
  court {
    bigint id PK
    varchar(30) code UK "NOT NULL"
    varchar(300) name "NOT NULL"
    boolean active "NOT NULL; default true"
  }
  authority {
    bigint id PK
    varchar(30) code UK "NOT NULL"
    varchar(300) name "NOT NULL"
    boolean active "NOT NULL; default true"
  }
  letter_type {
    bigint id PK
    varchar(30) code UK "NOT NULL"
    varchar(300) name "NOT NULL"
    boolean active "NOT NULL; default true"
  }
  review_standard {
    bigint id PK
    varchar(30) code UK "NOT NULL"
    varchar(200) provision "NOT NULL"
    varchar(400) description
    boolean active "NOT NULL; default true"
  }
```

## Tabele

| Tabela | Kolumny | Klucze obce (wychodzące) | Indeksy |
| --- | ---: | --- | ---: |
| `access_request` | 9 | case_file_id → case_file | 1 |
| `app_user_seen` | 7 | — | 2 |
| `appeal` | 8 | case_file_id → case_file | 1 |
| `archive_category` | 5 | — | 1 |
| `archive_transfer` | 10 | case_file_id → case_file | 2 |
| `audit_event` | 10 | — | 3 |
| `authority` | 4 | — | 2 |
| `case_assignment` | 6 | case_file_id → case_file | 3 |
| `case_document` | 24 | case_file_id → case_file, template_version_id → letter_template | 2 |
| `case_file` | 8 | — | 2 |
| `case_file_version` | 17 | case_file_id → case_file | 3 |
| `checklist_entry` | 8 | case_file_id → case_file | 2 |
| `correction_decision` | 6 | version_id → case_file_version | 2 |
| `court` | 4 | — | 2 |
| `deadline` | 10 | case_file_id → case_file, delivery_id → delivery | 2 |
| `delegation` | 11 | — | 2 |
| `delivery` | 10 | addressee_party_id → party, document_id → case_document | 2 |
| `docket_counter` | 3 | — | 1 |
| `final_ruling` | 7 | case_file_id → case_file | 1 |
| `formal_requirement` | 12 | — | 3 |
| `general_letter` | 7 | letter_id → incoming_letter, reply_document_id → case_document | 1 |
| `highlight` | 7 | document_id → case_document | 2 |
| `holiday` | 4 | — | 1 |
| `incoming_counter` | 2 | — | 1 |
| `incoming_letter` | 14 | case_file_id → case_file | 2 |
| `incoming_scan` | 9 | letter_id → incoming_letter | 2 |
| `internal_note` | 6 | case_file_id → case_file | 2 |
| `judge` | 11 | — | 3 |
| `letter_template` | 9 | — | 2 |
| `letter_type` | 4 | — | 2 |
| `party` | 7 | case_file_id → case_file | 1 |
| `power_of_attorney` | 7 | party_id → party | 1 |
| `procedural_act` | 10 | case_file_id → case_file, document_id → case_document | 1 |
| `publication` | 8 | document_id → case_document | 1 |
| `review_standard` | 5 | — | 2 |
| `review_subject` | 5 | case_file_id → case_file | 1 |
