-- PREPARED ONLY. Confirm noctua-homologacao / sscexzxtqdcsqdlcytum first.
-- Catalog/configuration only; never calls Auth/academic RPC or selects user data.
BEGIN TRANSACTION READ ONLY;
SELECT current_setting('server_version') AS server_version,current_user AS inspection_role,
 current_setting('statement_timeout') AS editor_statement_timeout;
SELECT c.relname,c.relrowsecurity,
 has_table_privilege('anon',c.oid,'SELECT') AS anonymous_read,
 has_table_privilege('authenticated',c.oid,'SELECT') AS authenticated_read,
 has_table_privilege('authenticated',c.oid,'INSERT') OR has_table_privilege('authenticated',c.oid,'UPDATE') OR
 has_table_privilege('authenticated',c.oid,'DELETE') AS direct_write
FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public'
 AND c.relname IN('noctua_commercial_rollout','noctua_commercial_accounts','noctua_commercial_roster','noctua_commercial_claims') ORDER BY c.relname;
SELECT p.oid::regprocedure AS signature,p.prosecdef,p.proconfig,
 has_function_privilege('anon',p.oid,'EXECUTE') AS anonymous_execute,
 has_function_privilege('authenticated',p.oid,'EXECUTE') AS authenticated_execute,
 pg_get_userbyid(p.proowner)=current_user AS owned_by_inspection_role,
 md5(p.prosrc) AS body_md5
FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public'
 AND p.proname IN('noctua_ensure_academic_e','noctua_provision_commercial','noctua_commercial_claim_immutable','noctua_sync_principal') ORDER BY p.proname;
SELECT tablename,policyname,roles,cmd FROM pg_policies WHERE schemaname='public'
 AND tablename IN('noctua_commercial_roster','noctua_commercial_claims') ORDER BY tablename,policyname;
SELECT l.lanname='sql' AS original_registry_without_h13 FROM pg_proc p JOIN pg_language l ON l.oid=p.prolang
 WHERE p.oid=to_regprocedure('public.noctua_academic_definition(text)');
SELECT count(*) AS unexpected_pt409_functions FROM pg_proc WHERE oid IN(
 to_regprocedure('public.noctua_enable_academic_c(bigint)'),to_regprocedure('public.noctua_enable_academic_d(bigint)'),to_regprocedure('public.noctua_enable_academic_e(bigint)')) AND position('PT409' in prosrc)>0;
ROLLBACK;
