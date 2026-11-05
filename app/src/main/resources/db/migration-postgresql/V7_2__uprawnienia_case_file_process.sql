-- V7.2 (tylko PostgreSQL, D1/NFR-01): identyfikator instancji procesu jest znany dopiero po starcie procesu w tej samej transakcji,
-- po INSERT wpisu – jedyna dozwolona zmiana kolumny wpisu repertorium (encja z @DynamicUpdate aktualizuje tylko tę kolumnę).
GRANT UPDATE (process_instance_id) ON case_file TO ${app_role};
