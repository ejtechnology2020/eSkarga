-- V4: stan korekty wpisu (UC-22: wniosek → akceptacja/odrzucenie bez usuwania wersji), zgodność PDF/A i OCR dokumentu (T-031, T-052).
ALTER TABLE case_file_version ADD COLUMN correction_status VARCHAR(20);   -- NULL = wersja systemowa, OCZEKUJE, ZATWIERDZONA, ODRZUCONA
ALTER TABLE case_document ADD COLUMN pdfa_compliant BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE case_document ADD COLUMN ocr_done BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE case_document ADD COLUMN signature_report VARCHAR(2000);
