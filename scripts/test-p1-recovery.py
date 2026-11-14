#!/usr/bin/env python3
"""Offline guards and strict evidence contract; never contact Supabase or ADB."""
import importlib.util,json,os,tempfile,unittest
from pathlib import Path
from unittest.mock import patch
spec=importlib.util.spec_from_file_location('p1',Path(__file__).with_name('p1-recovery.py'));p=importlib.util.module_from_spec(spec);spec.loader.exec_module(p)
ROOT=Path(__file__).resolve().parents[1]
def env(mode='LOCAL'):
 return dict(GITHUB_ACTIONS='true',CI='true',GITHUB_EVENT_NAME='workflow_dispatch',GITHUB_REF=p.auth.BRANCH,GITHUB_REPOSITORY=p.auth.REPO,NOCTUA_P1_MODE=mode,GITHUB_RUN_ID='123',GITHUB_RUN_ATTEMPT='1',RUNNER_ENVIRONMENT='github-hosted')
def report():
 j={k:True for k in p.FLAGS};j.update({k:0 for k in p.NUMBERS})
 j.update(case='P1',mode='LOCAL',status='PASSED',stage='DONE',failure_code=None,a_head=2,b_head=1,a_records=171,b_records=6,login_calls=3,logout_calls=3,read_calls=40,total_ms=12000,timings_ms={'LOGIN_A':150},a_hash='a'*64,b_hash='b'*64)
 j['eligibility']={side:dict(protocol='V2',scope='E',test_allowed=True,ready=True)for side in ('A','B')}
 for side,count in (('a',171),('b',6)):
  j['types_'+side]=[dict(kind=k,local_count=count if i==0 else 0,remote_count=count if i==0 else 0,local_hash='a'*64,remote_hash='a'*64,present=i==0)for i,k in enumerate(sorted(p.h05.KINDS))]
 return j
class Guards(unittest.TestCase):
 def test_hosted_requires_independent_authorization(self):
  with self.assertRaises(p.Blocked):p.context(env('HOSTED'))
  e=env('HOSTED');e['NOCTUA_P1_AUTHORIZED']='YES';self.assertEqual(p.context(e),'HOSTED')
 def test_preflight_is_offline_and_not_execution(self):self.assertEqual(p.context(env('PREFLIGHT')),'PREFLIGHT')
 def test_scope_wrong_branch_rejected(self):
  e=env();e['GITHUB_REF']='refs/heads/main'
  with self.assertRaises(p.auth.Blocked):p.context(e)
 def test_local_ignores_all_real_secrets(self):
  with tempfile.TemporaryDirectory()as tmp:
   e=env();e.update(RUNNER_TEMP=tmp,GITHUB_ENV=tmp+'/github-env',NOCTUA_HOMOLOGACAO_AUTH_TEST_JSON='DO-NOT-CONSUME',NOCTUA_HOMOLOGACAO_PUBLISHABLE_KEY='DO-NOT-CONSUME')
   with patch.object(p,'REPORT',Path(tmp)/'public.json'),patch.object(p.auth,'validate',side_effect=AssertionError('secret consumed')):p.prepare(e)
   private=p.folder(e)/'input.json';self.assertEqual(private.stat().st_mode&0o777,0o600)
   data=json.loads(private.read_text());self.assertEqual(len(data['accounts']),2);self.assertEqual(data['consentP1'],'YES');self.assertNotIn('DO-NOT-CONSUME',private.read_text());p.clean(e)
 def test_eligibility_cannot_publish_uncontrolled_values(self):
  for field in ('protocol','scope','test_allowed','ready'):
   j=report();j['eligibility']['B'][field]='private value'
   with self.assertRaises(p.Blocked):p.proof(json.dumps(j),'LOCAL')
 def test_failed_scope_observation_is_never_approval(self):
  j=report();j.update(status='FAILED',stage='RECOVER_B',failure_code='SCOPE')
  j['eligibility']['B'].update(scope='D',ready=False)
  self.assertEqual(p.proof(json.dumps(j),'LOCAL')['status'],'FAILED')
  j.update(status='PASSED',stage='DONE',failure_code=None)
  with self.assertRaises(p.Blocked):p.proof(json.dumps(j),'LOCAL')
 def test_all_three_scope_predicates_must_agree_with_ready(self):
  for change in (dict(protocol='V1'),dict(scope='D'),dict(test_allowed=False)):
   j=report();j['eligibility']['B'].update(change)
   with self.assertRaises(p.Blocked):p.proof(json.dumps(j),'LOCAL')
 def test_original_hosted_failure_remains_readable_without_invented_status(self):
  j=json.loads((ROOT/'docs/evidence/P1_HOSTED_25.json').read_text())['proofs'][0]
  self.assertNotIn('eligibility',j)
  self.assertEqual(p.proof(json.dumps(j),'HOSTED')['failure_code'],'SCOPE')
 def test_known_good_complete_proof(self):self.assertEqual(p.proof(json.dumps(report()),'LOCAL')['status'],'PASSED')
 def test_unknown_fields_cannot_leak(self):
  j=report();j['email']='private'
  with self.assertRaises(p.Blocked):p.proof(json.dumps(j),'LOCAL')
 def test_unknown_stage_cannot_leak(self):
  j=report();j['failure_code']='private server text'
  with self.assertRaises(p.Blocked):p.proof(json.dumps(j),'LOCAL')
 def test_all_success_flags_mandatory(self):
  for k in p.FLAGS:
   j=report();del j[k]
   with self.assertRaises(p.Blocked):p.proof(json.dumps(j),'LOCAL')
 def test_mutation_attempt_blocks_approval(self):
  j=report();j['blocked_writes']=1
  with self.assertRaises(p.Blocked):p.proof(json.dumps(j),'LOCAL')
 def test_mismatched_hash_blocks_approval(self):
  j=report();j['types_a'][0]['remote_hash']='f'*64
  with self.assertRaises(p.Blocked):p.proof(json.dumps(j),'LOCAL')
 def test_empty_b_is_supported_without_weakening_a(self):
  j=report();j.update(b_records=0,b_head=0)
  for r in j['types_b']:r.update(local_count=0,remote_count=0,present=False)
  self.assertEqual(p.proof(json.dumps(j),'LOCAL')['b_records'],0)
  j['a_records']=0
  with self.assertRaises(p.Blocked):p.proof(json.dumps(j),'LOCAL')
 def test_two_avds_require_owned_disposable_runner(self):
  self.assertEqual(p.identities(env()),['noctua_p1_local_123_1_first','noctua_p1_local_123_1_second'])
  e=env();e['RUNNER_ENVIRONMENT']='self-hosted'
  with self.assertRaises(p.Blocked):p.identities(e)
 def test_probe_uses_ui_and_never_manually_recovers(self):
  source=(ROOT/'app/src/androidTest/java/br/com/diariodeclasse/P1RecoveryProbeTest.kt').read_text()
  for bad in ('.activate(','.run(','.request(','.recoverAuthorizedE(','.consentE(','academic-retry'):self.assertNotIn(bad,source)
  for tag in ('entry-email','entry-password','entry-enter','auth-logout'):self.assertIn(tag,source)
 def test_private_transport_reuses_all_h05_write_guards(self):
  source=(ROOT/'app/src/androidTest/java/br/com/diariodeclasse/P1RecoverySupport.kt').read_text()
  self.assertIn('H05ReadOnlyRemote(',source);self.assertIn('c.requestMethod="GET"',source);self.assertIn('c.instanceFollowRedirects=false',source)
 def test_workflow_authorization_default_and_local_no_secrets(self):
  import yaml
  w=yaml.safe_load((ROOT/'.github/workflows/p1-recovery.yml').read_text());local=w['jobs']['local-proof']
  self.assertNotIn('environment',local);self.assertNotIn('secrets.',json.dumps(local))
  self.assertIn("inputs.authorized",json.dumps(w['jobs']['hosted-preflight']))
if __name__=='__main__':unittest.main()
