#!/usr/bin/env python3
"""H13.1 differential laboratory. No production validator is replaced.
Runs only on the whitelisted disposable E DB. A counterexample is evidence of
NON-equivalence, never classified as approval of the proposed shortcut.
"""
import argparse,copy,hashlib,importlib.util,json,os,subprocess,time,uuid
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]

def load(n,file):
 s=importlib.util.spec_from_file_location(n,ROOT/'scripts'/file);m=importlib.util.module_from_spec(s);s.loader.exec_module(m);return m

def clone(snapshot,prefix):
 ids=set()
 def gather(x):
  if isinstance(x,dict):
   for k,v in x.items():
    if k in {'id','classId','personId','studentId','lessonId','activityId','sourceStudentId','targetStudentId','targetClassId'}and isinstance(v,str):ids.add(v)
    gather(v)
  elif isinstance(x,list):
   for v in x:gather(v)
 gather(snapshot)
 def value(s):
  if s.startswith('student:'):return 'student:'+prefix+s[8:]
  if s.startswith('blank:'):return 'blank:'+prefix+s[6:]
  return prefix+s
 mapping={s:value(s)for s in ids}
 def apply(x):
  if isinstance(x,dict):return {k:apply(v)for k,v in x.items()}
  if isinstance(x,list):return [apply(v)for v in x]
  return mapping.get(x,x)if isinstance(x,str)else x
 result=apply(snapshot)
 # Timetable revision uniqueness is per owner/year/effective, not per class.
 offset=int(''.join(c for c in prefix if c.isdigit())or'0')*10
 for t in result['timetables']:t['revision']+=offset
 return result

def extensive(snapshot,n=80):
 s=copy.deepcopy(snapshot);c=s['histories'][0]['content']
 for i in range(n):
  lesson=dict(c['lessons'][0],id=f'h131-history-lesson-{i}',ordinal=100+i)
  activity=dict(c['activities'][0],id=f'h131-history-activity-{i}')
  c['lessons'].append(lesson);c['activities'].append(activity)
  c['marks'].append(dict(c['marks'][0],lessonId=lesson['id']))
  c['grades'].append(dict(c['grades'][0],activityId=activity['id'],value=None if i%2 else 0))
 return s

def run(repetitions):
 assert os.environ.get('PGDATABASE')=='noctua_phase_e_test' and os.environ.get('PGHOST')in('127.0.0.1','localhost','/workspace/pg-local'),'Disposable loopback E DB only'
 b=load('h131_b','test-phase-b-postgres.py');b.reset();f=load('h131_e','test-phase-e-postgres.py');bench=load('h131_bench','benchmark-h13.py')
 f.execute((ROOT/'scripts/h131/focal-grade-lab.sql').read_text())
 tables=('academic_records','academic_versions','academic_batches','academic_receipts','academic_links','academic_uniques','academic_heads','e_parts','e_transfers','e_decisions')
 def state():
  values=[]
  for name in tables:
   values.append(f.execute("SELECT coalesce(string_agg(v::text,E'\\n' ORDER BY v::text),'') FROM (SELECT to_jsonb(t) v FROM public.noctua_"+name+" t) q;",f.A))
  return hashlib.sha256('\n'.join(values).encode()).hexdigest()
 def public_functions():return f.execute("SELECT md5(string_agg(oid::text||prosrc,E'\\n' ORDER BY oid)) FROM pg_proc WHERE pronamespace='public'::regnamespace AND proname LIKE 'noctua_%';")
 original=public_functions();results=[]
 def setup(rows):
  b.reset();b.activate();f.d.c.enable();f.d.enable();f.enable()
  op,parts,_=f.begin(rows)
  for n,p in enumerate(parts):f.stage(op,n,p)
  reply,measurement=bench.sql_commit(f,op)
  if reply is None:
   bench.ensure_cancelled(f,len(parts));measurement['capacity_blocked']=True;measurement['atomic_timeout_verified']=True
  else:assert reply['accepted']
  return measurement
 def compare(name,changes):
  before=state();op=str(uuid.uuid4());claims=bench.claims(f)
  sql="SET ROLE authenticated; SET request.jwt.claim.sub='"+f.A+"'; SET request.jwt.claims='"+json.dumps(claims)+"'; SET statement_timeout='8s'; SELECT h131_lab.compare('"+op+"',"+f.literal(changes)+");"
  start=time.perf_counter();p=subprocess.run([f.f.PSQL,'-XAtq','-v','ON_ERROR_STOP=1','-v','VERBOSITY=verbose'],input=sql,text=True,capture_output=True,timeout=15)
  if p.returncode:
   assert '57014'in p.stderr,'Unexpected laboratory failure (private response omitted)'
   r=dict(candidate='INCONCLUSIVE',integral_accepted=None,equivalent_on_case=None,integral_sqlstate='57014')
  else:r=json.loads(p.stdout)
  assert state()==before,'Differential oracle leaked an academic write/receipt'
  r.update(case=name,wall_ms=round((time.perf_counter()-start)*1000,3),oracle_rolled_back=True)
  results.append(r);return r
 def grade(rows,value=1):
  row=next(r for r in rows if r['kind']=='grades'and r['payload']['studentId']=='ds2')
  return dict(row,base=1,payload=dict(row['payload'],value=value))
 try:
  rows=f.d.rows();setup(rows)
  for v in (None,0,1,5,10,-1,11):
   r=compare('grade-value-'+('null'if v is None else str(v)),[grade(rows,v)])
   assert r['equivalent_on_case']is True
  stale=dict(grade(rows),base=0);assert compare('stale-grade-base',[stale])['candidate']=='REJECT'
  # Every other type remains an explicit integral fallback; it is not "optimized".
  seen=set()
  for row in rows:
   if row['kind']in seen:continue
   seen.add(row['kind']);r=compare('same-payload-'+row['kind'],[dict(row,base=1)])
   assert r['equivalent_on_case']is True
   if row['kind']!='grades':assert r['candidate']=='FULL_REQUIRED'
  audit=copy.deepcopy(next(r for r in rows if r['kind']=='relocations'));audit['base']=1;audit['payload']['corrections'][0]['previousTarget']='Changed fixture audit'
  assert compare('immutable-relocation-audit',[audit])['integral_accepted']is False
  history=copy.deepcopy(next(r for r in rows if r['kind']=='preserved_histories'));history['base']=1;history['payload']['content']['grades'][0]['value']=9
  assert compare('immutable-preserved-grade',[history])['integral_accepted']is False
  assert compare('mixed-grade-and-invalid-history',[grade(rows),history])['integral_accepted']is False
  target_id=next(r['payload']['targetStudentId']for r in rows if r['kind']=='relocations'and r['payload']['targetStudentId'])
  target=copy.deepcopy(next(r for r in rows if r['kind']=='students'and r['payload']['id']==target_id))
  target['base']=1;target['payload']['personId']='incompatible-fixture-person'
  assert compare('movement-person-mismatch',[target])['integral_accepted']is False
  # Check the proposed legacy route first. C must reject the malformed enrollment.
  # The differential counterexample below is explicitly ADMIN FAULT INJECTION,
  # not evidence of an ordinary user bypass or a reachable product defect.
  b.reset();b.activate();f.d.c.enable()
  c=f.f.classroom();other=f.f.classroom('unrelated-fixture')
  student=f.f.student();bad=dict(f.f.student('unrelated-student','unrelated-fixture',1),status='Transferido',left=None)
  legacy=c+other+[f.f.change('students',student),f.f.change('students',bad),f.f.change('activities',f.f.activity()),f.d.c.grade(0)]
  rejected=f.execute(f.d.c.statement(legacy),f.A,fails=True)
  assert 'enrollment requires a movement chain beyond phase B'in rejected
  assert f.d.status()['academicSequence']==0
  valid_legacy=[dict(r,payload=dict(r['payload'],status='Ativo'))if r['kind']=='students'and r['payload']['id']=='unrelated-student'else r for r in legacy]
  assert f.d.c.push(valid_legacy)['accepted'];f.d.enable();f.enable()
  f.execute("UPDATE public.noctua_academic_records SET payload=jsonb_set(payload,'{status}','\"Transferido\"') WHERE owner_id='"+f.A+"' AND kind='students' AND key="+f.literal({'id':'unrelated-student'})+";")
  g=f.d.c.grade(1,base=1)
  counter=compare('admin-fault-unrelated-inactive-without-exit',[g])
  assert counter['candidate']=='ACCEPT'and counter['integral_accepted']is False and counter['equivalent_on_case']is False
  repaired=f.f.change('students',dict(bad,status='Ativo'),1)
  repair=compare('repair-legacy-state-and-grade-atomically',[repaired,g]);assert repair['candidate']=='FULL_REQUIRED'and repair['integral_accepted']is True
  # A pre-validation that REJECTS an invalid baseline would incorrectly block this repair.
  # Current integrity oracle stays unchanged for both directions.
  datasets=[('5171-records',f.large()),('six-classes-three-histories',f.large()+f.d.rows(clone(f.d.SNAP,'copy1-'))+f.d.rows(clone(f.d.SNAP,'copy2-')))]
  big=copy.deepcopy(f.d.SNAP);big=extensive(big);extra=[r for r in f.large()if not(r['kind']=='preserved_histories')]
  datasets.append(('5171-extended-historical-content',extra+[f.f.change('preserved_histories',big['histories'][0])]))
  benchmarks=[]
  for label,dataset in datasets:
   bootstrap=setup(dataset);samples=[]
   construction=None
   if bootstrap.get('capacity_blocked') and label=='5171-extended-historical-content':
    # A failed initial atomic transfer stays failed. Build a separate research
    # fixture through independent, valid enrollment transactions; never split
    # a relocation/history or retry a production batch with altered semantics.
    def synthetic(r):return str(r['payload'].get('studentId',r['payload'].get('id',''))).startswith('e-student-')
    seed=[r for r in dataset if not synthetic(r)];addition=[r for r in dataset if synthetic(r)]
    construction=dict(independent_fixture_transactions=True,initial=setup(seed),steps=[])
    ids=sorted(r['payload']['id']for r in addition if r['kind']=='students')
    if not construction['initial'].get('capacity_blocked'):
     for first in range(0,len(ids),100):
      group=set(ids[first:first+100]);chunk=[r for r in addition if r['payload'].get('studentId',r['payload'].get('id'))in group]
      assert len(chunk)==len(group)*5
      op,parts,_=f.begin(chunk)
      for n,p in enumerate(parts):f.stage(op,n,p)
      before=state();reply,m=bench.sql_commit(f,op);construction['steps'].append(m)
      if reply is None:
       assert state()==before;construction['capacity_blocked']=True;break
      assert reply['accepted']
     else:construction['completed']=True
   if bootstrap.get('capacity_blocked') and not(construction and construction.get('completed')):
    benchmarks.append(dict(label=label,physical_records=len(dataset),bootstrap=bootstrap,construction=construction,samples=[],equivalence='INCONCLUSIVE_BOOTSTRAP_TIMEOUT'))
    continue
   for i in range(repetitions):
    r=compare(label+'-'+str(i+1),[grade(dataset,0 if i%2 else 1)])
    if r['integral_accepted']is not None:assert r['equivalent_on_case']is True
    samples.append(r)
   # Independent EXPLAIN sample: buffers/temp/I/O, not a peak-memory measurement.
   before=state();profile_sql="SET ROLE authenticated; SET request.jwt.claim.sub='"+f.A+"'; SET request.jwt.claims='"+json.dumps(bench.claims(f))+"'; SET statement_timeout='8s'; EXPLAIN (ANALYZE,BUFFERS,FORMAT JSON,TIMING OFF) SELECT h131_lab.compare('"+str(uuid.uuid4())+"',"+f.literal([grade(dataset,1)])+");"
   pp=subprocess.run([f.f.PSQL,'-XAtq','-v','ON_ERROR_STOP=1','-v','VERBOSITY=verbose'],input=profile_sql,text=True,capture_output=True,timeout=15)
   if pp.returncode:
    assert '57014'in pp.stderr;profile=dict(outcome='57014-atomic-rollback')
   else:
    plan=json.loads(pp.stdout)[0];profile=dict(outcome='completed',execution_ms=plan['Execution Time'],shared_hit_blocks=plan['Plan'].get('Shared Hit Blocks'),shared_read_blocks=plan['Plan'].get('Shared Read Blocks'),temp_written_blocks=plan['Plan'].get('Temp Written Blocks'),peak_memory_measured=False)
   assert state()==before
   benchmarks.append(dict(label=label,physical_records=len(dataset),construction=construction,resource_profile=profile,projection_rows=int(f.execute("SELECT jsonb_array_length(public.noctua_d_projection('"+f.A+"'));")),bootstrap=bootstrap,samples=samples))
  # Two independently validated sessions/devices cannot reuse a stale parent preview.
  setup(rows);preview=compare('before-peer-maximum-change',[grade(rows,8)]);assert preview['integral_accepted']is True
  activity=copy.deepcopy(next(r for r in rows if r['kind']=='activities'and r['payload']['id']=='d-a1'));activity['base']=1;activity['payload']['maximum']=7
  # Seed fixture grade may already be <=7. Real CAS/graph must decide the peer update.
  op,parts,_=f.begin([activity])
  for n,p in enumerate(parts):f.stage(op,n,p)
  peer=f.commit(op);assert peer['accepted']
  after=compare('after-peer-maximum-change',[grade(rows,8)]);assert after['candidate']=='REJECT'and after['integral_accepted']is False
  # No hypothetical stale "accepted" flag may authorize this later operation.
  assert public_functions()==original
  verdict=dict(case='H13.1',candidate='existing-grade-value-and-parents',replacement_authorized=False,production_validator='INTEGRAL_UNCHANGED',
   equivalence='NOT_DEMONSTRATED_COUNTEREXAMPLE_FOUND',hosted_executed=False,statement_timeout='8s',
   public_functions_unchanged=True,oracle_transactions_rolled_back=True,
   settings=json.loads(f.execute("SELECT jsonb_build_object('server',current_setting('server_version'),'shared_buffers',current_setting('shared_buffers'),'work_mem',current_setting('work_mem'),'temp_file_limit',current_setting('temp_file_limit'));")),
   counterexample_origin='explicit-disposable-admin-fault-injection',legacy_C_malformed_state_rejected=True,preview_revalidation_required=True,
   comparisons=results,benchmarks=benchmarks,repetitions=repetitions)
  return verdict
 finally:
  unchanged=public_functions()==original
  f.execute('DROP SCHEMA h131_lab CASCADE;');b.reset()
  assert unchanged,'Public function changed during isolated research'
if __name__=='__main__':
 parser=argparse.ArgumentParser();parser.add_argument('--repetitions',type=int,default=3);args=parser.parse_args()
 assert 3<=args.repetitions<=5
 print(json.dumps(run(args.repetitions),indent=2))
