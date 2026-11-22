#!/usr/bin/env python3
"""Actual disposable PostgREST HTTP; signed synthetic identities, NOT hosted Supabase Auth."""
import importlib.util
import base64
import hashlib
import hmac
import json
import os
from pathlib import Path
import subprocess
import sys
import time
import uuid

if (os.environ.get('NOCTUA_COMMERCIAL_DISPOSABLE') != 'YES' or
        os.environ.get('PGHOST') != '127.0.0.1' or os.environ.get('PGPORT') != '55431' or
        os.environ.get('PGDATABASE') != 'noctua_phase_e_test'):
    raise SystemExit('Disposable local 55431 database required; no hosted option')
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('commercial_http_transport',ROOT/'scripts/test-phase-b-http.py')
f=importlib.util.module_from_spec(spec);spec.loader.exec_module(f)
port=os.environ.get('NOCTUA_COMMERCIAL_HTTP_PORT','3001')
assert port in ('3000','3001')
f.BASE='http://127.0.0.1:'+port
A='44444444-4444-4444-8444-444444444444';B='55555555-5555-4555-8555-555555555555'
SA='44444444-4444-4444-8444-444444444441';SB='55555555-5555-4555-8555-555555555551'

def sql(text):
    r=subprocess.run([os.environ.get('PSQL_BIN','psql'),'-XAtq','-v','ON_ERROR_STOP=1'],input=text,text=True,capture_output=True)
    if r.returncode:raise AssertionError('Disposable SQL fixture setup failed')
    return r.stdout.strip()

if sys.argv[1:]==['--prepare']:
    sql(f"""
    DO $$ BEGIN IF NOT EXISTS(SELECT FROM pg_roles WHERE rolname='postgrest_fixture') THEN
      CREATE ROLE postgrest_fixture LOGIN NOINHERIT PASSWORD 'local-fixture-only'; END IF; END $$;
    GRANT anon,authenticated TO postgrest_fixture;
    CREATE OR REPLACE FUNCTION auth.uid() RETURNS uuid LANGUAGE sql STABLE AS
      'SELECT coalesce(nullif(current_setting(''request.jwt.claim.sub'',true),''''),auth.jwt()->>''sub'')::uuid';
    INSERT INTO auth.users VALUES('{A}',now(),false),('{B}',now(),false);
    INSERT INTO auth.sessions VALUES('{SA}','{A}',NULL),('{SB}','{B}',NULL);
    INSERT INTO public.noctua_commercial_accounts(owner_id,enabled) VALUES('{A}',true),('{B}',false);
    INSERT INTO auth.users VALUES('88888888-8888-4888-8888-888888888888',now(),false),('99999999-9999-4999-8999-999999999999',now(),false);
    INSERT INTO auth.sessions VALUES('81818181-8181-4181-8181-818181818181','88888888-8888-4888-8888-888888888888',NULL),
      ('82828282-8282-4282-8282-828282828282','88888888-8888-4888-8888-888888888888',NULL),
      ('93939393-9393-4393-8393-939393939393','99999999-9999-4999-8999-999999999999',NULL);
    INSERT INTO public.noctua_commercial_accounts(owner_id,enabled) VALUES('88888888-8888-4888-8888-888888888888',true);
    UPDATE public.noctua_commercial_rollout SET enabled=true;
    """)
    print('Disposable HTTP fixture prepared; no hosted project, users or credentials.')
    raise SystemExit(0)
if sys.argv[1:]:raise SystemExit('Unknown option')

checks=0
def call(path,body=None,token=None,method='POST',expected=200):
    global checks
    value=f.request(path,body,token,method,expected);checks+=1;return value

a,b=f.token(A,SA),f.token(B,SB)
call('/rpc/noctua_ensure_academic_e',expected=401)
call('/rpc/noctua_ensure_academic_e',token='invalid-token',expected=401)
call('/rpc/noctua_ensure_academic_e',token=f.token(A,str(uuid.uuid4())),expected=403)
assert call('/rpc/noctua_ensure_academic_e',token=b)['reason']=='INELIGIBLE'
first=call('/rpc/noctua_ensure_academic_e',token=a)
assert first==dict(eligible=True,initialized=True,reason='READY',academicSequence=0)
assert call('/rpc/noctua_ensure_academic_e',token=a)==first
assert call('/rpc/noctua_academic_status_c',token=a)['testAllowed'] is False
for table in ('noctua_commercial_rollout','noctua_commercial_accounts'):
    call('/'+table,token=a,method='GET',expected=403)
    call('/'+table,token=a,method='DELETE',expected=403)
call('/rpc/noctua_e_authorized',token=a,expected=403)
call('/rpc/noctua_e_principal',token=a,expected=403)

snapshot=json.loads((ROOT/'app/src/androidTest/assets/phase-d.json').read_text())
names=dict(classes='classes',terms='terms',students='students',lessons='lessons',marks='marks',activities='activities',grades='grades',recompositions='recompositions',relocations='relocations',drafts='import_drafts',exportRows='export_rows',classArchives='class_archives',lessonRecords='lesson_records',histories='preserved_histories',timetables='weekly_timetables')
definitions=json.loads((ROOT/'supabase/contracts/academic-v2.json').read_text())['entities']
rows=[dict(kind=kind,key={k:p[k] for k in definitions[kind]['key']},base=0,payload=p)
      for name,kind in names.items() for p in snapshot[name]]
operation=str(uuid.uuid4());parts=[rows[n:n+128] for n in range(0,len(rows),128)]
call('/rpc/noctua_begin_academic_e',dict(p_operation=operation,p_total=len(rows),p_parts=len(parts)),a)
for n,part in enumerate(parts):call('/rpc/noctua_stage_academic_e',dict(p_operation=operation,p_part=n,p_changes=part),a)
accepted=call('/rpc/noctua_commit_academic_e',dict(p_operation=operation),a);assert accepted['accepted']
assert call('/rpc/noctua_commit_academic_e',dict(p_operation=operation),a)==accepted
received=[];offset=0
while True:
    page=call('/rpc/noctua_pull_academic_e',dict(p_after=0,p_offset=offset),a)[0]
    received+=page['records'];offset+=len(page['records'])
    if offset==page['total']:break
assert len(received)==len(rows) and len({r['kind'] for r in received})==15
assert {(r['kind'],json.dumps(r['key'],sort_keys=True)) for r in received}=={(r['kind'],json.dumps(r['key'],sort_keys=True)) for r in rows}

# Phase 3.3: an unentitled ordinary owner stays unentitled even knowing A's keys.
# All failures are real local PostgREST/SQL permission checks, not Android guards.
before=sql("SELECT md5(jsonb_agg(to_jsonb(r) ORDER BY owner_id,kind,key)::text) FROM public.noctua_academic_records r;")
assert call('/rpc/noctua_commercial_status',token=b)['reason']=='INELIGIBLE'
call('/rpc/noctua_pull_academic_e',dict(p_after=0,p_offset=0),b,expected=403)
call('/rpc/noctua_begin_academic_e',dict(p_operation=str(uuid.uuid4()),p_total=1,p_parts=1),b,expected=403)
call('/rpc/noctua_resolve_academic_e',dict(p_resolution=str(uuid.uuid4()),p_request={},p_cancel=False),b,expected=403)
call('/noctua_academic_records',dict(owner_id=A,kind='grades',key=rows[0]['key'],revision=1),b,expected=403)
assert call('/noctua_academic_records?owner_id=eq.'+A,token=b,method='GET')==[]
def signed_expired():
    encode=lambda value:base64.urlsafe_b64encode(json.dumps(value).encode()).decode().rstrip('=')
    raw=encode(dict(alg='HS256',typ='JWT'))+'.'+encode(dict(sub=A,role='authenticated',session_id=SA,exp=int(time.time())-3600))
    return raw+'.'+base64.urlsafe_b64encode(hmac.new(f.SECRET.encode(),raw.encode(),hashlib.sha256).digest()).decode().rstrip('=')
call('/rpc/noctua_pull_academic_e',dict(p_after=0,p_offset=0),signed_expired(),expected=401)
call('/rpc/noctua_pull_academic_e',dict(p_after=0,p_offset=0),f.token(A,SB),expected=403)
assert before==sql("SELECT md5(jsonb_agg(to_jsonb(r) ORDER BY owner_id,kind,key)::text) FROM public.noctua_academic_records r;")

sql(f"UPDATE public.noctua_commercial_accounts SET enabled=true WHERE owner_id='{B}';")
assert call('/rpc/noctua_ensure_academic_e',token=b)['initialized']
assert call('/rpc/noctua_pull_academic_e',dict(p_after=0,p_offset=0),b)==[]
for table in ('noctua_academic_records','noctua_academic_versions','noctua_academic_receipts','noctua_e_transfers','noctua_e_parts','noctua_e_decisions'):
    assert call('/'+table+'?owner_id=eq.'+A,token=b,method='GET')==[]
    call('/'+table,token=b,method='DELETE',expected=403)
sql(f"DELETE FROM auth.sessions WHERE id='{SA}';")
call('/rpc/noctua_pull_academic_e',dict(p_after=0,p_offset=0),a,expected=403)
result=dict(checks_passed=checks,records=len(received),types=15,transport='local PostgREST',
    unentitled_cross_account_checks=True,expired_invalid_revoked_sessions_checked=True,
    hosted_auth=False,production_access=False,H13_or_PT409_applied=False)
print(json.dumps(result,sort_keys=True))
