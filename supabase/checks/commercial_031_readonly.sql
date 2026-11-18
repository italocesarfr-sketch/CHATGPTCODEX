-- Future authorized administrative inspection only. No RPC execution or academic/Auth rows.
BEGIN TRANSACTION READ ONLY;
SELECT c.relname,c.relrowsecurity,
 has_table_privilege('anon',c.oid,'SELECT') AS anonymous_read,
 has_table_privilege('authenticated',c.oid,'SELECT') AS authenticated_read,
 has_table_privilege('authenticated',c.oid,'INSERT') OR
 has_table_privilege('authenticated',c.oid,'UPDATE') OR
 has_table_privilege('authenticated',c.oid,'DELETE') AS authenticated_write
FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
WHERE n.nspname='public' AND c.relname IN('noctua_commercial_accounts','noctua_commercial_rollout')
ORDER BY c.relname;
SELECT p.oid::regprocedure AS signature,p.prosecdef,p.proconfig,
 has_function_privilege('anon',p.oid,'EXECUTE') AS anonymous_execute,
 has_function_privilege('authenticated',p.oid,'EXECUTE') AS authenticated_execute,
 md5(pg_get_functiondef(p.oid)) AS definition_md5
FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
WHERE n.nspname='public' AND p.proname IN('noctua_commercial_status','noctua_ensure_academic_e',
 'noctua_e_authorized','noctua_e_principal','noctua_e_guarded_write')
ORDER BY p.proname;
SELECT tablename,policyname,roles,cmd FROM pg_policies WHERE schemaname='public'
 AND tablename IN('noctua_commercial_accounts','noctua_commercial_rollout') ORDER BY tablename,policyname;
ROLLBACK;
