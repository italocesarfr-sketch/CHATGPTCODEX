#!/usr/bin/env python3
"""Unmodified E RPCs, real disposable PostgREST, synthetic two-session clients.
No candidate validator is installed in this database or any write path.
"""
import concurrent.futures,copy,hashlib,importlib.util,json,os,socket,tempfile,time,urllib.request,urllib.error,uuid
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
assert os.environ.get('PGHOST')in('127.0.0.1','localhost','/workspace/pg-local')and os.environ.get('PGDATABASE')=='noctua_phase_b_test'
def load(n,file):
 s=importlib.util.spec_from_file_location(n,ROOT/'scripts'/file);m=importlib.util.module_from_spec(s);s.loader.exec_module(m);return m
selected_database=os.environ['PGDATABASE'];os.environ['PGDATABASE']='noctua_phase_e_test'
f=load('h131_net_fixture','test-phase-b-postgres.py');os.environ['PGDATABASE']=selected_database
wire=load('h131_http','test-phase-b-http.py')
port=os.environ.get('NOCTUA_H131_LOCAL_PORT','3000');assert port in('3000','33013')
A=f.A;B=f.B;ta=wire.token(A,'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');tb=wire.token(A,'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');other=wire.token(B,'cccccccc-cccc-4ccc-8ccc-cccccccccccc')
class NoRedirect(urllib.request.HTTPRedirectHandler):
 def redirect_request(self,*args,**kwargs):raise AssertionError('Disposable test refuses redirects')
opener=urllib.request.build_opener(NoRedirect())
def rpc(name,body=None,token=ta,expected=200,local_port=port):
 assert name.startswith('noctua_')
 request=urllib.request.Request('http://127.0.0.1:'+str(local_port)+'/rpc/'+name,data=json.dumps(body or {}).encode(),headers={'Content-Type':'application/json','Authorization':'Bearer '+token},method='POST')
 try:
  with opener.open(request,timeout=20)as r:code=r.status;raw=r.read(16*1024*1024+1)
 except urllib.error.HTTPError as e:code=e.code;raw=e.read(65536)
 assert code==expected,'Disposable RPC '+name+' status='+str(code)+' code='+str(json.loads(raw).get('code','unknown'))
 assert len(raw)<=16*1024*1024,'Response size limit exceeded'
 value=json.loads(raw)if raw else None
 return value

def activate(token):
 s=rpc('noctua_academic_status_c',token=token)
 rpc('noctua_activate_academic',dict(p_expected_sequence=s['v1Sequence'],p_expected_v1=s['v1']),token)
 for stage in('c','d','e'):rpc('noctua_enable_academic_'+stage,dict(p_expected_sequence=0),token)
def stage(rows,token=ta,op=None):
 op=op or str(uuid.uuid4());parts=[rows[n:n+128]for n in range(0,len(rows),128)]
 rpc('noctua_begin_academic_e',dict(p_operation=op,p_total=len(rows),p_parts=len(parts)),token)
 for n,p in enumerate(parts):rpc('noctua_stage_academic_e',dict(p_operation=op,p_part=n,p_changes=p),token)
 return op
def commit(op,token=ta,expected=200):return rpc('noctua_commit_academic_e',dict(p_operation=op),token,expected)
def digest():
 raw=f.execute("SELECT coalesce(string_agg(v::text,E'\\n' ORDER BY v::text),'') FROM (SELECT jsonb_build_array(kind,key,revision,sequence,deleted,payload) v FROM public.noctua_academic_records) r;",A)
 return hashlib.sha256(raw.encode()).hexdigest()
def full_hash():return f.execute("SELECT md5(string_agg(oid::text||prosrc,E'\\n' ORDER BY oid)) FROM pg_proc WHERE pronamespace='public'::regnamespace AND proname LIKE 'noctua_%';")
start=time.perf_counter();baseline_functions=full_hash();activate(ta);activate(other)
snap=json.loads((ROOT/'app/src/androidTest/assets/phase-d.json').read_text())
NAMES=dict(classes='classes',terms='terms',students='students',lessons='lessons',marks='marks',activities='activities',grades='grades',recompositions='recompositions',relocations='relocations',drafts='import_drafts',exportRows='export_rows',classArchives='class_archives',lessonRecords='lesson_records',histories='preserved_histories',timetables='weekly_timetables')
def change(kind,row,base=0,key=None):return f.f.change(kind,row,base,key)
rows=[change(kind,r)for name,kind in NAMES.items()for r in snap[name]];seed=stage(rows);assert commit(seed)['accepted']
grade=next(r for r in rows if r['kind']=='grades'and r['payload']['studentId']=='ds2');pending=dict(grade,base=1,payload=dict(grade['payload'],value=1))
# Persist a fictitious pending operation before any connection; restore exact bytes.
with tempfile.TemporaryDirectory(prefix='h131-local-private-')as tmp:
 p=Path(tmp)/'pending.json';op=str(uuid.uuid4());raw=json.dumps(dict(operation=op,changes=[pending])).encode()
 fd=os.open(p,os.O_CREAT|os.O_EXCL|os.O_WRONLY,0o600)
 with os.fdopen(fd,'wb')as out:out.write(raw)
 with socket.socket()as unavailable:
  unavailable.bind(('127.0.0.1',0))
  try:rpc('noctua_academic_status_c',local_port=unavailable.getsockname()[1]);raise AssertionError('Expected connection refusal')
  except urllib.error.URLError:pass
 assert p.read_bytes()==raw
 recovered=json.loads(p.read_bytes());assert recovered['operation']==op
 stage(recovered['changes'],op=op);accepted=commit(op) # Drop the response at the client; no acknowledgement yet.
 assert accepted['accepted'];assert commit(op,tb)==accepted
 assert rpc('noctua_academic_status_c')['academicSequence']==2
# Concurrent devices, same revision: exactly one wins and both receipts stay stable.
ops=[]
for value,token in((2,ta),(3,tb)):
 ops.append((stage([dict(grade,base=2,payload=dict(grade['payload'],value=value))],token),token))
with concurrent.futures.ThreadPoolExecutor(2)as pool:answers=list(pool.map(lambda entry:commit(*entry),ops))
assert sum(r['accepted']for r in answers)==1
for entry,result in zip(ops,answers):assert commit(*entry)==result
assert rpc('noctua_academic_status_c')['academicSequence']==3
# Independent record edits both converge; null and zero remain separate values.
independent=[]
for sid,value,token in(('ds3',None,ta),('ds4',0,tb)):
 g=next(r for r in rows if r['kind']=='grades'and r['payload']['studentId']==sid)
 independent.append((stage([dict(g,base=1,payload=dict(g['payload'],value=value))],token),token))
with concurrent.futures.ThreadPoolExecutor(2)as pool:assert all(r['accepted']for r in pool.map(lambda entry:commit(*entry),independent))
assert rpc('noctua_academic_status_c')['academicSequence']==5
# Peer changes an activity maximum. An older grade preview cannot override it.
a=next(r for r in rows if r['kind']=='activities'and r['payload']['id']=='d-a1');a=dict(a,base=1,payload=dict(a['payload'],maximum=7))
assert commit(stage([a],tb),tb)['accepted']
head=rpc('noctua_academic_status_c')['academicSequence'];before=digest()
stale=stage([dict(grade,base=3,payload=dict(grade['payload'],value=8))]);err=commit(stale,expected=400);assert err['code']=='P0001'
assert digest()==before and rpc('noctua_academic_status_c')['academicSequence']==head
assert f.execute("SELECT count(*) FROM public.noctua_e_parts WHERE operation_id='"+stale+"';",A)=='1'
# A bad historical change cannot partially apply a valid grade from the same batch.
h=copy.deepcopy(next(r for r in rows if r['kind']=='preserved_histories'));h['base']=1;h['payload']['content']['grades'][0]['value']=9
mixed=stage([dict(grade,base=3,payload=dict(grade['payload'],value=0)),h]);before=digest();err=commit(mixed,expected=400)
assert err['code']=='P0001'and digest()==before and rpc('noctua_academic_status_c')['academicSequence']==head
assert rpc('noctua_pull_academic_e',dict(p_after=0,p_offset=0),other)==[]
# Sessions and private writers remain protected; only synthetic fixture credentials.
rpc('noctua_e_guarded_write',dict(p_operation=str(uuid.uuid4()),p_changes=[]),token=other,expected=403)
rpc('noctua_academic_status_c',token='invalid-fixture-token',expected=401)
assert full_hash()==baseline_functions
assert f.execute('SELECT count(*) FROM public.noctua_academic_records;',A)=='171'
assert f.execute('SELECT count(*) FROM public.noctua_academic_receipts;',A)=='7' # seed, replayed offline write, conflict pair, independent pair, activity
assert rpc('noctua_academic_status_c')['academicSequence']==6
print(json.dumps(dict(case='H13.1-network',hosted_executed=False,real_auth=False,production_validator='INTEGRAL_UNCHANGED',
 client_fixture_restart_not_android_process_death=True,offline_pending_preserved=True,lost_response_replay_exact=True,
 same_record_conflict_preserved=True,independent_records_converged=True,stale_parent_revalidated=True,
 invalid_history_atomic_rollback=True,pending_failed_parts_preserved=True,foreign_account_denied=True,
 invalid_session_denied=True,private_helper_denied=True,public_functions_unchanged=True,records=171,head=6,receipts=7,
 final_sha256=digest(),wall_ms=round((time.perf_counter()-start)*1000,3)),indent=2))
