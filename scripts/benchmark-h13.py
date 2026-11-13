#!/usr/bin/env python3
"""H13: repeated, 8s bounded SQL/HTTP benchmarks, synthetic fixtures, LOCAL ONLY.
Uses E fixtures/validators/receipts, no hosted Auth/key/endpoint or Android inference.
Baseline restores ONLY the old registry helper on an explicitly disposable database.
"""
import argparse,copy,hashlib,importlib.util,json,math,os,statistics,subprocess,time,urllib.request,urllib.error,uuid
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
H13=ROOT/'supabase/migrations/202611130001_h13_registry_cache.sql'

def load(name,path):
 spec=importlib.util.spec_from_file_location(name,path);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m

telemetry=load('h13_telemetry',ROOT/'scripts/benchmark_telemetry.py')

def old_definition():
 source=(ROOT/'supabase/migrations/202611010001_academic_protocol.sql').read_text()
 start=source.index('CREATE OR REPLACE FUNCTION public.noctua_academic_definition(')
 return source[start:source.index('$definition$;',start)+len('$definition$;')]

def summary(samples):
 values=sorted(samples)
 return dict(n=len(values),min=round(values[0],3),median=round(statistics.median(values),3),max=round(values[-1],3),
  sample_p95_nearest_rank=round(values[math.ceil(.95*len(values))-1],3),p95_is_not_an_sla=True)

def canonical(records):return sorted(json.dumps(r,ensure_ascii=False,sort_keys=True,separators=(',',':'))for r in records)
def digest(records):return hashlib.sha256('\n'.join(canonical(records)).encode()).hexdigest()
def cases(f):
 small=f.f.classroom()+[f.f.change('students',dict(f.f.student(f'student-{n}',number=n),personId=f'person-{n}'))for n in range(1,37)]
 result=[small,f.large(0),f.large(100),f.large()];assert [len(r)for r in result]==[41,171,671,5171];return result

def assertions(f,rows,op,reply,pull):
 assert reply['accepted'] and reply['sequence']==1
 for table in ('records','versions'):assert int(f.execute('SELECT count(*) FROM public.noctua_academic_'+table+';',f.A))==len(rows)
 for table in ('receipts','batches'):assert f.execute('SELECT count(*) FROM public.noctua_academic_'+table+';',f.A)=='1'
 expected=[dict(kind=r['kind'],key=r['key'],revision=r['base']+1,payload=r['payload'])for r in rows]
 received=[];offset=0;start=time.perf_counter()
 while offset<len(rows):
  fragments=pull(0,offset);assert len(fragments)==1
  page=fragments[0];assert page['sequence']==1 and page['operation']==op and page['offset']==offset and page['total']==len(rows)
  assert 0<len(page['records'])<=128;received+=page['records'];offset+=len(page['records'])
 assert canonical(expected)==canonical(received) and pull(1,0)==[]
 return dict(recovered_sha256=digest(received),recovery_ms=round((time.perf_counter()-start)*1000,3),payloads_ids_revisions_equal=True,
  sequence=1,records=len(received),receipt_count=1,batch_count=1)

def claims(f):return dict(sub=f.A,role='authenticated',session_id=f.f.SESSION[f.A],exp=int(time.time())+3600)
def sql_commit(f,op):
 sql="SET statement_timeout='8s'; SET ROLE authenticated; SET request.jwt.claim.sub='"+f.A+"'; SET request.jwt.claims='"+json.dumps(claims(f))+"';\nEXPLAIN (ANALYZE,BUFFERS,FORMAT JSON,TIMING OFF) SELECT public.noctua_commit_academic_e('"+op+"');"
 start=time.perf_counter();p=subprocess.run([f.f.PSQL,'-XAtq','-v','ON_ERROR_STOP=1','-v','VERBOSITY=verbose'],input=sql,capture_output=True,text=True,timeout=15,env=dict(os.environ,PGAPPNAME='h13-commit'))
 wall=(time.perf_counter()-start)*1000
 if p.returncode:
  assert '57014' in p.stderr and 'statement timeout'in p.stderr,'Unexpected SQL failure (private fixture response omitted)'
  return None,dict(outcome='57014-atomic-rollback',wall_ms=round(wall,3),server_execution_ms=None)
 plan=json.loads(p.stdout)[0]
 reply=json.loads(f.execute("SELECT response FROM public.noctua_academic_receipts WHERE operation_id='"+op+"';",f.A))
 return reply,dict(outcome='accepted',wall_ms=round(wall,3),server_execution_ms=round(plan['Execution Time'],3),
  shared_hit_blocks=plan['Plan'].get('Shared Hit Blocks'),shared_read_blocks=plan['Plan'].get('Shared Read Blocks'),
  temp_written_blocks=plan['Plan'].get('Temp Written Blocks'),io_read_ms=plan['Plan'].get('I/O Read Time',plan['Plan'].get('Shared I/O Read Time')))

def function_profile(f):
 return json.loads(f.execute("SELECT coalesce(jsonb_agg(to_jsonb(f) ORDER BY total_time DESC),'[]') FROM (SELECT funcname,calls,round(total_time::numeric,3) total_time,round(self_time::numeric,3) self_time FROM pg_stat_user_functions WHERE calls>0) f;"))
def ensure_cancelled(f,parts):
 for table in ('records','versions','batches','receipts','links','uniques'):assert f.execute('SELECT count(*) FROM public.noctua_academic_'+table+';',f.A)=='0'
 assert f.d.status()['academicSequence']==0 and int(f.execute('SELECT count(*) FROM public.noctua_e_parts;',f.A))==parts

class NoRedirect(urllib.request.HTTPRedirectHandler):
 def redirect_request(self,*args,**kwargs):raise AssertionError('Local benchmark refuses redirects')

class Http:
 def __init__(self,f):
  self.f=f;self.net=load('h13_fixture_http',ROOT/'scripts/test-phase-b-http.py');self.headers={'Content-Type':'application/json','Authorization':'Bearer '+self.net.token(f.A,f.f.SESSION[f.A])}
  self.opener=urllib.request.build_opener(NoRedirect())
 def request(self,name,body=None,owner=None):
  assert name.startswith('noctua_')
  headers=self.headers if owner is None else dict(self.headers,Authorization='Bearer '+self.net.token(owner,self.f.f.SESSION[owner]))
  port=os.environ.get('NOCTUA_H13_LOCAL_PORT','3000');assert port in ('3000','33013')
  req=urllib.request.Request('http://127.0.0.1:'+port+'/rpc/'+name,data=json.dumps(body or {}).encode(),headers=headers,method='POST')
  try:
   with self.opener.open(req,timeout=20)as r:raw=r.read(16*1024*1024+1)
  except urllib.error.HTTPError as e:
   raw=e.read(65536)
   if e.code==500 and json.loads(raw).get('code')=='57014':return dict(timeout=True)
   raise AssertionError('Unexpected disposable HTTP status '+str(e.code))from None
  assert len(raw)<=16*1024*1024;return json.loads(raw)
 def commit(self,op):
  start=time.perf_counter();result=self.request('noctua_h13_commit_probe',dict(p_operation=op));wall=(time.perf_counter()-start)*1000
  if result.get('timeout'):return None,dict(outcome='57014-atomic-rollback',wall_ms=round(wall,3),server_execution_ms=None)
  assert result['statement_timeout']=='8s'
  server=result['server_ms'];return result['reply'],dict(outcome='accepted',wall_ms=round(wall,3),server_execution_ms=server,
    residual_http_ms=round(wall-server,3),residual_includes_serialization_commit_network_client=True)
 def activate(self,owner):
  s=self.request('noctua_academic_status_c',owner=owner)
  self.request('noctua_activate_academic',dict(p_expected_sequence=s['v1Sequence'],p_expected_v1=s['v1']),owner)
  for stage in ('c','d','e'):self.request('noctua_enable_academic_'+stage,dict(p_expected_sequence=0),owner)

# These test-only wrappers are NEVER part of a migration or APK.
HTTP_PROBE="""
CREATE OR REPLACE FUNCTION public.noctua_h13_commit_probe(p_operation uuid) RETURNS jsonb LANGUAGE plpgsql SET search_path=pg_catalog AS $$
DECLARE started timestamptz:=clock_timestamp(); result jsonb;
BEGIN
 result:=public.noctua_commit_academic_e(p_operation);
 RETURN jsonb_build_object('reply',result,'server_ms',extract(epoch FROM clock_timestamp()-started)*1000,'statement_timeout',current_setting('statement_timeout'));
END $$;
REVOKE ALL ON FUNCTION public.noctua_h13_commit_probe(uuid) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.noctua_h13_commit_probe(uuid) TO authenticated;
NOTIFY pgrst,'reload schema'; NOTIFY pgrst,'reload config';
"""

def run(mode,repetitions):
 assert os.environ.get('PGHOST')in('127.0.0.1','localhost','/workspace/pg-local'),'Disposable loopback only'
 expected='noctua_phase_e_test'if mode=='sql'else'noctua_phase_b_test'
 assert os.environ.get('PGDATABASE')==expected,'Explicit disposable database required'
 # Reuse the exact existing E/B data constructors, without copying their payload definitions.
 if mode=='http':os.environ['PGDATABASE']='noctua_phase_e_test'
 b=load('h13_base',ROOT/'scripts/test-phase-b-postgres.py');b.reset()
 f=load('h13_e',ROOT/'scripts/test-phase-e-postgres.py')
 if mode=='http':os.environ['PGDATABASE']=expected
 f.execute('ALTER DATABASE '+expected+" SET track_functions='all'; ALTER DATABASE "+expected+" SET track_io_timing=on;")
 variants=('before','after');results=[];http=None
 journal=telemetry.Journal(ROOT/('build/h13/progress-'+mode+'.json'),'H13')
 journal.event(mode=mode,stage='initializing',budget='8s')
 role_setting=None
 try:
  if mode=='http':
   role_setting=f.execute("SELECT coalesce((SELECT split_part(setting,'=',2) FROM pg_roles CROSS JOIN LATERAL unnest(rolconfig) setting WHERE rolname='authenticated' AND setting LIKE 'statement_timeout=%'),'');")
   f.execute("ALTER ROLE authenticated SET statement_timeout='8s';"+HTTP_PROBE)
   http=Http(f)
   # Wait only for real PostgREST schema/config reload; no benchmark timed sleep.
   deadline=time.monotonic()+15
   while True:
    try:
     # Probe without a transfer is expected to fail, so use a read-only settings RPC.
     f.execute("CREATE OR REPLACE FUNCTION public.noctua_h13_budget() RETURNS text LANGUAGE sql AS $$ SELECT current_setting('statement_timeout'); $$; REVOKE ALL ON FUNCTION public.noctua_h13_budget() FROM PUBLIC,anon; GRANT EXECUTE ON FUNCTION public.noctua_h13_budget() TO authenticated; NOTIFY pgrst,'reload schema';")
     assert http.request('noctua_h13_budget')=='8s';break
    except (AssertionError,urllib.error.URLError):
     assert time.monotonic()<deadline,'PostgREST did not apply the 8s role budget';time.sleep(.2)
  settings=json.loads(f.execute("SELECT jsonb_build_object('version',current_setting('server_version'),'shared_buffers',current_setting('shared_buffers'),'work_mem',current_setting('work_mem'),'track_functions',current_setting('track_functions'))"))
  for rows in cases(f):
   samples={v:[]for v in variants}
   # Alternate order to reduce the bias of warm filesystem/cache and CPU frequency.
   for trial in range(repetitions):
    for variant in variants if trial%2==0 else variants[::-1]:
     journal.event(mode=mode,stage='prepare',records=len(rows),parts=(len(rows)+127)//128,trial=trial+1,variant=variant)
     f.execute(old_definition()if variant=='before'else H13.read_text());f.b.reset()
     if http:
      for owner in (f.A,f.B):http.activate(owner)
     else:
      f.b.activate();f.d.c.enable();f.d.enable();f.enable()
     start=time.perf_counter();op=str(uuid.uuid4());parts=[rows[i:i+128]for i in range(0,len(rows),128)]
     if http:
      http.request('noctua_begin_academic_e',dict(p_operation=op,p_total=len(rows),p_parts=len(parts)))
      for i,p in enumerate(parts):http.request('noctua_stage_academic_e',dict(p_operation=op,p_part=i,p_changes=p))
      pull=lambda after,offset:http.request('noctua_pull_academic_e',dict(p_after=after,p_offset=offset))
     else:
      op,parts,_=f.begin(rows)
      for i,p in enumerate(parts):f.stage(op,i,p)
      pull=lambda after,offset:f.fragment(after,offset)
     staging=(time.perf_counter()-start)*1000
     assert pull(0,0)==[] and f.d.status()['academicSequence']==0
     f.execute('SELECT pg_stat_reset();');commit=http.commit if http else lambda op:sql_commit(f,op)
     journal.event(mode=mode,stage='commit-start',records=len(rows),parts=len(parts),trial=trial+1,variant=variant)
     reply,measurement=commit(op)
     profile=function_profile(f)if not http else None
     journal.event(mode=mode,stage='commit-result',records=len(rows),parts=len(parts),trial=trial+1,variant=variant,measurement=measurement,profile=profile,profile_partial=reply is None)
     item=dict(trial=trial+1,variant=variant,records=len(rows),parts=len(parts),staging_ms=round(staging,3),commit=measurement)
     if profile:item['function_profile']=profile
     if reply is None:
      ensure_cancelled(f,len(parts));item['atomic_timeout_verified']=True
      # Preserve all parts/operation. Recover under the SAME 8s budget using the candidate.
      journal.event(mode=mode,stage='rollback-verified',records=len(rows),parts=len(parts),trial=trial+1,variant=variant,rollback_verified=True)
      f.execute(H13.read_text());reply,recovery=commit(op)
      journal.event(mode=mode,stage='recovery-result',records=len(rows),parts=len(parts),trial=trial+1,variant=variant,measurement=recovery,profile=function_profile(f),profile_partial=reply is None,capacity_blocked=reply is None)
      assert reply is not None,'Candidate cannot finish under 8s; capacity blocked (see durable progress journal)'
      item['same_operation_recovery_after_optimization']=recovery
     recovered=assertions(f,rows,op,reply,pull);item.update(recovered)
     replays=[]
     for _ in range(3):
      r,m=commit(op);assert r==reply;replays.append(m)
     item['confirmed_replays']=replays
     assert f.d.status()['academicSequence']==1 and f.execute('SELECT count(*) FROM public.noctua_academic_receipts;',f.A)=='1'
     if http:assert http.request('noctua_pull_academic_e',dict(p_after=0,p_offset=0),f.B)==[]
     else:
      f.b.activate(f.B);f.d.c.enable(f.B);f.d.enable(f.B);f.enable(f.B);assert f.fragment(owner=f.B)==[]
     assert f.execute("SELECT count(*) FROM public.noctua_academic_records WHERE owner_id='"+f.A+"';",f.B)=='0'
     item['foreign_account_denied']=True
     item['table_bytes_including_indexes']=int(f.execute("SELECT sum(pg_total_relation_size(c.oid)) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind='r' AND c.relname LIKE 'noctua_%';"))
     item['input_json_bytes']=len(json.dumps(rows,ensure_ascii=False).encode());samples[variant].append(item)
     journal.event(mode=mode,stage='verified',records=len(rows),trial=trial+1,variant=variant,integrity_equal=True,replay_equal=True,hash=item['recovered_sha256'])
   assert len({s['recovered_sha256']for v in samples.values()for s in v})==1
   stats={}
   for variant in variants:
    accepted=[s['commit']['server_execution_ms']for s in samples[variant]if s['commit']['outcome']=='accepted']
    stats[variant]=dict(accepted=len(accepted),timed_out=repetitions-len(accepted),server_ms=summary(accepted)if accepted else None,
     wall_ms=summary([s['commit']['wall_ms']for s in samples[variant]]),replay_server_ms=summary([r['server_execution_ms']for s in samples[variant]for r in s['confirmed_replays']]))
   results.append(dict(records=len(rows),parts=len(parts),kinds=len({r['kind']for r in rows}),statistics=stats,samples=samples))
  journal.event(mode=mode,stage='completed',completed=True)
  return dict(case='H13',mode=mode,environment='disposable-loopback-postgresql-postgrest',hosted_executed=False,real_auth=False,
   statement_timeout='8s',repetitions=repetitions,postgres=settings,sql_time='EXPLAIN ANALYZE execution time; excludes connection/COMMIT fsync'if mode=='sql'else'clock_timestamp around the original commit RPC; excludes response serialization/final COMMIT',
   wall_time='psql connection + SQL + final transaction'if mode=='sql'else'HTTP encoding + RPC + final transaction + transfer + JSON decode',
   baseline_changes='only original academic_definition registry helper',candidate_migration=H13.name,results=results)
 finally:
  f.execute(H13.read_text());f.b.reset()
  if http:
   f.execute('DROP FUNCTION IF EXISTS public.noctua_h13_commit_probe(uuid); DROP FUNCTION IF EXISTS public.noctua_h13_budget();')
   f.execute("ALTER ROLE authenticated "+("SET statement_timeout='"+role_setting.replace("'","''")+"';"if role_setting else'RESET statement_timeout;')+" NOTIFY pgrst,'reload schema'; NOTIFY pgrst,'reload config';")
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--mode',choices=('sql','http'),default='sql');p.add_argument('--repetitions',type=int,default=5);args=p.parse_args()
 assert 3<=args.repetitions<=7,'Use 3-7 bounded repetitions, no unbounded hosted load'
 print(json.dumps(run(args.mode,args.repetitions),indent=2))
