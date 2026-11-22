#!/usr/bin/env python3
"""Prepared institutional roster, disposable PostgreSQL only; no hosted users/entitlements."""
import concurrent.futures,importlib.util,json,os,time,unittest,uuid
from pathlib import Path
if not(os.environ.get('NOCTUA_COMMERCIAL_DISPOSABLE')=='YES' and os.environ.get('PGHOST')=='127.0.0.1' and os.environ.get('PGPORT')=='55431' and os.environ.get('PGDATABASE')=='noctua_phase_e_test'):
 raise SystemExit('Explicit disposable loopback fixture required; no hosted option')
spec=importlib.util.spec_from_file_location('commercial_provision_fixture',Path(__file__).with_name('test-commercial-bootstrap.py'))
f=importlib.util.module_from_spec(spec);spec.loader.exec_module(f)
execute,A,B=f.execute,f.A,f.B
# Only the local minimal Auth fixture lacks this genuine GoTrue column.
execute('ALTER TABLE auth.users ADD COLUMN email text;')
before=execute("SELECT proacl::text FROM pg_proc WHERE oid='public.noctua_ensure_academic_e()'::regprocedure;")
old=execute("SELECT prosrc FROM pg_proc WHERE oid='public.noctua_ensure_academic_e()'::regprocedure;")
execute((f.f.ROOT/'supabase/migrations/202611220001_institutional_provisioning.sql').read_text())
new=execute("SELECT prosrc FROM pg_proc WHERE oid='public.noctua_ensure_academic_e()'::regprocedure;")
assert new.replace(' PERFORM public.noctua_provision_commercial();\n','')==old
assert before==execute("SELECT proacl::text FROM pg_proc WHERE oid='public.noctua_ensure_academic_e()'::regprocedure;")
f.b.TABLES[:0]=['noctua_commercial_claims','noctua_commercial_roster']
class InstitutionalProvisionTest(unittest.TestCase):
 def setUp(self):
  f.b.reset();execute("DELETE FROM public.noctua_academic_test_accounts; UPDATE public.noctua_commercial_rollout SET enabled=true;")
  execute(f"UPDATE auth.users SET email=CASE id WHEN '{A}' THEN 'a@example.invalid' WHEN '{B}' THEN 'b@example.invalid' ELSE 'unconfirmed@example.invalid' END;")
 def roster(self,email='a@example.invalid',enabled=True,expires="now()+interval '1 day'"):
  return execute(f"INSERT INTO public.noctua_commercial_roster(email,institution_reference,enabled,expires_at,created_at) VALUES('{email}','institution-fixture-1',{str(enabled).lower()},{expires},now()-interval '2 days') RETURNING id;")
 def count(self,table):return execute('SELECT count(*) FROM public.'+table+';')
 def test_01_exact_confirmed_roster_initializes_E_and_keeps_test_allowlist_empty(self):
  self.roster();self.assertTrue(f.ensure()['initialized']);self.assertEqual('1',self.count('noctua_commercial_claims'))
  self.assertEqual('2|5',execute('SELECT protocol,academic_scope FROM public.noctua_sync_protocols;',A))
  self.assertEqual('institution-fixture-1',execute('SELECT institution_reference FROM public.noctua_commercial_claims;'))
  self.assertEqual('0',self.count('noctua_academic_test_accounts'))
 def test_02_no_roster_or_same_domain_does_not_grant_anyone(self):
  self.assertFalse(f.ensure()['eligible']);self.roster('someone-else@example.invalid');self.assertFalse(f.ensure()['eligible'])
  self.assertEqual('0',self.count('noctua_commercial_accounts'))
 def test_03_disabled_or_expired_roster_does_not_grant(self):
  self.roster(enabled=False);self.assertFalse(f.ensure()['eligible'])
  execute("UPDATE public.noctua_commercial_roster SET enabled=true,expires_at=now()-interval '1 day';")
  self.assertFalse(f.ensure()['eligible']);self.assertEqual('0',self.count('noctua_commercial_claims'))
 def test_04_rollout_off_still_blocks_the_new_roster(self):
  self.roster();execute('UPDATE public.noctua_commercial_rollout SET enabled=false;')
  self.assertFalse(f.ensure()['eligible']);self.assertEqual('0',self.count('noctua_commercial_accounts'))
 def test_05_unconfirmed_and_revoked_sessions_cannot_provision(self):
  self.roster();execute(f"UPDATE auth.users SET email_confirmed_at=NULL WHERE id='{A}';")
  execute('SELECT public.noctua_ensure_academic_e();',A,fails=True)
  execute(f"UPDATE auth.users SET email_confirmed_at=now() WHERE id='{A}'; DELETE FROM auth.sessions WHERE user_id='{A}';")
  execute('SELECT public.noctua_ensure_academic_e();',A,fails=True)
  self.assertEqual('0',self.count('noctua_commercial_claims'))
 def test_06_clients_cannot_read_write_or_redeem_private_helpers(self):
  self.roster()
  for table in ('noctua_commercial_roster','noctua_commercial_claims'):
   for action in (f'SELECT * FROM public.{table};',f'DELETE FROM public.{table};'):
    self.assertIn('permission denied',execute(action,A,fails=True))
  execute('SELECT public.noctua_provision_commercial();',A,fails=True)
  execute('SET ROLE anon; SELECT public.noctua_ensure_academic_e();',fails=True)
 def test_07_test_enrollment_even_false_is_never_a_commercial_authorization(self):
  self.roster();execute(f"INSERT INTO public.noctua_academic_test_accounts VALUES('{A}',false);")
  self.assertFalse(f.ensure()['eligible']);self.assertEqual('0',self.count('noctua_commercial_accounts'))
 def test_08_v1_data_blocks_and_rolls_back_tentative_entitlement(self):
  self.roster();execute(f.f.statement(f.f.bundle()),A)
  before=execute('SELECT md5(jsonb_agg(to_jsonb(r))::text) FROM public.noctua_class_versions r;')
  self.assertFalse(f.ensure()['eligible']);self.assertEqual('0',self.count('noctua_commercial_accounts'));self.assertEqual('0',self.count('noctua_commercial_claims'))
  self.assertEqual(before,execute('SELECT md5(jsonb_agg(to_jsonb(r))::text) FROM public.noctua_class_versions r;'))
 def test_09_unmanaged_v2_is_not_adopted_and_no_empty_v1_head_is_left(self):
  self.roster();execute(f"INSERT INTO public.noctua_academic_heads VALUES('{A}',0);")
  self.assertFalse(f.ensure()['eligible']);self.assertEqual('0',self.count('noctua_commercial_accounts'));self.assertEqual('0',self.count('noctua_sync_heads'))
 def test_10_existing_manual_accounts_are_not_reassigned_to_an_institution(self):
  f.grant();f.ensure();before=execute('SELECT md5(jsonb_agg(to_jsonb(r))::text) FROM public.noctua_commercial_accounts r;')
  self.roster();self.assertTrue(f.ensure()['initialized']);self.assertEqual('0',self.count('noctua_commercial_claims'))
  self.assertEqual(before,execute('SELECT md5(jsonb_agg(to_jsonb(r))::text) FROM public.noctua_commercial_accounts r;'))
 def test_11_disabled_existing_entitlement_is_not_reenabled_by_a_roster(self):
  self.roster();execute(f"INSERT INTO public.noctua_commercial_accounts(owner_id,enabled)VALUES('{A}',false);")
  self.assertFalse(f.ensure()['eligible']);self.assertEqual('f',execute('SELECT enabled FROM public.noctua_commercial_accounts;'));self.assertEqual('0',self.count('noctua_commercial_claims'))
 def test_12_two_sessions_claim_and_initialize_only_once(self):
  self.roster();sid=str(uuid.uuid4());execute(f"INSERT INTO auth.sessions VALUES('{sid}','{A}',NULL);")
  claims=dict(sub=A,role='authenticated',session_id=sid,exp=int(time.time())+3600)
  with concurrent.futures.ThreadPoolExecutor(2)as pool:
   a=pool.submit(f.ensure);b=pool.submit(f.ensure,claims=claims);self.assertEqual(a.result(),b.result())
  self.assertEqual('1',self.count('noctua_commercial_claims'));self.assertEqual('1',self.count('noctua_sync_protocols'))
 def test_13_replay_preserves_claim_and_initializer_timestamps(self):
  self.roster();first=f.ensure();before=execute('SELECT md5(jsonb_agg(to_jsonb(r))::text) FROM public.noctua_commercial_claims r;')
  self.assertEqual(first,f.ensure());self.assertEqual(before,execute('SELECT md5(jsonb_agg(to_jsonb(r))::text) FROM public.noctua_commercial_claims r;'))
 def test_14_outer_rollback_leaves_no_right_claim_or_protocol(self):
  self.roster();execute('BEGIN; SELECT public.noctua_ensure_academic_e(); ROLLBACK;',A)
  for t in ('noctua_commercial_accounts','noctua_commercial_claims','noctua_sync_protocols','noctua_sync_heads'):self.assertEqual('0',self.count(t))
  self.assertTrue(f.ensure()['initialized'])
 def test_15_v1_writer_race_preserves_the_winner_and_no_partial_claim(self):
  self.roster()
  def writer():
   try:execute(f.f.statement(f.f.bundle()),A);return True
   except AssertionError:return False
  with concurrent.futures.ThreadPoolExecutor(2)as pool:
   w=pool.submit(writer);e=pool.submit(f.ensure);won=w.result();initialized=e.result()['initialized'];self.assertNotEqual(won,initialized)
  self.assertEqual('1' if initialized else '0',self.count('noctua_commercial_claims'))
 def test_16_claim_audit_cannot_be_updated_or_deleted_even_by_ordinary_SQL_owner(self):
  self.roster();f.ensure()
  self.assertIn('immutable',execute("UPDATE public.noctua_commercial_claims SET institution_reference='changed';",fails=True))
  self.assertIn('immutable',execute('DELETE FROM public.noctua_commercial_claims;',fails=True))
 def test_17_claimed_email_cannot_be_reassigned_to_a_second_identity(self):
  self.roster();f.ensure();execute(f"UPDATE auth.users SET email='a@example.invalid' WHERE id='{B}';")
  self.assertFalse(f.ensure(B)['eligible']);self.assertEqual('1',self.count('noctua_commercial_claims'))
 def test_18_verified_auth_email_not_claim_metadata_determines_entitlement(self):
  self.roster('b@example.invalid')
  claims=dict(sub=A,role='authenticated',session_id=f.f.SESSION[A],exp=int(time.time())+3600,email='b@example.invalid',user_metadata={'institution_reference':'institution-fixture-1'})
  self.assertFalse(f.ensure(claims=claims)['eligible']);self.assertEqual('0',self.count('noctua_commercial_claims'))
if __name__=='__main__':unittest.main(verbosity=2)
