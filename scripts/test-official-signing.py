#!/usr/bin/env python3
"""Synthetic tests only: no network, permanent key or APK signing."""
import base64,copy,importlib.util,json,os,re,tempfile,unittest
from pathlib import Path
from unittest.mock import patch
import yaml
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('signing',ROOT/'scripts/official-signing.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
def environment():
 return dict(name='release',can_admins_bypass=False,deployment_branch_policy=dict(protected_branches=False,custom_branch_policies=True),protection_rules=[dict(type='required_reviewers',prevent_self_review=True,reviewers=[dict(type='User',reviewer=dict(id=1))])])
def branches():return dict(total_count=2,branch_policies=[dict(name='main',type='branch'),dict(name=m.BRANCH,type='branch')])
def env(mode='PREFLIGHT'):
 return dict(GITHUB_REPOSITORY=m.REPO,GITHUB_REF='refs/heads/'+m.BRANCH,GITHUB_EVENT_NAME='workflow_dispatch',RUNNER_ENVIRONMENT='github-hosted',GITHUB_RUN_ATTEMPT='1',GITHUB_WORKFLOW_REF=m.REPO+'/.github/workflows/'+m.WORKFLOW+'@refs/heads/'+m.BRANCH,GITHUB_SHA='a'*40,NOCTUA_REVIEWED_SHA='a'*40,NOCTUA_SIGNING_MODE=mode,NOCTUA_AUTHORIZE_PREFLIGHT='true',NOCTUA_AUTHORIZE_CANDIDATE='true'if mode=='CANDIDATE'else'false')
def proof():
 names=['OFFLINE_GUARD','SEED_C40','BOUND_C40','CHECK_MIGRATED','CHECK_BOUND_CONFLICT','RESTORE','REOPEN','DELETE_HISTORY','REOPEN_HISTORY','DELETE_STUDENT','REOPEN_STUDENT']
 return dict(source_sha='a'*40,signing='PERMANENT',passed=True,phases_passed=11,official_signature_tested=True,original_re_signed_copy=False,stages=[dict(stage=n,passed=True,native=dict(expected_tests=1,ok_marker=True,process_crashed=False,instrumentation_failed=False,failures=False,exception_types=[]))for n in names])
class Safety(unittest.TestCase):
 def test_ready_policy_requires_manual_owner_authorization_not_a_second_reviewer(self):
  r=m.policy(environment(),branches());self.assertTrue(r['manual_authorization_required']);self.assertFalse(r['independent_review_required_by_workflow'])
 def test_current_unprotected_environment_is_blocked(self):
  with self.assertRaisesRegex(m.Blocked,'ADMIN_BYPASS'):m.policy(dict(name='release',can_admins_bypass=True,protection_rules=[],deployment_branch_policy=None),{})
 def test_missing_bypass_metadata_is_not_assumed_safe(self):
  e=environment();del e['can_admins_bypass']
  with self.assertRaises(m.Blocked):m.policy(e,branches())
 def test_selected_branches_are_mandatory(self):
  for p in (None,dict(protected_branches=True,custom_branch_policies=False)):
   e=environment();e['deployment_branch_policy']=p
   with self.assertRaises(m.Blocked):m.policy(e,branches())
 def test_wildcards_tags_unknown_branches_and_missing_branch_are_refused(self):
  for rules in ([dict(name='*',type='branch')],[dict(name=m.BRANCH,type='tag')],[dict(name='main',type='branch')],[dict(name=m.BRANCH,type='branch'),dict(name='other',type='branch')]):
   with self.subTest(rules=rules),self.assertRaises(m.Blocked):m.policy(environment(),dict(total_count=len(rules),branch_policies=rules))
 def test_truncated_branch_policy_page_is_blocked(self):
  b=branches();b['total_count']=101
  with self.assertRaises(m.Blocked):m.policy(environment(),b)
 def test_absent_reviewers_and_self_review_settings_do_not_block_owner(self):
  for rules in ([],[dict(type='required_reviewers',prevent_self_review=False,reviewers=[])],[dict(type='required_reviewers')]):
   e=environment();e['protection_rules']=rules
   self.assertTrue(m.policy(e,branches())['manual_authorization_required'])
 def test_distinct_preflight_and_candidate_authorizations(self):
  self.assertEqual('PREFLIGHT',m.context(env()));self.assertEqual('CANDIDATE',m.context(env('CANDIDATE')))
  for mode,field,value in [('PREFLIGHT','NOCTUA_AUTHORIZE_CANDIDATE','true'),('CANDIDATE','NOCTUA_AUTHORIZE_CANDIDATE','false'),('PREFLIGHT','NOCTUA_AUTHORIZE_PREFLIGHT','false')]:
   e=env(mode);e[field]=value
   with self.assertRaises(m.Blocked):m.context(e)
 def test_context_blocks_fork_main_push_selfhost_and_rerun(self):
  for k,v in [('GITHUB_REPOSITORY','other/repo'),('GITHUB_REF','refs/heads/main'),('GITHUB_EVENT_NAME','push'),('GITHUB_EVENT_NAME','pull_request'),('GITHUB_REF','refs/tags/v0.22.0'),('RUNNER_ENVIRONMENT','self-hosted'),('GITHUB_RUN_ATTEMPT','2'),('GITHUB_WORKFLOW_REF','other')]:
   e=env();e[k]=v
   with self.subTest(k=k),self.assertRaises(m.Blocked):m.context(e)
 def test_reviewed_sha_cannot_follow_a_moving_branch(self):
  e=env();e['NOCTUA_REVIEWED_SHA']='b'*40
  with self.assertRaisesRegex(m.Blocked,'REVIEWED_SHA'):m.context(e)
 def test_secret_absence_and_malformed_base64_do_not_echo_input(self):
  for e in ({},dict(STORE_B64='PRIVATE-NOT-VALID',STORE_PASS='secret',KEY_ALIAS='private-alias',KEY_PASS='secret',CERT_SHA=m.CERT)):
   with self.assertRaises(m.Blocked)as caught:m.cert_env(e)
   self.assertNotIn('PRIVATE',str(caught.exception));self.assertNotIn('secret',str(caught.exception))
 def test_expected_cert_is_pinned_independently_of_environment(self):
  e=dict(STORE_B64=base64.b64encode(b'0synthetic').decode(),STORE_PASS='test',KEY_ALIAS='test',KEY_PASS='test',CERT_SHA='0'*64)
  with self.assertRaisesRegex(m.Blocked,'EXPECTED_CERTIFICATE'):m.cert_env(e)
 def test_pkcs12_size_and_shape_are_bounded(self):
  e=dict(STORE_B64=base64.b64encode(b'not-pkcs12').decode(),STORE_PASS='test',KEY_ALIAS='test',KEY_PASS='test',CERT_SHA=m.CERT)
  with self.assertRaisesRegex(m.Blocked,'PKCS12_REQUIRED'):m.cert_env(e)
  e['STORE_B64']='A'*(4*1024*1024+1)
  with self.assertRaisesRegex(m.Blocked,'KEYSTORE_SIZE'):m.cert_env(e)
 def test_child_processes_only_receive_credentials_they_need(self):
  with patch.dict(os.environ,dict(STORE_B64='secret',KEY_PASS='secret',GH_TOKEN='github-secret',DIARY_RELEASE_STORE_PASSWORD='secret'),clear=True):
   self.assertEqual({},m.child_env());self.assertEqual({'GH_TOKEN':'github-secret'},m.child_env(github_token=True))
 def test_unreadable_environment_does_not_fall_back_to_input_flags(self):
  with patch.dict(os.environ,env(),clear=True),patch.object(m,'github',side_effect=m.Blocked('ENVIRONMENT_POLICY_UNREADABLE')):
   with self.assertRaisesRegex(m.Blocked,'ENVIRONMENT_POLICY_UNREADABLE'):m.protect()
 def test_valid_proof_has_eleven_completed_permanent_signature_stages(self):self.assertTrue(m.approved_proof(proof(),'a'*40))
 def test_disposable_or_stale_proof_is_not_permanent_approval(self):
  for changes in (dict(signing='DISPOSABLE_ONLY'),dict(source_sha='b'*40),dict(official_signature_tested=False),dict(original_re_signed_copy=True)):
   p=proof();p.update(changes)
   with self.assertRaises(m.Blocked):m.approved_proof(p,'a'*40)
 def test_missing_or_reordered_stage_blocks_candidate(self):
  for p in (proof(),proof()):
   p['stages']=p['stages'][:-1]
   with self.assertRaises(m.Blocked):m.approved_proof(p,'a'*40)
  p=proof();p['stages'].reverse()
  with self.assertRaises(m.Blocked):m.approved_proof(p,'a'*40)
 def test_crash_failure_skip_or_unknown_count_cannot_be_approved(self):
  for change in (dict(ok_marker=False),dict(process_crashed=True),dict(instrumentation_failed=True),dict(failures=True),dict(exception_types=['AssertionError']),dict(expected_tests=0)):
   p=proof();p['stages'][0]['native'].update(change)
   with self.assertRaises(m.Blocked):m.approved_proof(p,'a'*40)
 def test_reusable_job_cannot_access_environment_before_policy_passes(self):
  w=yaml.safe_load((ROOT/'.github/workflows/official-signed-upgrade.yml').read_text())
  self.assertNotIn('environment',w['jobs']['policy']);self.assertEqual('policy',w['jobs']['protected']['needs']);self.assertEqual('release',w['jobs']['protected']['environment'])
  self.assertFalse(w['concurrency']['cancel-in-progress'])
 def test_repository_secrets_are_forwarded_explicitly_without_inherit(self):
  c=yaml.safe_load((ROOT/'.github/workflows/h131-investigation.yml').read_text())['jobs']['official-signed']
  self.assertEqual({('REPO_DIARY_'+name):'${{ secrets.DIARY_'+name+' }}'for name in ('KEYSTORE_BASE64','STORE_PASSWORD','KEY_ALIAS','KEY_PASSWORD')},c['secrets'])
  self.assertEqual('${{ vars.DIARY_CERT_SHA256 }}',c['with']['repository_certificate_sha256'])
  w=yaml.safe_load((ROOT/'.github/workflows/official-signed-upgrade.yml').read_text());steps=w['jobs']['protected']['steps']
  self.assertEqual(1,sum('secrets.'in str(s)for s in steps));self.assertNotIn('secrets.',str(w.get('env')));self.assertNotIn('secrets: inherit',str(c));self.assertNotIn('secrets.',str(w['jobs']['policy']))
 def test_repository_aliases_do_not_read_same_named_environment_secrets_or_variable(self):
  w=yaml.safe_load((ROOT/'.github/workflows/official-signed-upgrade.yml').read_text())
  s=next(s for s in w['jobs']['protected']['steps']if 'secrets.'in str(s))
  self.assertEqual('${{ secrets.REPO_DIARY_KEYSTORE_BASE64 }}',s['env']['STORE_B64'])
  self.assertEqual('${{ inputs.repository_certificate_sha256 }}',s['env']['CERT_SHA'])
  self.assertNotIn('secrets.DIARY_',str(w));self.assertNotIn('vars.DIARY_CERT_SHA256',str(w))
 def test_workflow_validator_does_not_require_reviewer_or_self_review_predicates(self):
  text=(ROOT/'scripts/official-signing.py').read_text()
  for removed in ('INDEPENDENT_REVIEW_REQUIRED','REVIEWER_CONFIGURATION',"get('protection_rules'","get('prevent_self_review'"):self.assertNotIn(removed,text)
 def test_action_references_are_pinned(self):
  w=yaml.safe_load((ROOT/'.github/workflows/official-signed-upgrade.yml').read_text())
  for j in w['jobs'].values():
   for s in j['steps']:
    if 'uses'in s:self.assertRegex(s['uses'],r'@[0-9a-f]{40}$')
 def test_original_is_not_resigned_and_no_key_generator_exists_in_protected_code(self):
  s=(ROOT/'scripts/official-signing.py').read_text();java=(ROOT/'scripts/signing/SigningPreflight.java').read_text()
  for forbidden in ('genkeypair','KeyPairGenerator','disposable.p12'):self.assertNotIn(forbidden,s+java)
  self.assertIn("shutil.copyfile(original,private()/'old.apk')",s);self.assertIn("execute(signing='PERMANENT')",s)
 def test_private_key_is_deleted_before_emulator_actions(self):
  s=(ROOT/'scripts/official-signing.py').read_text();self.assertIn('finally:store.unlink(missing_ok=True)',s);self.assertIn('KEY_MUST_BE_REMOVED',s)
 def test_apk_upload_is_after_approval_and_never_always(self):
  w=yaml.safe_load((ROOT/'.github/workflows/official-signed-upgrade.yml').read_text());ss=w['jobs']['protected']['steps']
  i=next(i for i,s in enumerate(ss)if s.get('run')=='python3 scripts/official-signing.py approve')
  uploads=[(n,s)for n,s in enumerate(ss)if 'uses'in s and s['uses'].startswith('actions/upload-artifact@')and s['with']['path'].endswith('/candidate/')]
  self.assertEqual(1,len(uploads));self.assertGreater(uploads[0][0],i);self.assertIn('success()',uploads[0][1]['if'])
  self.assertNotIn('tests.apk',str([s['with']for s in ss if s.get('uses','').startswith('actions/upload-artifact@')]))
 def test_default_application_and_experimental_eligibility_remain(self):
  g=(ROOT/'app/build.gradle.kts').read_text();self.assertIn('versionName = if (officialCandidate) "0.22.0" else "0.22.0-dev-e2"',g)
  self.assertIn('versionCode = 45',g)
  a=(ROOT/'app/src/main/java/br/com/diariodeclasse/sync/academic/AcademicRuntime.kt').read_text();self.assertIn('=BuildConfig.DEBUG &&',a)
 def test_missing_secrets_never_invoke_java_build_or_fallback(self):
  with tempfile.TemporaryDirectory()as d,patch.dict(os.environ,dict(env(),RUNNER_TEMP=d),clear=True),patch.object(m,'protect'),patch.object(m,'run')as called:
   with self.assertRaisesRegex(m.Blocked,'SIGNING_SECRETS_UNAVAILABLE'):m.preflight()
   called.assert_not_called();self.assertFalse((m.private()/'existing.p12').exists())
 def test_failed_key_checks_delete_temporary_store_in_both_modes(self):
  for command in (m.preflight,m.prepare):
   with tempfile.TemporaryDirectory()as d,patch.dict(os.environ,dict(env('CANDIDATE'),RUNNER_TEMP=d),clear=True):
    m.private().mkdir(mode=0o700);store=m.private()/'existing.p12';store.write_bytes(b'synthetic-temporary-fixture')
    with patch.object(m,'load_existing',side_effect=m.Blocked('KEYSTORE_VERIFICATION')),self.assertRaises(m.Blocked):command()
    self.assertFalse(store.exists())
 def test_failed_android_proof_cannot_create_an_apk_artifact(self):
  with tempfile.TemporaryDirectory()as d,patch.object(m,'ROOT',Path(d)),patch.dict(os.environ,env('CANDIDATE'),clear=True),patch.object(m,'protect'),patch.object(m,'driver')as called:
   r=Path(d)/'build/official-upgrade';r.mkdir(parents=True);(r/'android.json').write_text(json.dumps(dict(passed=False)))
   o=m.out();(o/'identity.json').write_text('{}')
   with self.assertRaises(m.Blocked):m.approve()
   called.return_value.identity.assert_not_called();self.assertFalse((o/'candidate').exists())
if __name__=='__main__':unittest.main()
