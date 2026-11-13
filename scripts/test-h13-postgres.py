#!/usr/bin/env python3
"""H13 regression: registry equivalence, privileges, receipts and frozen transfers.
Only the existing disposable E database; never a hosted Auth session/project.
"""
import importlib.util,json,os,uuid,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
assert os.environ.get('PGDATABASE')=='noctua_phase_e_test' and os.environ.get('PGHOST')in('127.0.0.1','localhost','/workspace/pg-local')
def load(name,file):
 s=importlib.util.spec_from_file_location(name,ROOT/'scripts'/file);m=importlib.util.module_from_spec(s);s.loader.exec_module(m);return m
b=load('h13test_b','test-phase-b-postgres.py');b.reset()
f=load('h13test_e','test-phase-e-postgres.py');bench=load('h13test_bench','benchmark-h13.py')
MIGRATION=bench.H13
class H13PostgresTest(unittest.TestCase):
 def setUp(self):b.reset();f.execute(MIGRATION.read_text())
 def tearDown(self):f.execute(MIGRATION.read_text());b.reset()
 def activate(self):b.activate();f.d.c.enable();f.d.enable();f.enable()
 def shape(self):
  return f.execute("SELECT jsonb_agg(jsonb_build_object('name',p.oid::regprocedure::text,'owner',p.proowner,'definer',p.prosecdef,'volatility',p.provolatile,'config',p.proconfig,'acl',p.proacl) ORDER BY p.oid::regprocedure::text) FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public' AND p.proname LIKE 'noctua_%';")
 def policies(self):
  return f.execute("SELECT jsonb_agg(to_jsonb(p) ORDER BY tablename,policyname) FROM pg_policies p WHERE schemaname='public';")
 def inventory(self):
  return {table:f.execute('SELECT coalesce(jsonb_agg(to_jsonb(t)),\'[]\') FROM public.noctua_'+table+' t;')for table in ('academic_records','academic_versions','academic_receipts','academic_batches','e_parts','e_transfers','e_decisions')}
 def test_01_every_contract_kind_and_unknown_null_are_identical(self):
  original=json.loads((ROOT/'supabase/migrations/202611010001_academic_protocol.sql').read_text().split('$registry$')[1])
  for name,definition in original.items():
   self.assertEqual(definition,json.loads(f.execute("SELECT public.noctua_academic_definition('"+name+"');")))
  for value in ('NULL',"'unknown'","'CLASSES'","'classes '","'classes''; SELECT 1;--'","''"):
   self.assertEqual('',f.execute('SELECT public.noctua_academic_definition('+value+');'))
  self.assertEqual(15,len(original))
 def test_07_nondeterministic_caller_collation_cannot_expand_wire_kinds(self):
  f.execute("CREATE COLLATION public.noctua_h13_fixture_ci (provider=icu,locale='und-u-ks-level2',deterministic=false);")
  try:
   for definition in (bench.old_definition(),MIGRATION.read_text()):
    f.execute(definition)
    self.assertEqual('',f.execute("SELECT public.noctua_academic_definition('CLASSES' COLLATE public.noctua_h13_fixture_ci);"))
    self.assertNotEqual('',f.execute("SELECT public.noctua_academic_definition('classes' COLLATE public.noctua_h13_fixture_ci);"))
  finally:f.execute('DROP COLLATION public.noctua_h13_fixture_ci;')
 def test_02_helper_permissions_owner_rls_guards_and_settings_unchanged(self):
  f.execute(bench.old_definition());before=self.shape();policies=self.policies()
  f.execute(MIGRATION.read_text());self.assertEqual(before,self.shape());self.assertEqual(policies,self.policies())
  self.assertIn('permission denied',f.execute("SELECT public.noctua_academic_definition('students');",f.A,fails=True))
  self.assertEqual('0',f.execute("SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relname LIKE 'noctua_%' AND c.relkind='r' AND NOT c.relrowsecurity;"))
 def test_03_existing_versions_audits_and_both_receipt_outcomes_survive_migration(self):
  self.activate();f.execute(bench.old_definition());rows=f.d.rows();op,parts,_=f.begin(rows)
  for n,p in enumerate(parts):f.stage(op,n,p)
  accepted=f.commit(op);self.assertTrue(accepted['accepted'])
  rejected_op,parts,_=f.begin([rows[0]])
  for n,p in enumerate(parts):f.stage(rejected_op,n,p)
  rejected=f.commit(rejected_op);self.assertFalse(rejected['accepted'])
  before=self.inventory();f.execute(MIGRATION.read_text());self.assertEqual(before,self.inventory())
  self.assertEqual(accepted,f.commit(op));self.assertEqual(rejected,f.commit(rejected_op));self.assertEqual(before,self.inventory())
 def test_04_incomplete_staged_operation_keeps_exact_parts_then_finishes_once(self):
  self.activate();f.execute(bench.old_definition());rows=f.d.rows();op,parts,_=f.begin(rows);f.stage(op,0,parts[0])
  before=self.inventory();f.execute("SELECT public.noctua_commit_academic_e('"+op+"');",f.A,fails=True)
  f.execute(MIGRATION.read_text());self.assertEqual(before,self.inventory());self.assertEqual([],f.fragment())
  for n,p in enumerate(parts[1:],1):f.stage(op,n,p)
  reply=f.commit(op);self.assertTrue(reply['accepted']);self.assertEqual(reply,f.commit(op))
  self.assertEqual('171',f.execute('SELECT count(*) FROM public.noctua_academic_records;',f.A));self.assertEqual(1,f.d.status()['academicSequence'])
 def test_05_bad_shape_and_academic_values_are_rejected_by_same_validator(self):
  examples=[]
  for row in f.d.rows():
   key=(row['kind'],tuple(row['key']))
   if any(k==key for k,_ in examples):continue
   bad=dict(row['payload'],notAField=True);examples.append((key,dict(row,payload=bad)))
  def verdicts():
   return ['invalid academic fields' in f.execute("SELECT public.noctua_academic_validate('"+r['kind']+"',"+f.literal(r['key'])+','+f.literal(r['payload'])+');',fails=True)for _,r in examples]
  f.execute(bench.old_definition());old=verdicts();f.execute(MIGRATION.read_text());self.assertEqual(old,verdicts());self.assertTrue(all(old))
 def test_06_migration_is_idempotent_and_does_not_change_protocol_or_timeout(self):
  self.activate();before=f.d.status();budget=f.execute('SHOW statement_timeout;');rls=self.policies()
  f.execute(MIGRATION.read_text());f.execute(MIGRATION.read_text())
  self.assertEqual(before,f.d.status());self.assertEqual(budget,f.execute('SHOW statement_timeout;'));self.assertEqual(rls,self.policies())
if __name__=='__main__':unittest.main(verbosity=2)
