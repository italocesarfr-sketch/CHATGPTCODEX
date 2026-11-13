#!/usr/bin/env python3
"""Offline only: fake inputs and XML. No Supabase, credentials, Gradle or network."""
import importlib.util
import json
import os
from pathlib import Path
import stat
import tempfile
import unittest
import uuid
import xml.etree.ElementTree as ET
from unittest.mock import patch

spec=importlib.util.spec_from_file_location('checks',Path(__file__).with_name('hosted-auth-checks.py'))
f=importlib.util.module_from_spec(spec);spec.loader.exec_module(f)
KEY='sb_publishable_'+'H'*24
PROD='sb_publishable_'+'P'*24
LEGACY='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'

def fixture():
    return dict(accounts=[dict(email='a@example.invalid',password='Fictitious-only-pw-A',expectedId='11111111-1111-4111-8111-111111111111'),dict(email='b@example.invalid',password='Fictitious-only-pw-B',expectedId='22222222-2222-4222-8222-222222222222')],fixtureClassId='fict-class-A',allowZeroRowPermissionProbe=True)
def xml(status=None, extra=False):
    r=ET.Element('testsuite',tests='4',failures='0',errors='0',skipped='0')
    for index,name in enumerate(f.METHODS):
        c=ET.SubElement(r,'testcase',classname='br.com.diariodeclasse.HomologationHostedAuthTest',name=name)
        if index==0 and status:
            ET.SubElement(c,status,message='PRIVATE_PASSWORD_OR_TOKEN').text='PRIVATE_PASSWORD_OR_TOKEN'
            ET.SubElement(c,'system-out').text='PRIVATE_PASSWORD_OR_TOKEN'
    if extra:ET.SubElement(r,'testcase',classname='other',name='PRIVATE_PASSWORD_OR_TOKEN')
    return ET.tostring(r)

class HostedAuthSafetyTest(unittest.TestCase):
    def test_reusable_workflow_forwards_only_named_secrets_and_keeps_environment_gate(self):
        caller=(f.ROOT/'.github/workflows/android.yml').read_text()
        job=caller.split('\n  hosted-auth:\n',1)[1].split('\n  homologacao:\n',1)[0]
        self.assertIn("github.event_name == 'workflow_dispatch'",job)
        self.assertIn("github.ref == '"+f.BRANCH+"'",job)
        self.assertIn('authorized: ${{ inputs.authorize_hosted_h01_h04 == true }}',job)
        # An Environment-only secret still needs this explicit caller mapping.
        secret_lines=job.split('    secrets:\n',1)[1].strip().splitlines()
        expected={name+': ${{ secrets.'+name+' }}' for name in (
            'NOCTUA_HOMOLOGACAO_PUBLISHABLE_KEY','NOCTUA_HOMOLOGACAO_AUTH_TEST_JSON')}
        self.assertEqual(expected,{line.strip() for line in secret_lines})
        self.assertNotIn('secrets: inherit',job)
        callee=(f.ROOT/'.github/workflows/hosted-auth.yml').read_text()
        self.assertIn('NOCTUA_HOMOLOGACAO_AUTH_TEST_JSON:\n        required: true',callee)
        self.assertIn('environment: noctua-homologacao-h01-h04',callee)
        self.assertIn("github.event_name == 'workflow_dispatch' && github.ref == '"+f.BRANCH+"'",callee)
        self.assertIn("default: false",callee)
    def test_valid_two_account_fixture_is_local_configuration_not_auth_proof(self):
        self.assertEqual(fixture(),f.validate(KEY,json.dumps(fixture()),PROD,LEGACY))
    def test_missing_admin_and_production_keys_are_blocked(self):
        for key in ('','sb_secret_never-allowed',PROD,'legacy-jwt'):
            with self.subTest(key_type=key[:5]),self.assertRaises(f.Blocked):f.validate(key,json.dumps(fixture()),PROD,LEGACY)
    def test_private_json_errors_do_not_echo_input(self):
        for raw in ('PRIVATE_PASSWORD_OR_TOKEN','{"accounts":["PRIVATE_PASSWORD_OR_TOKEN"]}'):
            with self.assertRaises(f.Blocked) as ctx:f.validate(KEY,raw,PROD,LEGACY)
            self.assertNotIn('PRIVATE_PASSWORD_OR_TOKEN',str(ctx.exception))
    def test_identity_duplicates_and_legacy_owner_are_blocked(self):
        for prop,value in [('expectedId',LEGACY),('expectedId','PRIVATE_PASSWORD_OR_TOKEN'),('expectedId',fixture()['accounts'][1]['expectedId']),('email',fixture()['accounts'][1]['email'])]:
            d=fixture();d['accounts'][0][prop]=value
            with self.subTest(prop=prop),self.assertRaises(f.Blocked):f.validate(KEY,json.dumps(d),PROD,LEGACY)
    def test_fixture_and_h03_consent_are_required(self):
        for prop,value in [('fixtureClassId',''),('fixtureClassId','<ID_A>'),('allowZeroRowPermissionProbe',False)]:
            d=fixture();d[prop]=value
            with self.subTest(prop=prop),self.assertRaises(f.Blocked):f.validate(KEY,json.dumps(d),PROD,LEGACY)
    def test_endpoint_override_in_secret_is_not_accepted(self):
        d=fixture();d['url']='https://forbidden.example.invalid'
        with self.assertRaises(f.Blocked):f.validate(KEY,json.dumps(d),PROD,LEGACY)
    def test_scope_never_accepts_push_main_or_other_repository(self):
        base=dict(GITHUB_ACTIONS='true',GITHUB_EVENT_NAME='workflow_dispatch',GITHUB_REF=f.BRANCH,GITHUB_REPOSITORY=f.REPO)
        f.scope(base)
        for prop,value in [('GITHUB_ACTIONS','false'),('GITHUB_EVENT_NAME','push'),('GITHUB_REF','refs/heads/main'),('GITHUB_REPOSITORY','other/repo')]:
            with self.subTest(prop=prop),self.assertRaises(f.Blocked):f.scope(dict(base,**{prop:value}))
    def test_four_results_require_successful_build_and_no_skips(self):
        cases,ok=f.summarize(xml(),0);self.assertTrue(ok);self.assertTrue(all(c['status']=='PASSED' for c in cases))
        for raw,code in [(xml(),1),(xml('skipped'),0),(xml('failure'),1),(xml('error'),1),(xml(extra=True),0),(b'',0)]:
            with self.subTest(code=code):self.assertFalse(f.summarize(raw,code)[1])
    def test_h03_selection_does_not_claim_the_other_cases_ran(self):
        r=ET.fromstring(xml())
        for node in list(r):
            if f.METHODS[node.get('name')]!='H03':r.remove(node)
        r.set('tests','1');raw=ET.tostring(r)
        cases,approved=f.summarize(raw,0,'H03')
        self.assertTrue(approved)
        self.assertEqual({'H01':'NOT_EXECUTED','H02':'NOT_EXECUTED','H03':'PASSED','H04':'NOT_EXECUTED'},
                         {c['id']:c['status'] for c in cases})
        self.assertFalse(f.summarize(raw,0,'ALL')[1])
        self.assertFalse(f.summarize(xml(),0,'H03')[1])
        with self.assertRaises(f.Blocked):f.selected_methods('PRIVATE_OR_ARBITRARY_FILTER')
    def test_h03_diagnostics_allow_only_fixed_vocabulary_and_http_status(self):
        r=ET.fromstring(xml('failure'))
        ET.SubElement(r,'system-out').text='\n'.join([
            'PRIVATE_PASSWORD_OR_TOKEN',
            'H03_DIAG stage=PATCH status=403 code=HTTP',
            'H03_DIAG stage=HELPER status=404 code=HTTP',
            'H03_DIAG stage=PATCH status=NONE code=TIMEOUT',
            'H03_DIAG stage=PRIVATE_PASSWORD_OR_TOKEN status=403 code=HTTP',
            'H03_DIAG stage=PATCH status=403 code=PRIVATE_PASSWORD_OR_TOKEN',
            'H03_DIAG stage=PATCH status=403 code=HTTP PRIVATE_PASSWORD_OR_TOKEN',
            'H03_DIAG stage=PATCH status=999 code=HTTP'])
        events=f.h03_diagnostics(ET.tostring(r))
        self.assertEqual([dict(stage='PATCH',status=403,code='HTTP'),
                          dict(stage='HELPER',status=404,code='HTTP'),
                          dict(stage='PATCH',status=None,code='TIMEOUT')],events)
        self.assertNotIn('PRIVATE_PASSWORD_OR_TOKEN',json.dumps(events))
    def test_h03_only_success_keeps_all_four_passed_false(self):
        with tempfile.TemporaryDirectory() as directory:
            r=ET.fromstring(xml())
            for node in list(r):
                if f.METHODS[node.get('name')]!='H03':r.remove(node)
            r.set('tests','1')
            source=Path(directory)/'h03.xml';source.write_bytes(ET.tostring(r))
            output=Path(directory)/'redacted.json'
            env=dict(GITHUB_ACTIONS='true',GITHUB_EVENT_NAME='workflow_dispatch',GITHUB_REF=f.BRANCH,
                     GITHUB_REPOSITORY=f.REPO,NOCTUA_HOSTED_AUTH_MODE='run',
                     NOCTUA_HOMOLOGACAO_AUTH_TEST_AUTHORIZED='YES',NOCTUA_HOSTED_AUTH_CASE='H03')
            with patch.object(f,'REPORT',output),patch.object(f,'XML',source),patch.dict(os.environ,env,clear=True),patch('sys.argv',['checks','summarize','--exit-code','0']):
                self.assertEqual(0,f.main())
            value=json.loads(output.read_text());self.assertFalse(value['all_four_passed'])
            self.assertTrue(value['selected_cases_passed']);self.assertEqual('PASSED_SELECTED_CASES',value['overall'])
    def test_report_never_copies_failure_message_stream_or_unexpected_test_names(self):
        for raw in (xml('failure'),xml(extra=True),b'PRIVATE_PASSWORD_OR_TOKEN'):
            cases,ok=f.summarize(raw,1)
            self.assertFalse(ok);self.assertNotIn('PRIVATE_PASSWORD_OR_TOKEN',json.dumps(cases))
    def test_inconsistent_suite_failure_count_cannot_pass(self):
        self.assertFalse(f.summarize(xml().replace(b'failures="0"',b'failures="1"'),0)[1])
    def test_missing_xml_after_attempt_is_unknown_not_zero_hosted_requests(self):
        with tempfile.TemporaryDirectory() as directory:
            env=dict(GITHUB_ACTIONS='true',GITHUB_EVENT_NAME='workflow_dispatch',GITHUB_REF=f.BRANCH,GITHUB_REPOSITORY=f.REPO,NOCTUA_HOSTED_AUTH_MODE='run',NOCTUA_HOMOLOGACAO_AUTH_TEST_AUTHORIZED='YES')
            output=Path(directory)/'redacted.json'
            with patch.object(f,'REPORT',output),patch.object(f,'XML',Path(directory)/'missing.xml'),patch.dict(os.environ,env,clear=True),patch('sys.argv',['checks','summarize','--exit-code','137']):
                self.assertEqual(1,f.main())
            value=json.loads(output.read_text())
            self.assertIsNone(value['hosted_requests_executed'])
            self.assertFalse(value['all_four_passed'])
            self.assertEqual({'NOT_REPORTED'},{c['status'] for c in value['cases']})
    def test_preflight_does_not_write_accounts_but_run_uses_private_permissions(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)/'repo';root.mkdir();(root/'app/src/main/java/br/com/diariodeclasse/auth').mkdir(parents=True)
            (root/'app/build.gradle.kts').write_text('val productionPublishable = "'+PROD+'"')
            (root/'app/src/main/java/br/com/diariodeclasse/auth/AuthModels.kt').write_text('const val LEGACY_OWNER_ID = "'+LEGACY+'"')
            temp=Path(directory)/'private';temp.mkdir();ghenv=Path(directory)/'github-env';ghenv.touch()
            env=dict(GITHUB_ACTIONS='true',GITHUB_EVENT_NAME='workflow_dispatch',GITHUB_REF=f.BRANCH,GITHUB_REPOSITORY=f.REPO,RUNNER_TEMP=str(temp),GITHUB_ENV=str(ghenv),NOCTUA_HOMOLOGACAO_PUBLISHABLE_KEY=KEY,NOCTUA_HOMOLOGACAO_AUTH_TEST_JSON=json.dumps(fixture()))
            with patch.object(f,'ROOT',root),patch.object(f,'XML',root/'generated.xml'):
                self.assertEqual('preflight',f.prepare(env));self.assertEqual([],list(temp.iterdir()))
                run=dict(env,NOCTUA_HOSTED_AUTH_MODE='run')
                with self.assertRaises(f.Blocked):f.prepare(run)
                run['NOCTUA_HOMOLOGACAO_AUTH_TEST_AUTHORIZED']='YES';self.assertEqual('run',f.prepare(run))
                accounts=f.private_dir(run)/'accounts.json'
                self.assertEqual(0o600,stat.S_IMODE(accounts.stat().st_mode));self.assertEqual(0o700,stat.S_IMODE(accounts.parent.stat().st_mode))
                self.assertNotIn('Fictitious-only',ghenv.read_text())
                f.clean(run);self.assertFalse(accounts.exists())

if __name__=='__main__':unittest.main(verbosity=2)
