-- Akt podstawowy upoważnienia (S-23, FR-36) jako dokument w repozytorium S3: klucz obiektu i skrót SHA-256 (D5);
-- basis_doc_ref pozostaje nazwą/opisem aktu. Kolumny opcjonalne – wcześniejsze wpisy mają tylko opis.
ALTER TABLE delegation ADD COLUMN basis_doc_key VARCHAR(300);
ALTER TABLE delegation ADD COLUMN basis_doc_sha256 VARCHAR(64);
