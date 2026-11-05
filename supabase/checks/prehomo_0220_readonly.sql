-- Safe before/after deployment; catalog/settings/sizes only. No academic rows,
-- UUIDs, e-mails, tokens, Auth contents, RPC execution or configuration writes.
BEGIN TRANSACTION READ ONLY;
SELECT current_database() AS database_name,current_setting('server_version') AS pg_version,
       pg_database_size(current_database()) AS database_bytes;
SELECT name,setting,unit FROM pg_settings
 WHERE name IN('statement_timeout','lock_timeout','work_mem','max_connections') ORDER BY name;
-- SHOW in the SQL Editor is the admin session, NOT the authenticated API budget.
SELECT r.rolname,v AS role_setting FROM pg_roles r
 CROSS JOIN LATERAL unnest(r.rolconfig) v
 WHERE r.rolname IN('anon','authenticated','authenticator')
 AND (v LIKE 'statement_timeout=%' OR v LIKE 'lock_timeout=%') ORDER BY r.rolname,v;
SELECT coalesce(r.rolname,'ALL') AS role_name,coalesce(d.datname,'ALL') AS database_name,v AS setting
 FROM pg_db_role_setting s LEFT JOIN pg_roles r ON r.oid=s.setrole
 LEFT JOIN pg_database d ON d.oid=s.setdatabase
 CROSS JOIN LATERAL unnest(s.setconfig) v
 WHERE v LIKE 'statement_timeout=%' OR v LIKE 'lock_timeout=%';
SELECT to_regclass('auth.users') IS NOT NULL AS auth_users_present,
       to_regclass('auth.sessions') IS NOT NULL AS auth_sessions_present,
       to_regclass('supabase_migrations.schema_migrations') IS NOT NULL AS migration_history_present;
SELECT table_name,column_name,data_type FROM information_schema.columns
 WHERE table_schema='auth' AND ((table_name='sessions' AND column_name IN('id','user_id','not_after')) OR
       (table_name='users' AND column_name IN('id','email_confirmed_at','is_anonymous'))) ORDER BY table_name,column_name;
WITH wanted(name) AS (VALUES
 ('noctua_sync_heads'),('noctua_class_bundles'),('noctua_class_versions'),('noctua_sync_receipts'),
 ('noctua_academic_heads'),('noctua_academic_batches'),('noctua_academic_records'),('noctua_academic_versions'),
 ('noctua_academic_receipts'),('noctua_academic_links'),('noctua_academic_uniques'),
 ('noctua_academic_test_accounts'),('noctua_sync_protocols'),
 ('noctua_e_transfers'),('noctua_e_parts'),('noctua_e_decisions'))
SELECT w.name,c.oid IS NOT NULL AS installed,c.relrowsecurity AS rls,
 CASE WHEN c.oid IS NOT NULL THEN has_table_privilege('anon',c.oid,'SELECT') END AS anonymous_read,
 CASE WHEN c.oid IS NOT NULL THEN has_table_privilege('authenticated',c.oid,'SELECT') END AS authenticated_read,
 CASE WHEN c.oid IS NOT NULL THEN has_table_privilege('authenticated',c.oid,'INSERT') OR
  has_table_privilege('authenticated',c.oid,'UPDATE') OR has_table_privilege('authenticated',c.oid,'DELETE') END AS direct_write,
 CASE WHEN c.oid IS NOT NULL THEN pg_total_relation_size(c.oid) END AS bytes_with_indexes
 FROM wanted w LEFT JOIN pg_class c ON c.oid=to_regclass('public.'||w.name) ORDER BY w.name;
SELECT tablename,policyname,roles,cmd,qual,with_check FROM pg_policies
 WHERE schemaname='public' AND tablename LIKE 'noctua_%' ORDER BY tablename,policyname;
SELECT p.oid::regprocedure AS signature,p.prosecdef AS security_definer,p.proconfig,
 pg_get_userbyid(p.proowner) AS function_owner,
 has_function_privilege('anon',p.oid,'EXECUTE') AS anonymous_execute,
 has_function_privilege('authenticated',p.oid,'EXECUTE') AS authenticated_execute,
 md5(pg_get_functiondef(p.oid)) AS definition_md5
 FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
 WHERE n.nspname='public' AND p.proname LIKE 'noctua_%' ORDER BY p.oid::regprocedure::text;
SELECT position('auth.sessions' in pg_get_functiondef(p.oid))>0 AS consults_sessions,
 position('email_confirmed_at' in pg_get_functiondef(p.oid))>0 AS checks_email_confirmation,
 CASE WHEN to_regclass('auth.sessions') IS NOT NULL THEN
  has_table_privilege(pg_get_userbyid(p.proowner),to_regclass('auth.sessions'),'SELECT') END AS owner_can_read_sessions
 FROM pg_proc p WHERE p.oid=to_regprocedure('public.noctua_sync_principal()');
SELECT conname,pg_get_constraintdef(oid) AS definition FROM pg_constraint
 WHERE conrelid=to_regclass('public.noctua_sync_protocols') AND conname='noctua_sync_protocols_academic_scope_check';
ROLLBACK;
