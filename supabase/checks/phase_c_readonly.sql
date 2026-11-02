-- AFTER approved A -> B -> C deployment. Inventory only; not proof of two-user RLS.
-- Read-only, no users, grants, records, credentials or hosted configuration are changed.
BEGIN TRANSACTION READ ONLY;
SELECT c.relname AS table_name,c.relrowsecurity AS rls_enabled,
 has_table_privilege('authenticated',c.oid,'INSERT') AS client_insert,
 has_table_privilege('authenticated',c.oid,'UPDATE') AS client_update,
 has_table_privilege('authenticated',c.oid,'DELETE') AS client_delete
FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace
WHERE n.nspname='public' AND c.relkind='r' AND c.relname LIKE 'noctua_%'
ORDER BY c.relname;

SELECT tablename,policyname,cmd,roles,qual,with_check
FROM pg_catalog.pg_policies WHERE schemaname='public' AND tablename LIKE 'noctua_%'
ORDER BY tablename,policyname;

WITH signatures(signature,client_should_execute) AS (VALUES
 ('public.noctua_academic_status_c()',true),
 ('public.noctua_enable_academic_c(bigint)',true),
 ('public.noctua_push_academic_c(uuid,jsonb)',true),
 ('public.noctua_pull_academic_c(bigint)',true),
 ('public.noctua_v1_push_class(uuid,text,bigint,boolean,jsonb)',false),
 ('public.noctua_v1_pull_classes(bigint)',false),
 ('public.noctua_v2_push_unfenced(uuid,jsonb)',false),
 ('public.noctua_v2_pull_unfenced(bigint)',false)
)
SELECT s.signature,p.oid IS NOT NULL AS installed,p.prosecdef AS security_definer,p.proconfig,
 CASE WHEN p.oid IS NOT NULL THEN has_function_privilege('anon',p.oid,'EXECUTE') END AS anonymous_execute,
 CASE WHEN p.oid IS NOT NULL THEN has_function_privilege('authenticated',p.oid,'EXECUTE') END AS client_execute,
 s.client_should_execute
FROM signatures s LEFT JOIN pg_catalog.pg_proc p ON p.oid=pg_catalog.to_regprocedure(s.signature);

-- Aggregates only: do not expose account UUIDs or academic content in the result.
SELECT protocol,academic_scope,count(*) AS test_profiles
FROM public.noctua_sync_protocols GROUP BY protocol,academic_scope ORDER BY protocol,academic_scope;
ROLLBACK;
