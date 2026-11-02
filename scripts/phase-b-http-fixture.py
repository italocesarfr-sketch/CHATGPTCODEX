#!/usr/bin/env python3
"""LOCAL ONLY. Prepare disposable PostgREST auth fixtures; no hosted credentials/users."""
import os,importlib.util,json,hmac,hashlib,base64,time,urllib.request,urllib.error
from pathlib import Path
if os.environ.get('PGDATABASE')!='noctua_phase_b_test' or os.environ.get('PGHOST') not in ('127.0.0.1','localhost','/workspace/pg-local'):
    raise SystemExit('Disposable LOCAL database only')
spec=importlib.util.spec_from_file_location('phase_b_fixture',Path(__file__).with_name('test-phase-b-postgres.py'))
f=importlib.util.module_from_spec(spec);spec.loader.exec_module(f);f.reset()
f.execute((Path(__file__).resolve().parents[1]/"supabase/migrations/202611020002_phase_c_scope.sql").read_text())
SECRET='noctua-phase-b-disposable-jwt-secret-2026-only'
SID_A='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';SID_A2='bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';SID_B='cccccccc-cccc-4ccc-8ccc-cccccccccccc'
f.execute(f"INSERT INTO auth.sessions VALUES('{SID_A}','{f.A}',NULL),('{SID_A2}','{f.A}',NULL),('{SID_B}','{f.B}',NULL);")
f.execute("INSERT INTO auth.users VALUES('44444444-4444-4444-8444-444444444444',now(),false),('55555555-5555-4555-8555-555555555555',now(),false);")
f.execute("INSERT INTO auth.sessions VALUES('dddddddd-dddd-4ddd-8ddd-dddddddddddd','44444444-4444-4444-8444-444444444444',NULL),('eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee','44444444-4444-4444-8444-444444444444',NULL),('ffffffff-ffff-4fff-8fff-ffffffffffff','55555555-5555-4555-8555-555555555555',NULL);")
f.execute("INSERT INTO public.noctua_academic_test_accounts VALUES('44444444-4444-4444-8444-444444444444',true),('55555555-5555-4555-8555-555555555555',true);")
f.execute("""
DO $$ BEGIN IF NOT EXISTS(SELECT FROM pg_roles WHERE rolname='postgrest_fixture') THEN
 CREATE ROLE postgrest_fixture LOGIN NOINHERIT PASSWORD 'local-fixture-only'; END IF; END $$;
GRANT anon,authenticated TO postgrest_fixture;
CREATE OR REPLACE FUNCTION auth.uid() RETURNS uuid LANGUAGE sql STABLE AS
'SELECT coalesce(nullif(current_setting(''request.jwt.claim.sub'',true),''''),auth.jwt()->>''sub'')::uuid';
""")
print('Disposable PostgREST fixture initialized; B/C fictitious owners and active fixture sessions. Not Supabase Auth.')
