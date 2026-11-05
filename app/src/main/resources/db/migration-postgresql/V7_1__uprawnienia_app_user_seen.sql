-- V7.1 (tylko PostgreSQL): rejestr osób znanych aplikacji jest aktualizowany przy logowaniu – rola aplikacji ma UPDATE, bez DELETE.
GRANT SELECT, INSERT, UPDATE ON app_user_seen TO ${app_role};
GRANT USAGE, SELECT ON SEQUENCE app_user_seen_id_seq TO ${app_role};
REVOKE DELETE, TRUNCATE ON app_user_seen FROM ${app_role};
