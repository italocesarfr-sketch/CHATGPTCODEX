-- Catalog-only inspection. No academic rows, ownership changes, or deployment.
-- Does NOT replace two authenticated ordinary-account API/RLS tests.
SELECT c.relname,c.relrowsecurity,
 has_table_privilege('anon',c.oid,'SELECT') AS anonymous_read,
 has_table_privilege('authenticated',c.oid,'SELECT') AS authenticated_read,
 has_table_privilege('authenticated',c.oid,'INSERT') OR has_table_privilege('authenticated',c.oid,'UPDATE') OR has_table_privilege('authenticated',c.oid,'DELETE') AS direct_write
FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
WHERE n.nspname='public' AND c.relname IN('noctua_e_transfers','noctua_e_parts','noctua_e_decisions') ORDER BY c.relname;
SELECT tablename,policyname,roles,cmd,qual FROM pg_policies WHERE schemaname='public' AND tablename IN('noctua_e_transfers','noctua_e_parts','noctua_e_decisions') ORDER BY tablename,policyname;
SELECT p.oid::regprocedure AS signature,p.prosecdef,p.proconfig,
 has_function_privilege('anon',p.oid,'EXECUTE') AS anonymous_execute,
 has_function_privilege('authenticated',p.oid,'EXECUTE') AS authenticated_execute
FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
WHERE n.nspname='public' AND p.proname IN('noctua_e_principal','noctua_e_write','noctua_e_guarded_write','noctua_enable_academic_e','noctua_begin_academic_e','noctua_stage_academic_e','noctua_commit_academic_e','noctua_pull_academic_e','noctua_resolve_academic_e','noctua_decision_academic_e') ORDER BY p.oid::regprocedure::text;
SELECT conname,pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid='public.noctua_sync_protocols'::regclass AND conname='noctua_sync_protocols_academic_scope_check';
