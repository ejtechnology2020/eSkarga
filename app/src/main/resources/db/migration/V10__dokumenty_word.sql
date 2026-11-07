-- V10: dokumenty Word w aktach (ADR 0026) – oryginał DOC/DOCX zostaje w repozytorium, kartą akt jest PDF/A z konwersji.
-- source_content_type: typ oryginału (NULL = plik był PDF); source_storage_key: klucz oryginału po konwersji (NULL = jeszcze nieskonwertowany,
-- wtedy storage_key wskazuje oryginał); source_file_name: nazwa pliku z rejestracji wpływu.
ALTER TABLE case_document ADD COLUMN source_content_type VARCHAR(120);
ALTER TABLE case_document ADD COLUMN source_storage_key  VARCHAR(400);
ALTER TABLE case_document ADD COLUMN source_file_name    VARCHAR(300);
