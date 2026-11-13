#!/usr/bin/env python3
"""E1, local disposable PostgreSQL ONLY; signed/SQL fixture identities, never hosted Auth."""
import os,importlib.util,json,uuid,unittest,copy,concurrent.futures
from pathlib import Path
if os.environ.get('PGDATABASE')!='noctua_phase_e_test':raise SystemExit('Disposable noctua_phase_e_test required')
spec=importlib.util.spec_from_file_location('phased',Path(__file__).with_name('test-phase-d-postgres.py'))
d=importlib.util.module_from_spec(spec);spec.loader.exec_module(d)
b=d.b;f=d.f;execute=d.execute;A=d.A;B=d.B;literal=d.literal
MIGRATION=f.ROOT/'supabase/migrations/202611030001_phase_e_paging.sql';execute(MIGRATION.read_text())
execute((f.ROOT/"supabase/migrations/202611030002_phase_e_decisions.sql").read_text())
execute((f.ROOT/"supabase/migrations/202611030003_phase_e_indexed_graph.sql").read_text())
execute((f.ROOT/"supabase/migrations/202611130001_h13_registry_cache.sql").read_text())
def enable(owner=A,seq=None):
 s=d.status(owner);return json.loads(execute(f"SELECT public.noctua_enable_academic_e({s['academicSequence'] if seq is None else seq});",owner))
def begin(rows,owner=A,op=None):
 op=op or str(uuid.uuid4());parts=[rows[i:i+128]for i in range(0,len(rows),128)]
 result=json.loads(execute(f"SELECT public.noctua_begin_academic_e('{op}',{len(rows)},{len(parts)});",owner))
 return op,parts,result
def stage(op,n,part,owner=A):return json.loads(execute(f"SELECT public.noctua_stage_academic_e('{op}',{n},{literal(part)});",owner))
def commit(op,owner=A):return json.loads(execute(f"SELECT public.noctua_commit_academic_e('{op}');",owner))
def push(rows,owner=A,op=None):
 op,parts,_=begin(rows,owner,op)
 for n,part in enumerate(parts):stage(op,n,part,owner)
 return commit(op,owner)
def fragment(after=0,offset=0,owner=A):return json.loads(execute(f'SELECT public.noctua_pull_academic_e({after},{offset});',owner))
def large(count=1000):
 s=copy.deepcopy(d.SNAP)
 for n in range(count):
  student=dict(s['students'][1],id=f'e-student-{n}',personId=f'e-person-{n}',number=37+n,joined='2026-01-01',left=None,status='Ativo')
  s['students'].append(student)
  for lesson in s['lessons'][:2]:s['marks'].append(dict(lessonId=lesson['id'],studentId=student['id'],mark=['P','F','FJ','NR'][n%4],reason='Outro' if n%4==2 else None))
  for activity in s['activities']:s['grades'].append(dict(activityId=activity['id'],studentId=student['id'],value=None if n%3==0 else 0 if n%3==1 else 5))
 return d.rows(s)
class PhaseEPostgresTest(unittest.TestCase):
 def setUp(self):b.reset();b.activate();d.c.enable();d.enable()
 def tearDown(self):b.reset()
 def test_01_explicit_scope_blocks_all_previous_writers(self):
  execute("SELECT public.noctua_begin_academic_e('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',1,1);",A,fails=True);enable()
  self.assertEqual(5,d.status()['academicScope'])
  execute(d.statement(d.rows()),A,fails=True);execute('SELECT public.noctua_enable_academic_d(0);',A,fails=True)
  execute('SELECT public.noctua_enable_academic_c(0);',A,fails=True);execute('SELECT public.noctua_pull_academic_d(0);',A,fails=True)
 def test_02_more_than_five_thousand_rows_recover_all_fifteen_types(self):
  enable();rows=large();self.assertEqual(5171,len(rows));op,parts,_=begin(rows)
  for n,part in enumerate(parts):
   stage(op,n,part)
   self.assertEqual([],fragment())
  self.assertTrue(commit(op)['accepted']);received=[];offset=0
  while True:
   page=fragment(offset=offset)[0];self.assertEqual(1,page['sequence']);self.assertEqual(len(rows),page['total']);self.assertEqual(offset,page['offset'])
   self.assertLessEqual(len(page['records']),128);received+=page['records'];offset+=len(page['records'])
   if offset==page['total']:break
  self.assertEqual({(r['kind'],json.dumps(r['key'],sort_keys=True))for r in rows},{(r['kind'],json.dumps(r['key'],sort_keys=True))for r in received})
  self.assertEqual(15,len({r['kind'] for r in received}));self.assertEqual([],fragment(1))
 def test_03_partial_transfer_cannot_commit_or_expose_academic_rows(self):
  enable();op,parts,_=begin(d.rows());stage(op,0,parts[0]);execute(f"SELECT public.noctua_commit_academic_e('{op}');",A,fails=True)
  self.assertEqual([],fragment());self.assertEqual('0',execute('SELECT count(*) FROM public.noctua_academic_records;',A))
 def test_04_replay_same_parts_and_receipt_is_idempotent(self):
  enable();op,parts,_=begin(d.rows())
  for n,part in enumerate(parts):self.assertEqual(stage(op,n,part),stage(op,n,part))
  first=commit(op);self.assertEqual(first,commit(op));self.assertEqual(first,push(d.rows(),op=op));self.assertEqual(1,fragment()[0]['sequence'])
 def test_05_part_cannot_change_under_same_operation(self):
  enable();op,parts,_=begin(d.rows());stage(op,0,parts[0]);bad=copy.deepcopy(parts[0]);bad[0]['payload']['name']='Changed'
  execute(f"SELECT public.noctua_stage_academic_e('{op}',0,{literal(bad)});",A,fails=True);self.assertEqual([],fragment())
 def test_06_manifest_identity_cannot_be_reused(self):
  enable();op,_,_=begin(d.rows());execute(f"SELECT public.noctua_begin_academic_e('{op}',1,1);",A,fails=True)
 def test_07_other_account_cannot_read_or_write_parts_or_known_ids(self):
  enable();op,parts,_=begin(d.rows());stage(op,0,parts[0]);b.activate(B);d.c.enable(B);d.enable(B);enable(B)
  for table in ['noctua_e_parts','noctua_e_transfers']+b.TABLES:self.assertEqual('0',execute(f"SELECT count(*) FROM public.{table} WHERE owner_id='{A}';",B))
  execute(f"SELECT public.noctua_stage_academic_e('{op}',1,{literal(parts[1])});",B,fails=True)
  execute(f"SELECT public.noctua_commit_academic_e('{op}');",B,fails=True);self.assertEqual([],fragment(owner=B))
 def test_08_revoked_unconfirmed_and_anonymous_sessions_are_rejected(self):
  enable();execute('SELECT public.noctua_pull_academic_e(0,0);',f.UNCONFIRMED,fails=True)
  execute("DELETE FROM auth.sessions WHERE user_id='"+A+"';");execute('SELECT public.noctua_pull_academic_e(0,0);',A,fails=True)
  execute('SET ROLE anon; SELECT public.noctua_pull_academic_e(0,0);',fails=True)
 def test_09_private_core_and_direct_writes_are_denied(self):
  enable()
  for call in ['noctua_e_principal()',f"noctua_e_write('{uuid.uuid4()}','[]')",f"noctua_e_guarded_write('{uuid.uuid4()}','[]')"]:self.assertIn('permission denied',execute('SELECT public.'+call+';',A,fails=True))
  for table in ['noctua_e_parts','noctua_e_transfers']:execute('DELETE FROM public.'+table+';',A,fails=True)
 def test_10_invalid_graph_rolls_back_complete_large_transfer(self):
  enable();rows=large(100);rows=[r for r in rows if r['kind']!='relocations'];op,parts,_=begin(rows)
  for n,part in enumerate(parts):stage(op,n,part)
  execute(f"SELECT public.noctua_commit_academic_e('{op}');",A,fails=True);self.assertEqual([],fragment())
  self.assertEqual('0',execute('SELECT count(*) FROM public.noctua_academic_receipts;',A))
 def test_11_stale_revisions_preserve_negative_receipt_and_remote_value(self):
  enable();push(d.rows());r=copy.deepcopy(d.SNAP['lessonRecords'][0]);r['description']='First';a=push([f.change('lesson_records',r,1)]);r['description']='Second'
  op=str(uuid.uuid4());b=push([f.change('lesson_records',r,1)],op=op);self.assertFalse(b['accepted']);self.assertEqual(b,commit(op));self.assertEqual(2,a['sequence'])
 def test_12_invalid_offsets_and_limits_fail_closed(self):
  enable();push(d.rows())
  for offset in [-1,171,99999]:execute(f'SELECT public.noctua_pull_academic_e(0,{offset});',A,fails=True)
  for total in [0,16385]:execute(f"SELECT public.noctua_begin_academic_e('{uuid.uuid4()}',{total},1);",A,fails=True)
 def test_13_abandoned_transfer_quota_preserves_all_original_parts(self):
  enable()
  for n in range(4):begin(d.rows())
  execute(f"SELECT public.noctua_begin_academic_e('{uuid.uuid4()}',1,1);",A,fails=True);self.assertEqual('4',execute('SELECT count(*) FROM public.noctua_e_transfers;',A))
 def test_14_audit_immutability_survives_staging(self):
  enable();push(d.rows());r=copy.deepcopy(d.SNAP['relocations'][0]);r['corrections'][0]['previousTarget']='Forged';op,parts,_=begin([f.change('relocations',r,1)]);stage(op,0,parts[0]);execute(f"SELECT public.noctua_commit_academic_e('{op}');",A,fails=True)
  self.assertEqual([],fragment(1))
 def test_15_scope_transition_rejects_stale_cursor(self):
  d.push(d.rows());execute('SELECT public.noctua_enable_academic_e(0);',A,fails=True);self.assertEqual(4,d.status()['academicScope'])
 def test_16_index_matches_live_and_historical_keys_and_remains_private(self):
  enable();push(d.rows())
  self.assertEqual('t',execute(f"WITH snapshot AS (SELECT public.noctua_d_projection('{A}') rows) SELECT bool_and(public.noctua_d_get(rows,r->>'kind',r->'key') IS NOT DISTINCT FROM public.noctua_d_get(public.noctua_d_index(rows),r->>'kind',r->'key')) FROM snapshot,LATERAL jsonb_array_elements(rows) r;"))
  duplicate=[dict(kind='students',key=dict(id='Á\\"'),payload=dict(name='A'))]*2
  execute(f"SELECT public.noctua_d_index({literal(duplicate)});",fails=True)
  execute("SELECT public.noctua_d_index('[]');",A,fails=True)
if __name__=='__main__':unittest.main(verbosity=2)
