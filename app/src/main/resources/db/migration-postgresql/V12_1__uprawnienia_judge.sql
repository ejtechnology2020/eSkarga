-- V12.1 (tylko PostgreSQL): słownik sędziów edytuje administrator (S-24) – rola aplikacji ma UPDATE, bez DELETE (wpisy wycofywane).
GRANT SELECT, INSERT, UPDATE ON judge TO ${app_role};
GRANT USAGE, SELECT ON SEQUENCE judge_id_seq TO ${app_role};
REVOKE DELETE, TRUNCATE ON judge FROM ${app_role};
