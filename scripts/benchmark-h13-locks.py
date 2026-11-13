#!/usr/bin/env python3
"""Real owner-lock wait + 8s cancellation and same-operation replay. LOCAL ONLY."""
import concurrent.futures,importlib.util,json,os,select,subprocess,time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
assert os.environ.get('PGDATABASE')=='noctua_phase_e_test' and os.environ.get('PGHOST')in('127.0.0.1','localhost','/workspace/pg-local')
def load(n,file):
 s=importlib.util.spec_from_file_location(n,ROOT/'scripts'/file);m=importlib.util.module_from_spec(s);s.loader.exec_module(m);return m
b=load('lock_base','test-phase-b-postgres.py');b.reset();f=load('lock_e','test-phase-e-postgres.py');bench=load('lock_bench','benchmark-h13.py')
locker=None
try:
 for owner in (f.A,f.B):b.activate(owner);f.d.c.enable(owner);f.d.enable(owner);f.enable(owner)
 rows=bench.cases(f)[0];op,parts,_=f.begin(rows)
 for n,part in enumerate(parts):f.stage(op,n,part)
 op_b,parts_b,_=f.begin(rows,f.B)
 for n,part in enumerate(parts_b):f.stage(op_b,n,part,f.B)
 locker=subprocess.Popen([f.f.PSQL,'-XAtq','-v','ON_ERROR_STOP=1'],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True,bufsize=1)
 locker.stdin.write("BEGIN; SELECT 'H13_LOCK_READY' FROM public.noctua_sync_heads WHERE owner_id='"+f.A+"' FOR UPDATE;\n");locker.stdin.flush()
 deadline=time.monotonic()+5
 while True:
  assert time.monotonic()<deadline,'Owner lock not ready'
  ready,_,_=select.select([locker.stdout],[],[],.1)
  if ready and locker.stdout.readline().strip()=='H13_LOCK_READY':break
 started=time.perf_counter();reply_b=json.loads(f.execute("SET statement_timeout='8s'; SELECT public.noctua_commit_academic_e('"+op_b+"');",f.B));b_ms=(time.perf_counter()-started)*1000
 assert reply_b['accepted'] and reply_b['sequence']==1,'Unrelated owner blocked'
 with concurrent.futures.ThreadPoolExecutor(1)as pool:
  worker=pool.submit(bench.sql_commit,f,op);observed=False;event=None;deadline=time.monotonic()+12
  while not worker.done():
   assert time.monotonic()<deadline
   event=f.execute("SELECT coalesce(max(wait_event),'') FROM pg_stat_activity WHERE datname=current_database() AND application_name='h13-commit' AND wait_event_type='Lock';")
   if event:observed=True
   time.sleep(.02)
  reply,blocked=worker.result()
 assert observed and reply is None and blocked['outcome']=='57014-atomic-rollback'
 bench.ensure_cancelled(f,len(parts))
 locker.stdin.write('ROLLBACK;\n\\q\n');locker.stdin.flush();locker.communicate(timeout=5);locker=None
 reply,retry=bench.sql_commit(f,op);assert reply and reply['accepted'];replay,replay_time=bench.sql_commit(f,op);assert replay==reply
 evidence=bench.assertions(f,rows,op,reply,lambda after,offset:f.fragment(after,offset))
 assert int(f.execute('SELECT count(*) FROM public.noctua_academic_records;',f.B))==41
 assert f.execute("SELECT count(*) FROM public.noctua_academic_records WHERE owner_id='"+f.A+"';",f.B)=='0'
 print(json.dumps(dict(case='H13-locks',hosted_executed=False,owner_lock_observed=observed,wait_event_type='Lock',
  blocked=blocked,other_owner_accepted_while_lock_held=True,other_owner_wall_ms=round(b_ms,3),
  atomic_rollback_verified=True,persisted_parts=len(parts),same_operation_retry=retry,confirmed_replay=replay_time,integrity=evidence),indent=2))
finally:
 if locker is not None:locker.kill();locker.communicate(timeout=5)
 b.reset()
