#!/usr/bin/env python3
"""Repeated integral H13 baseline vs structural H13.2; explicit 8s, LOCAL ONLY.
Checkpoints survive failure; candidate timeouts block admission, never become green.
"""
import argparse,copy,json,os,statistics,sys,time,uuid
import common as c

def run(mode,repetitions):
 b,f,bench,lab=c.fixtures(mode)
 journal=bench.telemetry.Journal(c.ROOT/('build/h132/progress-'+mode+'.json'),'H13.2')
 original_meta=c.metadata(f);original_bodies=c.bodies(f)
 definition_query="SELECT string_agg(pg_get_functiondef(oid),E';\n' ORDER BY proname)||';' FROM pg_proc WHERE pronamespace='public'::regnamespace AND proname IN('noctua_d_get','noctua_d_history_guard','noctua_d_graph');"
 original_functions=f.execute(definition_query)
 http=None;role=None;samples=[];candidate_blocked=False
 try:
  f.execute('ALTER DATABASE '+os.environ['PGDATABASE']+" SET track_functions='all'; ALTER DATABASE "+os.environ['PGDATABASE']+" SET track_io_timing=on;")
  settings=json.loads(f.execute("SELECT jsonb_build_object('version',current_setting('server_version'),'work_mem',current_setting('work_mem'),'shared_buffers',current_setting('shared_buffers'));"))
  if mode=='http':
   role=f.execute("SELECT coalesce((SELECT split_part(setting,'=',2) FROM pg_roles CROSS JOIN LATERAL unnest(rolconfig) setting WHERE rolname='authenticated' AND setting LIKE 'statement_timeout=%'),'');")
   f.execute("ALTER ROLE authenticated SET statement_timeout='8s';"+bench.HTTP_PROBE)
   http=bench.Http(f)
   f.execute("CREATE OR REPLACE FUNCTION public.noctua_h13_budget() RETURNS text LANGUAGE sql AS $$SELECT current_setting('statement_timeout');$$; REVOKE ALL ON FUNCTION public.noctua_h13_budget() FROM PUBLIC,anon; GRANT EXECUTE ON FUNCTION public.noctua_h13_budget() TO authenticated; NOTIFY pgrst,'reload schema'; NOTIFY pgrst,'reload config';")
   deadline=time.monotonic()+15
   while True:
    try:
     if http.request('noctua_h13_budget')=='8s':break
    except (AssertionError,OSError):pass
    assert time.monotonic()<deadline,'Disposable PostgREST failed to apply budget';time.sleep(.2)
  def activate():
   b.reset()
   if http:
    for owner in (f.A,f.B):http.activate(owner)
   else:b.activate();f.d.c.enable();f.d.enable();f.enable()
  def stage(rows):
   op=str(uuid.uuid4());parts=[rows[n:n+128]for n in range(0,len(rows),128)]
   if http:
    http.request('noctua_begin_academic_e',dict(p_operation=op,p_total=len(rows),p_parts=len(parts)))
    for n,p in enumerate(parts):http.request('noctua_stage_academic_e',dict(p_operation=op,p_part=n,p_changes=p))
   else:
    op,parts,_=f.begin(rows)
    for n,p in enumerate(parts):f.stage(op,n,p)
   return op,len(parts)
  commit=http.commit if http else lambda op:bench.sql_commit(f,op)
  pull=(lambda after,offset:http.request('noctua_pull_academic_e',dict(p_after=after,p_offset=offset)))if http else(lambda after,offset:f.fragment(after,offset))
  datasets=[(str(len(r))+'-records',r)for r in bench.cases(f)]
  extended=f.large();history=lab.extensive(f.d.SNAP)['histories'][0]
  extended=[dict(r,payload=history)if r['kind']=='preserved_histories'else r for r in extended]
  datasets.append(('5171-extended-history',extended))
  for label,rows in datasets:
   for scenario in ['bootstrap']+(['single-grade']if len(rows)==5171 else[]):
    for trial in range(repetitions):
     for variant in (('before','after')if trial%2==0 else('after','before')):
      context=dict(mode=mode,label=label,stage='prepare',records=len(rows),trial=trial+1,variant=variant,case=scenario)
      journal.event(**context,budget='8s');activate()
      if scenario=='single-grade':
       # Identical valid baseline through the real bounded candidate writer, then
       # switch representations before measuring. No administrative data insertion.
       f.execute(c.CANDIDATE.read_text());op,parts=stage(rows);r,m=commit(op)
       journal.event(**dict(context,stage='seed-result'),parts=parts,measurement=m)
       if r is None:
        bench.ensure_cancelled(f,parts);candidate_blocked=True;samples.append(dict(**context,outcome='candidate-seed-capacity-blocked',measurement=m));continue
       assert r['accepted'];bench.assertions(f,rows,op,r,pull)
       source=copy.deepcopy(next(r for r in rows if r['kind']=='grades'and r['payload']['studentId']=='ds2'))
       source['base']=1;source['payload']['value']=0 if trial%2 else 1;changes=[source]
      else:changes=rows
      f.execute(c.BASELINE if variant=='before'else c.CANDIDATE.read_text())
      staging=time.perf_counter();op,parts=stage(changes);staging_ms=(time.perf_counter()-staging)*1000
      before=c.state(f);f.execute('SELECT pg_stat_reset();')
      journal.event(**dict(context,stage='commit-start'),parts=parts,changes=len(changes))
      reply,m=commit(op);profile=bench.function_profile(f)
      journal.event(**dict(context,stage='commit-result'),parts=parts,changes=len(changes),measurement=m,profile=profile,profile_partial=(mode=='http'or reply is None))
      item=dict(label=label,scenario=scenario,mode=mode,variant=variant,trial=trial+1,records=len(rows),parts=parts,changes=len(changes),commit=m,function_profile=profile,profile_partial=(mode=='http'or reply is None),staging_wall_ms=round(staging_ms,3))
      if reply is None:
       assert c.state(f)==before,'Timeout leaked data/versions/receipt/cursor'
       item.update(rollback_verified=True,persisted_parts=parts)
       if variant=='after':candidate_blocked=True
       journal.event(**dict(context,stage='rollback-verified'),rollback_verified=True,capacity_blocked=variant=='after');samples.append(item);continue
      assert reply['accepted']
      if scenario=='bootstrap':item.update(bench.assertions(f,rows,op,reply,pull))
      else:
       assert reply['sequence']==2;page=pull(1,0);assert len(page)==1 and page[0]['total']==1
       assert page[0]['records']==[dict(kind=source['kind'],key=source['key'],revision=2,payload=source['payload'])]
       actual=json.loads(f.execute("SELECT jsonb_agg(jsonb_build_object('kind',kind,'key',key,'revision',revision,'payload',payload)) FROM public.noctua_academic_records;",f.A))
       expected=[dict(kind=r['kind'],key=r['key'],revision=2 if r['kind']==source['kind']and r['key']==source['key']else 1,payload=source['payload']if r['kind']==source['kind']and r['key']==source['key']else r['payload'])for r in rows]
       assert bench.canonical(actual)==bench.canonical(expected)
       item.update(payloads_ids_revisions_equal=True,recovered_sha256=bench.digest(actual),receipt_count=int(f.execute('SELECT count(*) FROM public.noctua_academic_receipts;',f.A)),sequence=2)
       assert item['receipt_count']==2
      stable=c.state(f);replays=[]
      for _ in range(3):
       r,replay=commit(op);assert r==reply;replays.append(replay)
      assert c.state(f)==stable;item.update(confirmed_replays=replays,replay_equal=True)
      if http:assert http.request('noctua_pull_academic_e',dict(p_after=0,p_offset=0),f.B)==[]
      else:
       b.activate(f.B);f.d.c.enable(f.B);f.d.enable(f.B);f.enable(f.B);assert f.fragment(owner=f.B)==[]
      item['foreign_account_denied']=True
      journal.event(**dict(context,stage='verified'),integrity_equal=True,replay_equal=True,hash=item['recovered_sha256']);samples.append(item)
  summary=[]
  for label,rows in datasets:
   for scenario in ['bootstrap']+(['single-grade']if len(rows)==5171 else[]):
    group=[s for s in samples if s['label']==label and s.get('scenario',s.get('case'))==scenario]
    stats={}
    for v in ('before','after'):
     items=[x for x in group if x['variant']==v];accepted=[x for x in items if x.get('commit',{}).get('outcome')=='accepted']
     stats[v]=dict(accepted=len(accepted),blocked=len(items)-len(accepted),server_ms=bench.summary([x['commit']['server_execution_ms']for x in accepted])if accepted else None)
    valid=[x for x in group if x.get('recovered_sha256')]
    for trial in range(repetitions):
     hashes={x['recovered_sha256']for x in valid if x['trial']==trial+1};assert len(hashes)<=1
    if mode=='sql':
     predicates=['noctua_academic_validate','noctua_academic_shape','noctua_d_graph','noctua_d_history_guard','noctua_validate_class']
     for trial in range(repetitions):
      pair=[x for x in valid if x['trial']==trial+1]
      if len(pair)==2:
       signatures=[{p['funcname']:p['calls']for p in x['function_profile']if p['funcname']in predicates}for x in pair]
       assert signatures[0]==signatures[1],'Integral validator call counts changed'
    summary.append(dict(label=label,scenario=scenario,records=len(rows),statistics=stats))
  gain_checks=[]
  for entry in summary:
   old=entry['statistics']['before'];new=entry['statistics']['after']
   reference=old['server_ms']['median']if old['server_ms']else 8000
   limit=reference*.9 if entry['records']>=5171 else max(reference*1.1,reference+5)
   gain_checks.append(dict(label=entry['label'],scenario=entry['scenario'],passed=new['accepted']==repetitions and new['server_ms']['median']<=limit,limit_ms=round(limit,3)))
  gain_demonstrated=all(x['passed']for x in gain_checks)
  journal.event(stage='measurements-completed',capacity_blocked=candidate_blocked)
  return dict(case='H13.2',mode=mode,hosted_executed=False,real_auth=False,budget='8s',repetitions=repetitions,postgres=settings,
   candidate_blocked=candidate_blocked,gain_demonstrated=gain_demonstrated,gain_checks=gain_checks,baseline='H13 integral with original getter/history guard',candidate='same predicates, materialized lookups',
   timing='SQL EXPLAIN or HTTP original RPC body; wall is separate; no Android time inferred',summaries=summary,samples=samples)
 finally:
  journal.event(stage='cleanup-start')
  f.execute(original_functions);b.reset()
  if http:
   f.execute('DROP FUNCTION IF EXISTS public.noctua_h13_commit_probe(uuid); DROP FUNCTION IF EXISTS public.noctua_h13_budget();')
   f.execute("ALTER ROLE authenticated "+("SET statement_timeout='"+role.replace("'","''")+"';"if role else'RESET statement_timeout;')+" NOTIFY pgrst,'reload schema'; NOTIFY pgrst,'reload config';")
  assert f.execute(definition_query)==original_functions,'Previous helper definitions not restored'
  assert c.metadata(f)==original_meta and c.bodies(f)==original_bodies,'Unrelated function/permission/settings change'
  journal.event(stage='cleanup-verified',completed=True)

if __name__=='__main__':
 parser=argparse.ArgumentParser();parser.add_argument('--mode',choices=['sql','http'],default='sql');parser.add_argument('--repetitions',type=int,default=3);args=parser.parse_args();assert 3<=args.repetitions<=5
 r=run(args.mode,args.repetitions);print(json.dumps(r,indent=2));sys.exit(1 if r['candidate_blocked']or not r['gain_demonstrated']else 0)
