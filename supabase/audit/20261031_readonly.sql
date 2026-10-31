-- Metadata only. Run in SQL Editor; never reads academic rows or Auth tokens.
-- An administrator seeing these results is NOT a two-user RLS test.
BEGIN TRANSACTION READ ONLY;
SELECT current_user AS inspection_role, 'Structural inspection only; not an authenticated-user isolation test' AS scope;

SELECT c.relname AS table_name, c.relrowsecurity AS rls_enabled, c.relforcerowsecurity AS forced,
       pg_get_userbyid(c.relowner) AS table_owner
FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
WHERE n.nspname='public' AND c.relname IN
 ('noctua_class_bundles','noctua_class_versions','noctua_sync_heads','noctua_sync_receipts')
ORDER BY c.relname;

SELECT tablename,policyname,roles,cmd,permissive,qual,with_check
FROM pg_policies WHERE schemaname='public' AND tablename IN
 ('noctua_class_bundles','noctua_class_versions','noctua_sync_heads','noctua_sync_receipts')
ORDER BY tablename,policyname;

SELECT c.relname AS table_name, r.role_name,
 has_table_privilege(r.role_name,c.oid,'SELECT') AS can_select,
 has_table_privilege(r.role_name,c.oid,'INSERT') AS can_insert,
 has_table_privilege(r.role_name,c.oid,'UPDATE') AS can_update,
 has_table_privilege(r.role_name,c.oid,'DELETE') AS can_delete,
 has_table_privilege(r.role_name,c.oid,'TRUNCATE') AS can_truncate
FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
CROSS JOIN (VALUES ('anon'),('authenticated')) r(role_name)
WHERE n.nspname='public' AND c.relname IN
 ('noctua_class_bundles','noctua_class_versions','noctua_sync_heads','noctua_sync_receipts')
ORDER BY c.relname,r.role_name;

SELECT grantee,table_name,column_name,privilege_type FROM information_schema.column_privileges
WHERE table_schema='public' AND table_name IN
 ('noctua_class_bundles','noctua_class_versions','noctua_sync_heads','noctua_sync_receipts')
 AND grantee IN ('PUBLIC','anon','authenticated') AND privilege_type IN ('INSERT','UPDATE','REFERENCES')
ORDER BY table_name,column_name,grantee;

SELECT p.oid::regprocedure AS function_signature,pg_get_userbyid(p.proowner) AS function_owner,
 p.prosecdef AS security_definer,p.proconfig AS function_settings,
 has_function_privilege('anon',p.oid,'EXECUTE') AS anon_execute,
 has_function_privilege('authenticated',p.oid,'EXECUTE') AS authenticated_execute,
 CASE WHEN p.proname='noctua_sync_principal' AND to_regclass('auth.sessions') IS NOT NULL THEN
      has_table_privilege(pg_get_userbyid(p.proowner),'auth.sessions','SELECT') ELSE NULL END AS owner_can_read_sessions,
 EXISTS(SELECT 1 FROM aclexplode(COALESCE(p.proacl,acldefault('f',p.proowner))) acl
        WHERE acl.grantee=0 AND acl.privilege_type='EXECUTE') AS public_execute,
 md5(pg_get_functiondef(p.oid)) AS definition_md5,
 CASE WHEN p.proname='noctua_sync_principal' THEN position('auth.sessions' IN pg_get_functiondef(p.oid))>0
      WHEN p.proname IN ('noctua_push_class','noctua_pull_classes') THEN position('public.noctua_sync_principal()' IN pg_get_functiondef(p.oid))>0
      ELSE NULL END AS active_session_guard_present
FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
WHERE n.nspname='public' AND p.proname IN
 ('noctua_sync_principal','noctua_validate_class','noctua_push_class','noctua_pull_classes')
ORDER BY p.proname;

SELECT to_regclass('auth.sessions') IS NOT NULL AS sessions_table_present,
 (SELECT count(*) FROM information_schema.columns WHERE table_schema='auth' AND table_name='sessions'
  AND column_name IN ('id','user_id','not_after'))=3 AS required_session_columns_present,
 has_schema_privilege('anon','auth','CREATE') AS anon_can_create_auth_objects,
 has_schema_privilege('authenticated','auth','CREATE') AS authenticated_can_create_auth_objects;
ROLLBACK;
