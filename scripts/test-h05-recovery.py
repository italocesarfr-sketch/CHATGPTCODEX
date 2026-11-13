#!/usr/bin/env python3
"""Offline H05 safety tests. No ADB, credentials, Gradle or hosted HTTP calls."""
import base64,hashlib,importlib.util,json,os,tempfile,unittest
from pathlib import Path
from unittest.mock import patch
spec=importlib.util.spec_from_file_location('h05',Path(__file__).with_name('h05-recovery.py'));f=importlib.util.module_from_spec(spec);spec.loader.exec_module(f)

def env(temp):return dict(GITHUB_ACTIONS='true',GITHUB_EVENT_NAME='workflow_dispatch',GITHUB_REF=f.auth.BRANCH,GITHUB_REPOSITORY=f.auth.REPO,RUNNER_TEMP=temp,GITHUB_ENV=str(Path(temp)/'env'))
def proof():
 d=dict(case='H05',mode='LOCAL',status='PASSED',stage='DONE',failure_code=None,timings_ms={'AUTH':1},local_hash='a'*64,remote_hash='a'*64)
 d.update({k:True for k in f.FLAGS});d.update({k:1 for k in f.NUMBERS});d['blocked_writes']=0
 d['types']=[dict(kind=k,local_count=1 if k=='classes' else 0,remote_count=1 if k=='classes' else 0,local_hash='a'*64,remote_hash='a'*64,present=k=='classes')for k in sorted(f.KINDS)]
 return d
class H05Safety(unittest.TestCase):
 def test_hosted_requires_independent_authorization(self):
  with tempfile.TemporaryDirectory()as t:
   e=dict(env(t),NOCTUA_H05_MODE='HOSTED',NOCTUA_HOMOLOGACAO_AUTH_TEST_AUTHORIZED='YES')
   with self.assertRaises(f.Blocked):f.context(e)
   e['NOCTUA_H05_AUTHORIZED']='YES';self.assertEqual('HOSTED',f.context(e))
 def test_wrong_branch_and_automatic_events_cannot_prepare(self):
  with tempfile.TemporaryDirectory()as t:
   for change in [dict(GITHUB_REF='refs/heads/main'),dict(GITHUB_EVENT_NAME='push'),dict(GITHUB_REPOSITORY='other/repo')]:
    with self.assertRaises(f.auth.Blocked):f.context(dict(env(t),**change))
 def test_local_mode_ignores_private_environment_secret(self):
  with tempfile.TemporaryDirectory()as t:
   e=dict(env(t),NOCTUA_H05_MODE='LOCAL',NOCTUA_HOMOLOGACAO_AUTH_TEST_JSON='PRIVATE_SENTINEL')
   with patch.object(f,'REPORT',Path(t)/'public.json'):f.prepare(e)
   p=f.folder(e)/'input.json';self.assertEqual(0o600,p.stat().st_mode&0o777)
   self.assertNotIn('PRIVATE_SENTINEL',p.read_text());self.assertNotIn('Fixture-Password',Path(e['GITHUB_ENV']).read_text())
   f.clean(e);self.assertFalse(p.exists())
 def test_preflight_only_checks_format_and_writes_no_private_account_file(self):
  with tempfile.TemporaryDirectory()as t:
   fake=dict(accounts=[dict(email='a@example.invalid',password='Fixture-A-only',expectedId='11111111-1111-4111-8111-111111111111'),dict(email='b@example.invalid',password='Fixture-B-only',expectedId='22222222-2222-4222-8222-222222222222')],fixtureClassId='fixture',allowZeroRowPermissionProbe=False)
   e=dict(env(t),NOCTUA_H05_MODE='PREFLIGHT',NOCTUA_HOMOLOGACAO_PUBLISHABLE_KEY='sb_publishable_'+'H'*24,NOCTUA_HOMOLOGACAO_AUTH_TEST_JSON=json.dumps(fake))
   with patch.object(f,'REPORT',Path(t)/'public.json'):f.prepare(e)
   self.assertFalse(f.folder(e).exists());self.assertNotIn('Fixture-A-only',(Path(t)/'public.json').read_text())
 def test_valid_proof_does_not_include_id_email_or_payload(self):
  self.assertEqual(proof(),f.redacted_proof(json.dumps(proof()).encode(),'LOCAL'))
 def test_unknown_report_fields_and_private_messages_are_rejected(self):
  for k,v in [('email','PRIVATE_SENTINEL'),('message','PRIVATE_SENTINEL'),('present',True),('stage','PRIVATE_SENTINEL'),('failure_code','PRIVATE_SENTINEL')]:
   d=proof();d[k]=v
   with self.assertRaises(f.Blocked)as e:f.redacted_proof(json.dumps(d).encode(),'LOCAL')
   self.assertNotIn('PRIVATE_SENTINEL',str(e.exception))
 def test_missing_checks_dirty_outbox_and_nonzero_write_attempt_never_approve(self):
  for k,v in [('queues_empty',False),('ids_and_payloads_equal',False),('remote_unchanged',False),('blocked_writes',1),('private_input_removed',False),('reopened_equal',False)]:
   d=proof();d[k]=v
   with self.assertRaises(f.Blocked):f.redacted_proof(json.dumps(d).encode(),'LOCAL')
 def test_type_counts_hashes_and_coverage_must_be_complete(self):
  for change in ('missing','hash','count','unknown'):
   d=proof()
   if change=='missing':d['types'].pop()
   if change=='hash':d['types'][0]['local_hash']='b'*64
   if change=='count':d['types'][0]['local_count']=999
   if change=='unknown':d['types'][0]['kind']='PRIVATE_SENTINEL'
   with self.assertRaises(f.Blocked):f.redacted_proof(json.dumps(d).encode(),'LOCAL')
 def test_report_mode_is_bound_to_the_selected_runner(self):
  with self.assertRaises(f.Blocked):f.redacted_proof(json.dumps(proof()).encode(),'HOSTED')
 def test_device_report_transfer_is_byte_exact_and_rejects_corruption(self):
  raw=json.dumps(proof()).encode();encoded=base64.encodebytes(raw)
  digest=(hashlib.sha256(raw).hexdigest()+'  files/h05-result.json\n').encode()
  self.assertEqual(raw,f.decode_device_report(encoded,digest))
  for b,d in [(encoded+b'PRIVATE_SENTINEL',digest),(encoded,b'0'*64+b'  files/h05-result.json'),(encoded,b'PRIVATE_SENTINEL')]:
   with self.assertRaises(f.Blocked)as e:f.decode_device_report(b,d)
   self.assertNotIn('PRIVATE_SENTINEL',str(e.exception))
 def test_report_shape_never_copies_values_or_unknown_field_names(self):
  j=dict(proof(),PRIVATE_SENTINEL='PRIVATE_SENTINEL');j['mode']='PRIVATE_SENTINEL'
  shape=f.report_shape(json.dumps(j).encode())
  self.assertEqual(1,shape['unknown_fields']);self.assertEqual('string',shape['known_field_types']['mode'])
  self.assertNotIn('PRIVATE_SENTINEL',json.dumps(shape))
 def test_disposable_guard_rejects_owner_devices_before_adb(self):
  with tempfile.TemporaryDirectory()as t:
   def no_adb(*args):self.fail('ADB must not be called')
   with self.assertRaises(f.Blocked):f.disposable(env(t),'emulator-5554',no_adb)
 def test_only_fresh_named_ci_avd_is_accepted(self):
  e=dict(RUNNER_ENVIRONMENT='github-hosted',CI='true',NOCTUA_H05_AVD_NAME='noctua_h05_local_123_1')
  def adb(*args):
   if args==('devices',):return b'List of devices attached\nemulator-5554\tdevice\n'
   if 'ro.kernel.qemu'in args:return b'1\n'
   return b'noctua_h05_local_123_1\nOK\n'
  f.disposable(e,'emulator-5554',adb)
  with self.assertRaises(f.Blocked):f.disposable(e,'physical-device',adb)
  with self.assertRaises(f.Blocked):f.disposable(dict(e,NOCTUA_H05_AVD_NAME='owner-existing'),'emulator-5554',adb)
 def test_workflow_is_independent_and_local_job_has_no_secrets(self):
  caller=(f.ROOT/'.github/workflows/android.yml').read_text();s=(f.ROOT/'.github/workflows/h05-recovery.yml').read_text()
  self.assertIn('authorized: ${{ inputs.authorize_hosted_h05 == true }}',caller)
  job=s.split('  local-proof:\n',1)[1].split('  hosted-preflight:\n',1)[0]
  self.assertNotIn('${{ secrets.',job);self.assertNotIn('environment:',job)
  self.assertIn('environment: noctua-homologacao-h01-h04',s)
  self.assertNotIn('secrets: inherit',s)
  self.assertEqual(2,s.count('path: build/h05/result.json'))
  self.assertIn("inputs.mode == 'HOSTED' && inputs.authorized",s)
if __name__=='__main__':unittest.main(verbosity=2)
