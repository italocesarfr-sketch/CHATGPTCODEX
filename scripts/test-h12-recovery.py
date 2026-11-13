#!/usr/bin/env python3
"""Offline checks only: H12 cannot inherit H05 consent or leak private reports."""
import base64,hashlib,importlib.util,json,tempfile,unittest
from pathlib import Path
from unittest.mock import patch
spec=importlib.util.spec_from_file_location('h12',Path(__file__).with_name('h05-recovery.py'));f=importlib.util.module_from_spec(spec);spec.loader.exec_module(f);f.configure('H12')
def env(t):return dict(GITHUB_ACTIONS='true',GITHUB_EVENT_NAME='workflow_dispatch',GITHUB_REF=f.auth.BRANCH,GITHUB_REPOSITORY=f.auth.REPO,RUNNER_TEMP=t,GITHUB_ENV=str(Path(t)/'env'))
def proof(mode='LOCAL'):
 d=dict(case='H12',mode=mode,status='PASSED',stage='DONE',failure_code=None,timings_ms={'RESTORE':10},local_hash='a'*64,remote_hash='a'*64,backup_hash='b'*64,historical_backup_reconstructed=mode=='HOSTED')
 d.update({k:True for k in f.H12_FLAGS});d.update({k:1 for k in f.H12_NUMBERS});d.update(records=2,backup_records=1,blocked_writes=0,lost_replies=1 if mode=='LOCAL' else 0)
 d['types']=[dict(kind=k,local_count=2 if k=='classes' else 0,remote_count=2 if k=='classes' else 0,local_hash='a'*64,remote_hash='a'*64,present=k=='classes')for k in sorted(f.KINDS)]
 return d
class H12Safety(unittest.TestCase):
 def test_requires_own_hosted_authorization(self):
  with tempfile.TemporaryDirectory()as t:
   for consent in ({},{'NOCTUA_H05_AUTHORIZED':'YES'},{'NOCTUA_HOMOLOGACAO_AUTH_TEST_AUTHORIZED':'YES'}):
    with self.assertRaises(f.Blocked):f.context(dict(env(t),NOCTUA_H12_MODE='HOSTED',**consent))
   self.assertEqual('HOSTED',f.context(dict(env(t),NOCTUA_H12_MODE='HOSTED',NOCTUA_H12_AUTHORIZED='YES')))
 def test_wrong_branch_repository_or_push_event_rejected(self):
  with tempfile.TemporaryDirectory()as t:
   for change in ({'GITHUB_REF':'refs/heads/main'},{'GITHUB_REPOSITORY':'other/repo'},{'GITHUB_EVENT_NAME':'push'}):
    with self.assertRaises(f.auth.Blocked):f.context(dict(env(t),**change))
 def test_local_prepare_uses_no_hosted_secrets(self):
  with tempfile.TemporaryDirectory()as t:
   e=dict(env(t),NOCTUA_H12_MODE='LOCAL',NOCTUA_HOMOLOGACAO_AUTH_TEST_JSON='PRIVATE_SENTINEL')
   with patch.object(f,'REPORT',Path(t)/'result'):f.prepare(e)
   data=json.loads((f.folder(e)/'input.json').read_text())
   self.assertEqual('KEEP_REMOTE',data['consentReconciliation']);self.assertNotIn('PRIVATE_SENTINEL',json.dumps(data))
   self.assertNotEqual(data['expectedId'],data['otherId']);self.assertEqual(0o600,(f.folder(e)/'input.json').stat().st_mode&0o777)
   self.assertIn('NOCTUA_H12_INPUT_FILE=',Path(e['GITHUB_ENV']).read_text());f.clean(e)
 def test_proof_has_all_checks_and_exactly_one_no_change_decision(self):
  for mode in ('LOCAL','HOSTED'):self.assertEqual(proof(mode),f.redacted_proof(json.dumps(proof(mode)).encode(),mode))
 def test_neither_missing_pause_nor_missing_account_block_can_pass(self):
  for k in f.H12_FLAGS:
   d=proof();d[k]=False
   with self.assertRaises(f.Blocked):f.redacted_proof(json.dumps(d).encode(),'LOCAL')
 def test_no_remote_academic_write_or_incomplete_review_can_pass(self):
  for change in ({'blocked_writes':1},{'decision_writes':0},{'decision_writes':2},{'remote_only_records':0},{'backup_hash':'a'*64},{'lost_replies':0},{'receipts':0}):
   with self.assertRaises(f.Blocked):f.redacted_proof(json.dumps(dict(proof(),**change)).encode(),'LOCAL')
 def test_hosted_fault_injection_is_not_approved(self):
  d=proof('HOSTED');d['lost_replies']=1
  with self.assertRaises(f.Blocked):f.redacted_proof(json.dumps(d).encode(),'HOSTED')
 def test_unknown_private_fields_and_diagnostics_are_rejected(self):
  for k in ('email','token','response','student','failure_code','stage'):
   d=proof();d[k]='PRIVATE_SENTINEL'
   with self.assertRaises(f.Blocked)as e:f.redacted_proof(json.dumps(d).encode(),'LOCAL')
   self.assertNotIn('PRIVATE_SENTINEL',str(e.exception))
 def test_absent_types_are_explicit_and_not_claimed_present(self):
  d=proof();self.assertEqual(14,len([r for r in f.redacted_proof(json.dumps(d).encode(),'LOCAL')['types']if not r['present']]))
  d['types'][0]['present']=not d['types'][0]['present']
  with self.assertRaises(f.Blocked):f.redacted_proof(json.dumps(d).encode(),'LOCAL')
 def test_per_type_mismatch_or_duplicate_kind_rejected(self):
  for action in ('hash','count','duplicate'):
   d=proof()
   if action=='hash':d['types'][0]['remote_hash']='b'*64
   elif action=='count':d['types'][0]['remote_count']=500
   else:d['types'][0]=d['types'][1]
   with self.assertRaises(f.Blocked):f.redacted_proof(json.dumps(d).encode(),'LOCAL')
 def test_h05_report_and_avd_cannot_be_reused(self):
  d=proof();d['case']='H05'
  with self.assertRaises(f.Blocked):f.redacted_proof(json.dumps(d).encode(),'LOCAL')
  e=dict(CI='true',RUNNER_ENVIRONMENT='github-hosted',NOCTUA_H12_AVD_NAME='noctua_h05_local_123_1')
  with self.assertRaises(f.Blocked):f.disposable(e,'emulator-5554',lambda *a:self.fail('No ADB allowed'))
 def test_report_transfer_remains_byte_exact(self):
  raw=json.dumps(proof()).encode();encoded=base64.encodebytes(raw)
  digest=(hashlib.sha256(raw).hexdigest()+'  files/h12-result.json\n').encode()
  self.assertEqual(raw,f.decode_device_report(encoded,digest))
  with self.assertRaises(f.Blocked):f.decode_device_report(encoded,b'0'*64+b'  files/h12-result.json')
 def test_workflow_has_independent_gate_and_local_job_no_environment(self):
  workflow=(f.ROOT/'.github/workflows/h12-restore.yml').read_text();caller=(f.ROOT/'.github/workflows/android.yml').read_text()
  local=workflow.split('  local-proof:\n')[1].split('  hosted-preflight:\n')[0]
  self.assertNotIn('secrets.',local);self.assertNotIn('environment:',local)
  self.assertNotIn('secrets: inherit',workflow);self.assertIn("inputs.mode == 'HOSTED' && inputs.authorized",workflow)
  self.assertIn('authorized: ${{ inputs.authorize_hosted_h12 == true }}',caller)
  self.assertEqual(2,workflow.count('path: build/h12/result.json'))
  self.assertIn('force-avd-creation: true',workflow);self.assertIn('--case H12',workflow)
 def test_no_private_artifacts_or_destructive_adb_commands(self):
  workflow=(f.ROOT/'.github/workflows/h12-restore.yml').read_text()
  for forbidden in ('path: app/', 'path: **','path: build/phase','logcat','adb uninstall','pm clear'):
   self.assertNotIn(forbidden,workflow)
  self.assertEqual(14,f.native_tests('LOCAL'));self.assertEqual(1,f.native_tests('HOSTED'))
if __name__=='__main__':unittest.main(verbosity=2)
