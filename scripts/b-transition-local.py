#!/usr/bin/env python3
"""LOCAL ONLY: verify existing monotonic C/D/E guards, seed B and verify no data writes."""
import os,importlib.util,json,hashlib,runpy,subprocess
from pathlib import Path
assert os.environ.get('GITHUB_ACTIONS')=='true'and os.environ.get('CI')=='true'
assert os.environ.get('PGHOST')=='127.0.0.1'and os.environ.get('PGDATABASE')=='noctua_phase_b_test'
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'build/b-transition';OUT.mkdir(parents=True,exist_ok=True)
spec=importlib.util.spec_from_file_location('http_fixture',ROOT/'scripts/test-phase-b-http.py');f=importlib.util.module_from_spec(spec);spec.loader.exec_module(f)
A='88888888-8888-4888-8888-888888888888';B='99999999-9999-4999-8999-999999999999'
TOKEN=f.token(B,'93939393-9393-4393-8393-939393939393')
def rpc(name,body=None,token=TOKEN,expected=200):return f.request('/rpc/'+name,body,token,expected=expected)
def sql(statement):
 r=subprocess.run(['psql','-X','-A','-t','-v','ON_ERROR_STOP=1','-c',statement],capture_output=True,text=True,check=True);return r.stdout.strip()
def digest(owner,exclude_scope=False):
 names=sql("SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename LIKE 'noctua_%' ORDER BY tablename").splitlines();parts=[]
 for name in names:
  assert name.replace('_','').isalnum()
  projection="to_jsonb(r)-'academic_scope'"if exclude_scope and name=='noctua_sync_protocols'else'to_jsonb(r)'
  parts.append(name+sql(f"SELECT coalesce(jsonb_agg(v ORDER BY v::text),'[]') FROM (SELECT {projection} AS v FROM public.{name} r WHERE owner_id='{owner}') q"))
 for name in ('users','sessions'):
  key='id'if name=='users'else'user_id';parts.append(name+sql(f"SELECT coalesce(jsonb_agg(to_jsonb(r) ORDER BY to_jsonb(r)::text),'[]') FROM auth.{name} r WHERE {key}='{owner}'"))
 return hashlib.sha256('\n'.join(parts).encode()).hexdigest()
def security():
 return hashlib.sha256(sql("SELECT jsonb_agg(to_jsonb(r) ORDER BY r.tablename) FROM (SELECT tablename,policyname,roles,cmd,qual,with_check FROM pg_policies WHERE schemaname='public' ORDER BY tablename,policyname) r").encode()+sql("SELECT jsonb_agg(to_jsonb(r) ORDER BY r.oid) FROM (SELECT c.oid,c.relrowsecurity,c.relacl FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relname LIKE 'noctua_%') r").encode()+sql("SELECT jsonb_build_array(proacl,prosecdef,proconfig,pg_get_function_identity_arguments(oid)) FROM pg_proc WHERE oid='public.noctua_enable_academic_c(bigint)'::regprocedure").encode()).hexdigest()
def prepare():
 # The effective A-E definition is the C function replaced by D, not the earlier C-only body.
 before=security()
 q=f.token('44444444-4444-4444-8444-444444444444','dddddddd-dddd-4ddd-8ddd-dddddddddddd')
 s=rpc('noctua_academic_status_c',token=q);rpc('noctua_activate_academic',dict(p_expected_sequence=s['v1Sequence'],p_expected_v1=s['v1']),q)
 rpc('noctua_enable_academic_c',dict(p_expected_sequence=0),q)
 for scope,stage in ((4,'d'),(5,'e')):
  rpc('noctua_enable_academic_'+stage,dict(p_expected_sequence=0),q)
  denied=rpc('noctua_enable_academic_c',dict(p_expected_sequence=0),q,expected=403);assert denied['code']=='42501'
  assert rpc('noctua_academic_status_c',token=q)['academicScope']==scope
 denied=rpc('noctua_enable_academic_d',dict(p_expected_sequence=0),q,expected=403);assert denied['code']=='42501'
 assert rpc('noctua_academic_status_c',token=q)['academicScope']==5
 assert security()==before
 runpy.run_path(str(ROOT/'scripts/h05-local-fixture.py')) # A=171 records / 15 types, unchanged reference tenant.
 s=rpc('noctua_academic_status_c');rpc('noctua_activate_academic',dict(p_expected_sequence=s['v1Sequence'],p_expected_v1=s['v1']))
 school=dict(id='b-transition-class',name='B fictícia',stage='Médio',subject='História',year=2026,shift='Matutino',schedule='0,0,0,0,0,0,0',duration=50,scale=10.0,low=5.0,high=7.0,countJustified=True,startPresent=False)
 change=lambda kind,key,payload:dict(kind=kind,key=key,base=0,payload=payload)
 rows=[change('classes',dict(id=school['id']),school)]
 for n,(start,end)in enumerate([('01-01','03-31'),('04-01','06-30'),('07-01','09-30'),('10-01','12-31')],1):
  key=dict(classId=school['id'],number=n);rows.append(change('terms',key,dict(key,start='2026-'+start,end='2026-'+end)))
 for n in range(1,37):
  row=dict(id=f'bt-s{n}',personId=f'bt-p{n}',classId=school['id'],name=f'Fictício B {n}',number=n,joined='2026-01-01',left=None,status='Ativo',notes='')
  rows.append(change('students',dict(id=row['id']),row))
 import uuid
 reply=rpc('noctua_push_academic',dict(p_operation=str(uuid.uuid4()),p_changes=rows));assert reply['accepted']
 assert rpc('noctua_academic_status_c')['academicSequence']==1
 for name in ('d','e'):rpc('noctua_enable_academic_'+name,dict(p_expected_sequence=1),expected=403)
 error=rpc('noctua_enable_academic_c',dict(p_expected_sequence=2),expected=500);assert error['code']=='40001'
 assert rpc('noctua_academic_status_c')['academicScope']==2
 result=dict(effective_d_guard_blocks_downgrade=True,no_new_migration_required=True,security_metadata_unchanged=True,skipped_stages_rejected=True,stale_sequence_rejected=True,a_hash=digest(A),b_data_hash=digest(B,True),b_records=41,b_sequence=1,hosted_requests=False)
 (OUT/'postgres.json').write_text(json.dumps(result,indent=2)+'\n')
def verify():
 result=json.loads((OUT/'postgres.json').read_text());assert digest(A)==result['a_hash'];assert digest(B,True)==result['b_data_hash']
 s=rpc('noctua_academic_status_c');assert s['protocol']==2 and s['academicScope']==5 and s['academicSequence']==1 and s['testAllowed']
 result.update(a_unchanged=True,b_data_receipts_histories_unchanged=True,b_scope_final='E',b_sequence_final=1)
 (OUT/'postgres.json').write_text(json.dumps(result,indent=2)+'\n')
if __name__=='__main__':
 import sys
 assert len(sys.argv)==2 and sys.argv[1]in ('prepare','verify')
 globals()[sys.argv[1]]();print('B transition local '+sys.argv[1]+' passed; no hosted access.')
