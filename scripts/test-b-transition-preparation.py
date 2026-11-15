#!/usr/bin/env python3
"""Static guardrails for LOCAL-only preparation. Actual PostgreSQL/Room proof runs in CI."""
from pathlib import Path
import unittest,yaml
ROOT=Path(__file__).resolve().parents[1]
class Safety(unittest.TestCase):
 def test_candidate_changes_exactly_the_missing_predecessor_guard(self):
  old=(ROOT/'supabase/migrations/202611020002_phase_c_scope.sql').read_text();start=old.index('CREATE OR REPLACE FUNCTION public.noctua_enable_academic_c(');end=old.index('\nCREATE OR REPLACE FUNCTION ',start+1)
  expected=old[start:end].replace("IF scope IS NULL THEN RAISE EXCEPTION 'protocol transition required'","IF scope IS NULL OR scope NOT IN(2,3) THEN RAISE EXCEPTION 'phase B transition required'")
  candidate=(ROOT/'supabase/migrations/202611150001_scope_transition_guard.sql').read_text()
  self.assertEqual(candidate[candidate.index('CREATE OR REPLACE'):candidate.rindex('\nCOMMIT;')].strip(),expected.strip())
  self.assertNotIn('GRANT ',candidate);self.assertNotIn('ALTER TABLE',candidate)
 def test_workflow_has_no_environment_secrets_or_hosted_mode(self):
  w=yaml.safe_load((ROOT/'.github/workflows/b-transition.yml').read_text());job=w['jobs']['local-only']
  self.assertNotIn('environment',job);self.assertNotIn('secrets.',str(w));self.assertEqual(job['env']['PGHOST'],'127.0.0.1')
  self.assertNotIn('HOSTED',str(w));self.assertIn('force-avd-creation',str(w))
 def test_dispatch_cannot_activate_a_hosted_transition(self):
  text=(ROOT/'.github/workflows/h131-investigation.yml').read_text()
  self.assertIn('B_TRANSITION_LOCAL',text);self.assertNotIn('B_TRANSITION_HOSTED',text)
 def test_gate_blocks_payload_mutations_and_uses_approved_engine(self):
  gate=(ROOT/'app/src/androidTest/java/br/com/diariodeclasse/BTransitionPreparation.kt').read_text()
  for name in ('push','pushC','pushD','pushE','stageE','resolveE'):self.assertIn('override suspend fun '+name+'(',gate)
  test=(ROOT/'app/src/androidTest/java/br/com/diariodeclasse/BTransitionNetworkTest.kt').read_text()
  for call in ('engine.activateC()','engine.activateD()','engine.activateE()'):self.assertIn(call,test)
  self.assertNotIn('UPDATE academic_scope',test);self.assertIn('PREPARED_C',test)
if __name__=='__main__':unittest.main()
