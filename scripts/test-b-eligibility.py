#!/usr/bin/env python3
"""Synthetic, network-free proof of the B-only request budget and sanitization."""
import base64,importlib.util,json,ssl,tempfile,time,unittest
from pathlib import Path
from unittest.mock import patch
spec=importlib.util.spec_from_file_location('bdiag',Path(__file__).with_name('b-eligibility.py'));b=importlib.util.module_from_spec(spec);spec.loader.exec_module(b)
B=dict(email='b@example.invalid',password='Synthetic-B-Only-2026',expectedId='99999999-9999-4999-8999-999999999999')
def token():
 claims=dict(iss='https://'+b.HOST+'/auth/v1',sub=B['expectedId'],role='authenticated',session_id='93939393-9393-4393-8393-939393939393',exp=int(time.time())+3600)
 enc=lambda j:base64.urlsafe_b64encode(json.dumps(j).encode()).decode().rstrip('=')
 return enc({'alg':'HS256'})+'.'+enc(claims)+'.synthetic'
def login():return dict(access_token=token(),user=dict(id=B['expectedId'],email=B['email'],email_confirmed_at='2026-01-01T00:00:00Z',is_anonymous=False))
class Fake(b.Client):
 def __init__(self,status=None,responses=None):
  super().__init__(b.Credentials('sb_publishable_SyntheticOnlyForTests',B));self.sent=[]
  self.status_value=status or dict(protocol=2,academicScope=5,testAllowed=True);self.responses=responses or {}
 def _exchange(self,path,body,headers):
  self.sent.append((path,body,headers));step={v:k for k,v in b.ROUTES.items()}[path]
  if step in self.responses:
   value=self.responses[step]
   if isinstance(value,Exception):raise value
   return value
  return (200,json.dumps(login()if step=='LOGIN_B'else self.status_value if step=='STATUS_B'else{}).encode())
def env():return dict(GITHUB_ACTIONS='true',GITHUB_EVENT_NAME='workflow_dispatch',GITHUB_REF=b.auth.BRANCH,GITHUB_REPOSITORY=b.auth.REPO,RUNNER_ENVIRONMENT='github-hosted',GITHUB_RUN_ATTEMPT='1')
class Safety(unittest.TestCase):
 def test_hosted_requires_separate_explicit_authorization(self):
  e=env();e['NOCTUA_B_ELIGIBILITY_MODE']='HOSTED'
  with self.assertRaises(b.Blocked):b.context(e)
  e['NOCTUA_B_ELIGIBILITY_AUTHORIZED']='YES';self.assertEqual(b.context(e),'HOSTED')
 def test_rerun_attempt_is_blocked(self):
  e=env();e.update(NOCTUA_B_ELIGIBILITY_MODE='HOSTED',NOCTUA_B_ELIGIBILITY_AUTHORIZED='YES',GITHUB_RUN_ATTEMPT='2')
  with self.assertRaises(b.Blocked):b.context(e)
 def test_wrong_branch_or_repository_blocked(self):
  for key in ('GITHUB_REF','GITHUB_REPOSITORY'):
   e=env();e[key]='wrong'
   with self.assertRaises(b.auth.Blocked):b.context(e)
 def test_preflight_performs_no_http(self):
  self.assertEqual(b.context(env()),'PREFLIGHT')
  with patch.object(b,'configuration',return_value=b.Credentials('private',B)),patch.object(b.Client,'_exchange',side_effect=AssertionError('HTTP')),patch.dict(b.os.environ,env(),clear=True),tempfile.TemporaryDirectory()as tmp,patch.object(b,'REPORT',Path(tmp)/'public.json'):
   b.main();self.assertFalse(json.loads(b.REPORT.read_text())['hosted_requests'])
 def test_only_b_is_sent_and_only_one_status(self):
  c=Fake();r=b.diagnose(c)
  self.assertEqual(r['overall'],'DIAGNOSED_ELIGIBLE');self.assertEqual(r['requests'],dict(LOGIN_B=1,STATUS_B=1,LOGOUT=1));self.assertTrue(r['own_session_logout_confirmed'])
  self.assertEqual(c.sent[0][1],{k:B[k]for k in ('email','password')});self.assertNotIn('Authorization',c.sent[0][2])
  self.assertEqual(c.sent[1][1],{});self.assertEqual(c.sent[2][1],{});self.assertEqual(c.sent[2][0],'/auth/v1/logout?scope=local')
 def test_each_eligibility_predicate_diagnosed_without_transition(self):
  for status,expected in [(dict(protocol=1,academicScope=2,testAllowed=True),('V1','B',True)),(dict(protocol=2,academicScope=3,testAllowed=True),('V2','C',True)),(dict(protocol=2,academicScope=4,testAllowed=True),('V2','D',True)),(dict(protocol=2,academicScope=5,testAllowed=False),('V2','E',False)),(dict(protocol=9,academicScope=99,testAllowed=True),('OTHER','OTHER',True))]:
   c=Fake(status);r=b.diagnose(c);o=r['eligibility'];self.assertEqual((o['protocol'],o['scope'],o['test_allowed']),expected)
   self.assertFalse(o['ready']);self.assertEqual(r['overall'],'DIAGNOSED_NOT_ELIGIBLE');self.assertEqual(len(c.sent),3)
 def test_no_missing_fields_are_invented(self):
  for key in ('protocol','academicScope','testAllowed'):
   s=dict(protocol=2,academicScope=5,testAllowed=True);del s[key]
   r=b.diagnose(Fake(s));self.assertEqual(r['blocking_code'],'STATUS_FIELDS_REQUIRED');self.assertIsNone(r['eligibility']);self.assertTrue(r['own_session_logout_confirmed'])
 def test_field_types_are_strict(self):
  for field,value in [('protocol',True),('academicScope','5'),('testAllowed',1)]:
   s=dict(protocol=2,academicScope=5,testAllowed=True);s[field]=value
   with self.assertRaises(b.Blocked):b.observation(s)
 def test_mutation_helpers_and_pull_blocked_before_transport(self):
  c=Fake()
  for op in ('PATCH','PUSH','PULL','STAGE','ACTIVATE','ENABLE_E','DECISION','LOGOUT_GLOBAL','https://production.invalid'):
   with self.assertRaises(b.Blocked):c._request(op,{})
  self.assertEqual(c.sent,[])
 def test_no_status_or_logout_before_created_session(self):
  c=Fake()
  for method in (c.status,c.logout):
   with self.assertRaises(b.Blocked):method()
  self.assertEqual(c.sent,[])
 def test_login_for_another_account_rejected_before_http(self):
  c=Fake()
  with self.assertRaises(b.Blocked):c._request('LOGIN_B',dict(email='a@example.invalid',password=B['password']))
  self.assertEqual(c.sent,[])
 def test_repeating_any_operation_is_not_a_retry(self):
  c=Fake();c.login();c.status();c.logout()
  for method in (c.login,c.status,c.logout):
   with self.assertRaises(b.Blocked):method()
  self.assertEqual(len(c.sent),3)
 def test_wrong_identity_closes_only_new_session_and_never_reads(self):
  data=login();data['user']['id']='88888888-8888-4888-8888-888888888888';c=Fake(responses={'LOGIN_B':(200,json.dumps(data).encode())});r=b.diagnose(c)
  self.assertEqual(r['blocking_code'],'AUTH_IDENTITY');self.assertTrue(r['own_session_logout_confirmed']);self.assertEqual(r['requests']['STATUS_B'],0);self.assertEqual(len(c.sent),2)
 def test_unconfirmed_or_anonymous_block_before_status(self):
  for fields in [dict(email_confirmed_at=None),dict(is_anonymous=True)]:
   data=login();data['user'].update(fields);r=b.diagnose(Fake(responses={'LOGIN_B':(200,json.dumps(data).encode())}))
   self.assertEqual(r['blocking_code'],'AUTH_CONFIRMED_REQUIRED');self.assertEqual(r['requests']['STATUS_B'],0);self.assertTrue(r['own_session_logout_confirmed'])
 def test_rejected_credentials_do_not_trigger_status_or_logout(self):
  r=b.diagnose(Fake(responses={'LOGIN_B':(400,b'{"private":"do not publish"}')}));self.assertEqual(r['requests'],dict(LOGIN_B=1,STATUS_B=0,LOGOUT=0));self.assertEqual(r['http_status']['LOGIN_B'],400)
 def test_failed_status_is_not_retried_and_own_logout_still_runs(self):
  for response in [(403,b'private'),(429,b'private'),(503,b'private'),OSError('private network text')]:
   c=Fake(responses={'STATUS_B':response});r=b.diagnose(c);self.assertEqual(r['overall'],'BLOCKED');self.assertTrue(r['own_session_logout_confirmed']);self.assertEqual(len(c.sent),3)
 def test_redirects_are_not_followed(self):
  c=Fake(responses={'STATUS_B':(307,b'private location')});r=b.diagnose(c);self.assertEqual(r['blocking_code'],'REDIRECT_REFUSED');self.assertEqual(len(c.sent),3)
 def test_failed_logout_is_not_repeated(self):
  c=Fake(responses={'LOGOUT':(503,b'private')});r=b.diagnose(c);self.assertEqual(r['blocking_code'],'OWN_LOGOUT_FAILED');self.assertFalse(r['own_session_logout_confirmed']);self.assertEqual(c.calls['LOGOUT'],1)
 def test_response_size_is_bounded(self):
  r=b.diagnose(Fake(responses={'STATUS_B':(200,b'x'*(b.LIMIT+1))}));self.assertEqual(r['blocking_code'],'RESPONSE_LIMIT');self.assertTrue(r['own_session_logout_confirmed'])
 def test_public_report_has_no_credentials_claims_or_raw_response(self):
  c=Fake();r=b.diagnose(c)
  with tempfile.TemporaryDirectory()as tmp,patch.object(b,'REPORT',Path(tmp)/'public.json'):
   b.publish(env(),**r);raw=b.REPORT.read_text()
   for secret in (B['email'],B['password'],B['expectedId'],c.credentials.key,'access_token','session_id'):self.assertNotIn(secret,raw)
 def test_unknown_exception_payload_is_never_a_diagnostic_code(self):
  c=Fake(responses={'STATUS_B':b.Blocked('PRIVATE_SECRET_SENTINEL')});r=b.diagnose(c)
  self.assertNotIn('PRIVATE_SECRET_SENTINEL',json.dumps(r));self.assertEqual(r['blocking_code'],'TRANSPORT_FAILED');self.assertTrue(r['own_session_logout_confirmed'])
 def test_tls_fixed_host_and_resource_limits(self):
  class Response:
   status=200
   def read1(self,n):return b''
  with patch.object(b.http.client,'HTTPSConnection')as connection:
   connection.return_value.getresponse.return_value=Response();c=Fake();b.Client._exchange(c,b.ROUTES['STATUS_B'],{},{} )
   args,kw=connection.call_args;self.assertEqual(args,(b.HOST,));self.assertLessEqual(kw['timeout'],10)
   self.assertTrue(kw['context'].check_hostname);self.assertEqual(kw['context'].verify_mode,ssl.CERT_REQUIRED)
   self.assertEqual(connection.return_value.request.call_args.args[:2],('POST',b.ROUTES['STATUS_B']))
   connection.return_value.close.assert_called_once()
 def test_configuration_selects_only_second_protected_account(self):
  a=dict(B,email='a@example.invalid',expectedId='88888888-8888-4888-8888-888888888888')
  e=dict(NOCTUA_HOMOLOGACAO_PUBLISHABLE_KEY='sb_publishable_SyntheticOnlyForTests',NOCTUA_HOMOLOGACAO_AUTH_TEST_JSON=json.dumps(dict(accounts=[a,B],fixtureClassId='synthetic-only',allowZeroRowPermissionProbe=False)))
  self.assertEqual(b.configuration(e).account,B)
 def test_workflow_requires_guards_environment_and_named_secrets(self):
  import yaml
  root=b.ROOT;w=yaml.safe_load((root/'.github/workflows/b-eligibility.yml').read_text())
  guards=w['jobs']['local-guards'];hosted=w['jobs']['diagnostic']
  self.assertNotIn('environment',guards);self.assertNotIn('secrets.',json.dumps(guards))
  self.assertEqual(hosted['needs'],'local-guards');self.assertEqual(hosted['environment'],'noctua-homologacao-h01-h04')
  self.assertNotIn('emulator-runner',json.dumps(w));self.assertNotIn('gradlew',json.dumps(w))
  caller=yaml.safe_load((root/'.github/workflows/h131-investigation.yml').read_text())['jobs']['b-eligibility']
  self.assertEqual(set(caller['secrets']),{'NOCTUA_HOMOLOGACAO_PUBLISHABLE_KEY','NOCTUA_HOMOLOGACAO_AUTH_TEST_JSON'})
  self.assertIn('authorize_hosted_b_eligibility',caller['with']['authorized'])
 def test_network_exchange_has_a_deadline_and_no_retry(self):
  class Response:
   status=200
   def read1(self,n):raise AssertionError('must stop before reading')
  with patch.object(b.http.client,'HTTPSConnection')as connection,patch.object(b.time,'monotonic',side_effect=[0,20]):
   connection.return_value.getresponse.return_value=Response()
   with self.assertRaises(b.Blocked):b.Client._exchange(Fake(),b.ROUTES['STATUS_B'],{},{} )
   self.assertEqual(connection.return_value.request.call_count,1);connection.return_value.close.assert_called_once()
 def test_private_representation_redacted(self):self.assertEqual(repr(b.Credentials('private',B)),'Credentials(REDACTED)')
if __name__=='__main__':unittest.main()
