#!/usr/bin/env python3
"""Single-grade edits in the SAME 5,171-record account; no full-database replacement.
LOCAL fixture only, 8s per statement, alternating registry helper before/after.
"""
import importlib.util,json,os,copy
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
assert os.environ.get('PGDATABASE')=='noctua_phase_e_test' and os.environ.get('PGHOST')in('127.0.0.1','localhost','/workspace/pg-local')
def load(n,file):
 s=importlib.util.spec_from_file_location(n,ROOT/'scripts'/file);m=importlib.util.module_from_spec(s);s.loader.exec_module(m);return m
b=load('delta_b','test-phase-b-postgres.py');b.reset();f=load('delta_e','test-phase-e-postgres.py');bench=load('delta_bench','benchmark-h13.py')
try:
 b.activate();f.d.c.enable();f.d.enable();f.enable();rows=f.large();op,parts,_=f.begin(rows)
 for n,p in enumerate(parts):f.stage(op,n,p)
 reply,_=bench.sql_commit(f,op);assert reply and reply['accepted']
 source=next(r for r in rows if r['kind']=='grades');payload=source['payload'];revision=1;sequence=1;samples=[]
 for trial in range(5):
  for variant in (('before','after')if trial%2==0 else('after','before')):
   f.execute(bench.old_definition()if variant=='before'else bench.H13.read_text())
   payload=dict(payload,value=1 if payload['value']==0 else 0)
   operation,parts,_=f.begin([dict(source,base=revision,payload=payload)])
   for n,p in enumerate(parts):f.stage(operation,n,p)
   f.execute('SELECT pg_stat_reset();');reply,measurement=bench.sql_commit(f,operation)
   assert reply and reply['accepted'] and reply['sequence']==sequence+1
   profile=bench.function_profile(f);replay,replay_time=bench.sql_commit(f,operation);assert replay==reply
   revision+=1;sequence+=1
   page=f.fragment(sequence-1);assert len(page)==1 and page[0]['total']==1
   version=page[0]['records'][0];assert version==dict(kind=source['kind'],key=source['key'],revision=revision,payload=payload)
   assert int(f.execute('SELECT count(*) FROM public.noctua_academic_records;',f.A))==5171
   assert int(f.execute('SELECT count(*) FROM public.noctua_academic_receipts;',f.A))==sequence
   samples.append(dict(trial=trial+1,variant=variant,commit=measurement,replay=replay_time,function_profile=profile,records=5171,changes=1,sequence=sequence,receipt_count=sequence))
 f.execute(bench.H13.read_text())
 actual=json.loads(f.execute("SELECT jsonb_agg(jsonb_build_object('kind',kind,'key',key,'revision',revision,'payload',payload)) FROM public.noctua_academic_records WHERE owner_id='"+f.A+"';",f.A))
 expected=[dict(kind=r['kind'],key=r['key'],revision=revision if r['kind']==source['kind']and r['key']==source['key']else 1,payload=payload if r['kind']==source['kind']and r['key']==source['key']else r['payload'])for r in rows]
 assert bench.canonical(actual)==bench.canonical(expected)
 stats={v:bench.summary([s['commit']['server_execution_ms']for s in samples if s['variant']==v])for v in ('before','after')}
 print(json.dumps(dict(case='H13-delta',hosted_executed=False,statement_timeout='8s',initial_records=5171,changes_per_operation=1,initial_migration='h13',
  statistics=stats,samples=samples,all_other_records_unchanged=True,final_sha256=bench.digest(actual),final_sequence=sequence,receipt_count=sequence),indent=2))
finally:f.execute(bench.H13.read_text());b.reset()
