-- V11.1 (tylko PostgreSQL): słownik wymogów formalnych edytuje administrator (S-24) – rola aplikacji ma UPDATE, bez DELETE (pozycje wycofywane).
GRANT SELECT, INSERT, UPDATE ON formal_requirement TO ${app_role};
GRANT USAGE, SELECT ON SEQUENCE formal_requirement_id_seq TO ${app_role};
REVOKE DELETE, TRUNCATE ON formal_requirement FROM ${app_role};
