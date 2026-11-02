-- READ ONLY: catalogue and aggregate permissions only. No academic contents, owner IDs or tokens.
SELECT count(*) AS tabelas_academicas_protegidas FROM pg_catalog.pg_class c
 JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace
 WHERE n.nspname='public' AND c.relname LIKE 'noctua_%' AND c.relkind='r' AND c.relrowsecurity;
SELECT c.relname AS tabela,
 has_table_privilege('authenticated',c.oid,'INSERT,UPDATE,DELETE') AS escrita_direta_authenticated,
 has_table_privilege('anon',c.oid,'SELECT,INSERT,UPDATE,DELETE') AS acesso_anon
 FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace
 WHERE n.nspname='public' AND c.relname LIKE 'noctua_%' AND c.relkind='r' ORDER BY c.relname;
SELECT p.proname AS funcao,p.prosecdef AS security_definer,p.proconfig,
 has_function_privilege('authenticated',p.oid,'EXECUTE') AS cliente,
 has_function_privilege('anon',p.oid,'EXECUTE') AS anonimo
 FROM pg_catalog.pg_proc p JOIN pg_catalog.pg_namespace n ON n.oid=p.pronamespace
 WHERE n.nspname='public' AND (p.proname LIKE 'noctua_d_%' OR p.proname IN
 ('noctua_enable_academic_d','noctua_push_academic_d','noctua_pull_academic_d','noctua_v2_push_unfenced','noctua_v2_pull_unfenced'))
 ORDER BY p.proname;
SELECT conname,pg_catalog.pg_get_constraintdef(oid) AS regra FROM pg_catalog.pg_constraint
 WHERE conrelid='public.noctua_sync_protocols'::regclass AND conname='noctua_sync_protocols_academic_scope_check';
-- Catalogue checks do not prove hosted isolation. Use two authorized ordinary sessions separately.
