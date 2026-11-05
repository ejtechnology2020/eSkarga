-- V6.1 (tylko PostgreSQL): skany wpływu są tylko dopisywane (D5, NFR-01) – rola aplikacji bez UPDATE/DELETE.
GRANT SELECT, INSERT ON incoming_scan TO ${app_role};
GRANT USAGE, SELECT ON SEQUENCE incoming_scan_id_seq TO ${app_role};
REVOKE UPDATE, DELETE, TRUNCATE ON incoming_scan FROM ${app_role};
