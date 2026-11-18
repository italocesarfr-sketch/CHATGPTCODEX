-- PREPARED ONLY: administrator must confirm noctua-homologacao / sscexzxtqdcsqdlcytum first.
-- Catalog-only; no RPC invocation, Auth rows, academic contents, credentials or DDL.
BEGIN TRANSACTION READ ONLY;
WITH wanted(name) AS (VALUES
 ('noctua_sync_heads'),('noctua_class_bundles'),('noctua_class_versions'),('noctua_sync_receipts'),
 ('noctua_academic_heads'),('noctua_academic_batches'),('noctua_academic_records'),('noctua_academic_versions'),
 ('noctua_academic_receipts'),('noctua_academic_links'),('noctua_academic_uniques'),
 ('noctua_academic_test_accounts'),('noctua_sync_protocols'),
 ('noctua_e_transfers'),('noctua_e_parts'),('noctua_e_decisions'))
SELECT w.name,c.oid IS NOT NULL AS installed,c.relrowsecurity AS rls,
 CASE WHEN c.oid IS NOT NULL THEN has_table_privilege('anon',c.oid,'SELECT') END AS anonymous_read,
 CASE WHEN c.oid IS NOT NULL THEN has_table_privilege('authenticated',c.oid,'INSERT') OR
  has_table_privilege('authenticated',c.oid,'UPDATE') OR has_table_privilege('authenticated',c.oid,'DELETE') END AS direct_write
FROM wanted w LEFT JOIN pg_class c ON c.oid=to_regclass('public.'||w.name) ORDER BY w.name;

SELECT c.relname,c.relkind FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
WHERE n.nspname='public' AND c.relname LIKE 'noctua_commercial_%' ORDER BY c.relname;
-- Expected: zero rows above and zero rows below. Any object means stop, not reapply.
SELECT p.oid::regprocedure AS unexpected_commercial_function FROM pg_proc p
JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public'
 AND (p.proname LIKE 'noctua_commercial_%' OR p.proname IN('noctua_ensure_academic_e','noctua_e_authorized'));

WITH signatures(name,client_execute) AS (VALUES
 ('public.noctua_sync_principal()',true),('public.noctua_academic_status_c()',true),
 ('public.noctua_begin_academic_e(uuid,integer,integer)',true),
 ('public.noctua_stage_academic_e(uuid,integer,jsonb)',true),('public.noctua_commit_academic_e(uuid)',true),
 ('public.noctua_pull_academic_e(bigint,integer)',true),
 ('public.noctua_resolve_academic_e(uuid,jsonb,boolean)',true),('public.noctua_decision_academic_e(uuid)',true),
 ('public.noctua_e_principal()',false),('public.noctua_e_guarded_write(uuid,jsonb)',false))
SELECT s.name,p.oid IS NOT NULL AS installed,p.prosecdef AS security_definer,p.proconfig,
 s.client_execute AS expected_authenticated_execute,
 CASE WHEN p.oid IS NOT NULL THEN has_function_privilege('anon',p.oid,'EXECUTE') END AS anonymous_execute,
 CASE WHEN p.oid IS NOT NULL THEN has_function_privilege('authenticated',p.oid,'EXECUTE') END AS authenticated_execute,
 md5(pg_get_functiondef(p.oid)) AS definition_md5
FROM signatures s LEFT JOIN pg_proc p ON p.oid=to_regprocedure(s.name) ORDER BY s.name;

SELECT position('auth.sessions' in pg_get_functiondef(p.oid))>0 AS checks_sessions,
 position('email_confirmed_at' in pg_get_functiondef(p.oid))>0 AS checks_confirmation,
 has_table_privilege(pg_get_userbyid(p.proowner),to_regclass('auth.sessions'),'SELECT') AS owner_can_read_sessions
FROM pg_proc p WHERE p.oid=to_regprocedure('public.noctua_sync_principal()');

WITH definition AS (SELECT pg_get_functiondef(to_regprocedure('public.noctua_e_guarded_write(uuid,jsonb)')) AS body),
 marker AS (SELECT 'NOT EXISTS(SELECT FROM public.noctua_academic_test_accounts WHERE owner_id=principal AND allowed)'::text AS needle)
SELECT (length(body)-length(replace(body,needle,'')))/length(needle)=1 AS reviewed_writer_authorization_marker_once
FROM definition CROSS JOIN marker;

-- Exact reviewed bodies (pg_proc.prosrc, UTF-8/LF). Formatting drift also requires
-- review; a mismatch is not permission to patch or reapply older migrations.
WITH reviewed(signature,body_md5) AS (VALUES
 ('public.noctua_sync_principal()','23db7de198f8cd91291425ed614eb118'),
 ('public.noctua_e_principal()','63ea2245f85593f543ec7260d6af2a4f'),
 ('public.noctua_e_guarded_write(uuid,jsonb)','e08198000dfe82c01843d57dab5075ae'))
SELECT r.signature,p.oid IS NOT NULL AS installed,
 coalesce(md5(p.prosrc)=r.body_md5,false) AS reviewed_body_matches
FROM reviewed r LEFT JOIN pg_proc p ON p.oid=to_regprocedure(r.signature)
ORDER BY r.signature;

SELECT l.lanname='sql' AS original_registry_language_without_h13 FROM pg_proc p
JOIN pg_language l ON l.oid=p.prolang WHERE p.oid=to_regprocedure('public.noctua_academic_definition(text)');
SELECT p.oid::regprocedure AS unexpected_pt409_candidate FROM pg_proc p
WHERE p.oid IN (to_regprocedure('public.noctua_enable_academic_c(bigint)'),
 to_regprocedure('public.noctua_enable_academic_d(bigint)'),to_regprocedure('public.noctua_enable_academic_e(bigint)'))
AND position('PT409' in pg_get_functiondef(p.oid))>0;

SELECT tablename,policyname,roles,cmd,qual FROM pg_policies WHERE schemaname='public'
 AND tablename LIKE 'noctua_%' ORDER BY tablename,policyname;
SELECT current_setting('server_version') AS server_version,current_setting('statement_timeout') AS editor_statement_timeout,
 current_setting('lock_timeout') AS editor_lock_timeout;
ROLLBACK;
