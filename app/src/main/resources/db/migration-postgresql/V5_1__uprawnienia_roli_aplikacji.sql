-- V5.1 (tylko PostgreSQL, T-003/NFR-01): rola aplikacji ${app_role} ma SELECT/INSERT na wszystkim (domyślne uprawnienia roli migracji),
-- a UPDATE wyłącznie na tabelach operacyjnych. Wpisy repertorium (case_file, case_file_version, correction_decision)
-- i dziennik zdarzeń (audit_event) pozostają bez UPDATE/DELETE; w dzienniku wpływu wolno zmienić tylko kwalifikację (UC-02).
GRANT SELECT, INSERT ON ALL TABLES IN SCHEMA repertorium TO ${app_role};
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA repertorium TO ${app_role};
GRANT UPDATE ON docket_counter, incoming_counter TO ${app_role};                 -- liczniki (BR-01, SELECT … FOR UPDATE)
GRANT UPDATE (qualification, case_file_id) ON incoming_letter TO ${app_role};    -- kwalifikacja pisma – jedyna dozwolona zmiana
GRANT UPDATE ON party, power_of_attorney, final_ruling, review_subject TO ${app_role};
GRANT UPDATE ON case_document, procedural_act, delivery, deadline, appeal, access_request TO ${app_role};
GRANT UPDATE ON letter_template, delegation, holiday, court, authority, letter_type, review_standard TO ${app_role};
GRANT UPDATE ON case_assignment, internal_note, highlight, general_letter, publication, archive_category, archive_transfer TO ${app_role};
REVOKE UPDATE, DELETE, TRUNCATE ON case_file, case_file_version, correction_decision, audit_event FROM ${app_role};
REVOKE DELETE, TRUNCATE ON ALL TABLES IN SCHEMA repertorium FROM ${app_role};
