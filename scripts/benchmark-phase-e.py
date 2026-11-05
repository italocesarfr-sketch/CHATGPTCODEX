#!/usr/bin/env python3
"""Reproducible DB-only timing on the explicitly disposable E test database.
No HTTP/Android timing is inferred. Imports/reuses the same schema/fixture/guards.
"""
import os,importlib.util,time,json,sys
from pathlib import Path
if os.environ.get('PGDATABASE')!='noctua_phase_e_test' or os.environ.get('PGHOST') not in ('127.0.0.1','localhost','/workspace/pg-local'):
 raise SystemExit('Disposable LOCAL noctua_phase_e_test only')
# Reset only this whitelisted synthetic fixture before replaying its A-D definitions.
base=importlib.util.spec_from_file_location('base_fixture',Path(__file__).with_name('test-phase-b-postgres.py'))
b=importlib.util.module_from_spec(base);base.loader.exec_module(b);b.reset()
if sys.argv[1:] not in ([],['--baseline']):raise SystemExit('Usage: benchmark-phase-e.py [--baseline]')
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
 f.b.reset();f.b.activate();f.d.c.enable();f.d.enable();f.enable()
 rows=f.large();start=time.monotonic();op,parts,_=f.begin(rows)
 for n,p in enumerate(parts):f.stage(op,n,p)
 staging=time.monotonic()-start
 start=time.monotonic();reply=f.commit(op);first=time.monotonic()-start
 assert reply['accepted'] and reply['sequence']==1
 replays=[]
 for _ in range(3):
  start=time.monotonic();assert f.commit(op)==reply;replays.append(time.monotonic()-start)
 assert f.d.status()['academicSequence']==1
 assert int(f.execute('SELECT count(*) FROM public.noctua_academic_records;',f.A))==len(rows)
 assert f.execute('SELECT count(*) FROM public.noctua_academic_receipts;',f.A)=='1'
 print(json.dumps(dict(environment='local-disposable-postgresql-psql',graph_variant='baseline' if baseline else 'indexed',records=len(rows),parts=len(parts),staging_seconds=round(staging,3),first_commit_seconds=round(first,3),confirmed_replay_seconds=[round(t,3)for t in replays],sequence=1,unchanged_receipt=True,http_measured=False,android_measured=False),indent=2))
finally:
 f.execute((f.f.ROOT/'supabase/migrations/202611030003_phase_e_indexed_graph.sql').read_text())
 f.b.reset()
