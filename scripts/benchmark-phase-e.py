#!/usr/bin/env python3
"""Reproducible DB-only timing on the explicitly disposable E test database.
No HTTP/Android timing is inferred. Imports/reuses the same schema/fixture/guards.
"""
import os,importlib.util,time,json,sys,hashlib
from pathlib import Path
if os.environ.get('PGDATABASE')!='noctua_phase_e_test' or os.environ.get('PGHOST') not in ('127.0.0.1','localhost','/workspace/pg-local'):
 raise SystemExit('Disposable LOCAL noctua_phase_e_test only')
# Reset only this whitelisted synthetic fixture before replaying its A-D definitions.
base=importlib.util.spec_from_file_location('base_fixture',Path(__file__).with_name('test-phase-b-postgres.py'))
b=importlib.util.module_from_spec(base);base.loader.exec_module(b);b.reset()
if sys.argv[1:] not in ([],['--baseline'],['--graduated']):raise SystemExit('Usage: benchmark-phase-e.py [--baseline|--graduated]')
baseline='--baseline' in sys.argv
spec=importlib.util.spec_from_file_location('fixture',Path(__file__).with_name('test-phase-e-postgres.py'))
f=importlib.util.module_from_spec(spec);spec.loader.exec_module(f)
try:
 if baseline:
  def definition(source,name):
   start=source.index('CREATE OR REPLACE FUNCTION public.'+name+'(')
   return source[start:source.index('$$;',source.index('AS $$',start))+3]
  # Restore function bodies only on this disposable fixture, never old scope DDL.
  f.execute(definition((f.f.ROOT/'supabase/migrations/202611020003_phase_d_scope.sql').read_text(),'noctua_d_graph'))
  f.execute(definition(f.MIGRATION.read_text(),'noctua_commit_academic_e'))
 def measure(rows, label):
  f.b.reset();f.b.activate();f.d.c.enable();f.d.enable();f.enable()
  start=time.monotonic();op,parts,_=f.begin(rows)
  for n,p in enumerate(parts):f.stage(op,n,p)
  staging=time.monotonic()-start
  # Staging does not expose partial rows or advance the academic head.
  assert f.fragment()==[] and f.d.status()['academicSequence']==0
  start=time.monotonic();reply=f.commit(op);first=time.monotonic()-start
  assert reply['accepted'] and reply['sequence']==1
  replays=[]
  for _ in range(3):
   start=time.monotonic();assert f.commit(op)==reply;replays.append(time.monotonic()-start)
  assert f.d.status()['academicSequence']==1
  for table in ('records','versions'):
   assert int(f.execute('SELECT count(*) FROM public.noctua_academic_'+table+';',f.A))==len(rows)
  assert f.execute('SELECT count(*) FROM public.noctua_academic_receipts;',f.A)=='1'
  assert f.execute('SELECT count(*) FROM public.noctua_academic_batches;',f.A)=='1'
  # Full paginated payload equality, including null/zero, audit chains and IDs.
  start=time.monotonic();received=[];offset=0;pages=0
  while offset<len(rows):
   fragments=f.fragment(offset=offset);assert len(fragments)==1
   page=fragments[0];records=page['records']
   assert page['operation']==op and page['sequence']==1 and page['offset']==offset and page['total']==len(rows)
   assert 0<len(records)<=128
   received.extend(records);offset+=len(records);pages+=1
  pull=time.monotonic()-start
  expected=[dict(kind=r['kind'],key=r['key'],revision=r['base']+1,payload=r['payload']) for r in rows]
  def canonical(records):
   return sorted(json.dumps(r,ensure_ascii=False,sort_keys=True,separators=(',',':')) for r in records)
  assert canonical(expected)==canonical(received) and f.fragment(1)==[]
  # Ordinary account B has its own active fixture session but cannot see A.
  f.b.activate(f.B);f.d.c.enable(f.B);f.d.enable(f.B);f.enable(f.B)
  assert f.fragment(owner=f.B)==[]
  assert f.execute("SELECT count(*) FROM public.noctua_academic_records WHERE owner_id='"+f.A+"';",f.B)=='0'
  storage=int(f.execute("SELECT sum(pg_total_relation_size(c.oid)) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind='r' AND c.relname LIKE 'noctua_%';"))
  return dict(label=label,environment='local-disposable-postgresql-psql',graph_variant='baseline' if baseline else 'indexed',
   records=len(rows),parts=len(parts),kinds=len({r['kind'] for r in rows}),staging_seconds=round(staging,3),
   first_commit_seconds=round(first,3),confirmed_replay_seconds=[round(t,3)for t in replays],
   pull_seconds=round(pull,3),pull_pages=pages,sequence=1,unchanged_receipt=True,
   recovered_payloads_equal=True,foreign_account_denied=True,
   recovered_sha256=hashlib.sha256('\n'.join(canonical(received)).encode()).hexdigest(),
   table_bytes_including_indexes=storage,memory_measured=False,http_measured=False,android_measured=False)
 if '--graduated' in sys.argv:
  # Reuse the B/D/E fixtures. 36 means enrollments (41 records), not 36 records.
  small=f.f.classroom()+[f.f.change('students',dict(f.f.student(f'student-{n}',number=n),personId=f'person-{n}')) for n in range(1,37)]
  cases=[(small,'36-enrollments'),(f.large(0),'171-records'),(f.large(100),'671-records'),(f.large(),'5171-records')]
  assert [len(rows)for rows,_ in cases]==[41,171,671,5171]
  results=[measure(rows,label)for rows,label in cases]
  print(json.dumps(dict(environment='local-disposable-postgresql-psql',graduated=True,passed=len(results),
    real_supabase_auth=False,hosted_executed=False,timings='psql wall seconds, including connection; not pure RPC or HTTP/Android time',results=results),indent=2))
 else:
  print(json.dumps(measure(f.large(),'5171-records'),indent=2))
finally:
 f.execute((f.f.ROOT/'supabase/migrations/202611030003_phase_e_indexed_graph.sql').read_text())
 f.b.reset()
