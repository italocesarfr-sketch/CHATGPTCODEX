#!/usr/bin/env python3
"""Offline workflow/deployment safeguards; no SQL execution, credentials or hosted clients."""
import hashlib
from pathlib import Path
import unittest
import yaml

ROOT=Path(__file__).resolve().parents[1]
class PreparationTest(unittest.TestCase):
    def setUp(self):
        self.caller=yaml.safe_load((ROOT/'.github/workflows/android.yml').read_text())
        self.callee=yaml.safe_load((ROOT/'.github/workflows/homologacao.yml').read_text())
    def test_only_public_key_is_forwarded(self):
        job=self.caller['jobs']['homologacao']
        self.assertEqual(set(job['secrets']),{'NOCTUA_HOMOLOGACAO_PUBLISHABLE_KEY'})
        self.assertEqual(job['uses'],'./.github/workflows/homologacao.yml')
    def test_dispatch_opt_in_is_manual_and_tied_to_the_commercial_branch(self):
        event=yaml.load((ROOT/'.github/workflows/android.yml').read_text(),Loader=yaml.BaseLoader)['on']
        self.assertEqual(event['workflow_dispatch']['inputs']['homologacao_only']['default'],'false')
        self.assertLessEqual(len(event['workflow_dispatch']['inputs']),10)
        condition=self.caller['jobs']['homologacao']['if']
        for required in ("github.event_name == 'workflow_dispatch'",'inputs.homologacao_only == true',
                         'refs/heads/sync/academic-commercial-auto-031'):
            self.assertIn(required,condition)
        self.assertEqual(self.caller['jobs']['homologacao']['with']['commercial_auto'],
            "${{ github.ref == 'refs/heads/sync/academic-commercial-auto-031' }}")
    def test_no_homologation_access_to_signing_or_account_credentials(self):
        text=(ROOT/'.github/workflows/homologacao.yml').read_text()
        self.assertNotIn('DIARY_',text);self.assertNotIn('AUTH_TEST_JSON',text)
        self.assertEqual(self.callee['permissions'],{'contents':'read'})
        env=self.callee['jobs']['homologacao']['env']
        for name in ('NOCTUA_AUTH_LIVE_FILE','NOCTUA_HOMOLOGACAO_AUTH_TEST_FILE','NOCTUA_HOMOLOGACAO_AUTH_TEST_AUTHORIZED'):
            self.assertEqual(env[name],'')
    def test_build_does_not_deploy_or_call_auth(self):
        for step in self.callee['jobs']['homologacao']['steps']:
            text=step.get('run','')
            for forbidden in ('psql','supabase db','installHomologacao','assembleRelease','official-signing.py','hosted-auth-checks.py'):
                self.assertNotIn(forbidden,text)
        self.assertIn('commercial_auto',self.caller['jobs']['homologacao']['with'])
    def test_migration_is_the_exact_validated_source(self):
        p=ROOT/'supabase/migrations/202611170001_commercial_bootstrap.sql'
        self.assertEqual(hashlib.sha256(p.read_bytes()).hexdigest(),'1d5c48c0b55a24edbae948407a8775030490338c8a3b0c6a64b2a98e5bf051e2')
        lines=p.read_text().splitlines()
        self.assertEqual(lines.count('BEGIN;'),1);self.assertEqual(lines.count('COMMIT;'),1)
    def test_preflight_is_read_only_and_does_not_invoke_rpc(self):
        text=(ROOT/'supabase/checks/commercial_032_preflight_readonly.sql').read_text()
        self.assertIn('BEGIN TRANSACTION READ ONLY;',text);self.assertTrue(text.endswith('ROLLBACK;\n'))
        self.assertNotIn('SELECT public.noctua_',text)
        for action in ('INSERT INTO','UPDATE public.','DELETE FROM','ALTER TABLE','CREATE FUNCTION'):
            self.assertNotIn(action,text)
    def test_rollout_and_entitlements_are_not_granted_by_preparation(self):
        text=(ROOT/'supabase/migrations/202611170001_commercial_bootstrap.sql').read_text()
        self.assertIn('VALUES(true,false)',text)
        self.assertNotIn('INSERT INTO public.noctua_commercial_accounts(',text)
        self.assertNotIn('INSERT INTO public.noctua_academic_test_accounts',text)
    def test_identity_validation_requires_the_requested_opt_in(self):
        text=(ROOT/'scripts/validate-homologacao.py').read_text()
        self.assertIn('--expect-commercial',text)
        self.assertIn('if a.expect_commercial:assert commercial',text)
        self.assertIn('certificate_sha256',text)
        self.assertIn("project='sscexzxtqdcsqdlcytum'",text)

if __name__=='__main__':unittest.main(verbosity=2)
