#!/usr/bin/env python3
"""Static guardrails for LOCAL-only preparation. Actual PostgreSQL/Room proof runs in CI."""
from pathlib import Path
import unittest,yaml
ROOT=Path(__file__).resolve().parents[1]
class Safety(unittest.TestCase):
 def test_effective_definition_already_has_monotonic_scope_guard(self):
  text=(ROOT/'supabase/migrations/202611020003_phase_d_scope.sql').read_text()
  start=text.index('CREATE OR REPLACE FUNCTION public.noctua_enable_academic_c(');end=text.index('-- Private projection',start)
  self.assertIn('IF scope NOT IN(2,3)',text[start:end])
  self.assertFalse((ROOT/'supabase/migrations/202611150001_scope_transition_guard.sql').exists())
 def test_http_candidate_changes_only_three_conflict_codes(self):
  import re
  text=(ROOT/'supabase/migrations/202611150002_scope_conflict_http.sql').read_text()
  actual=re.findall(r'CREATE OR REPLACE FUNCTION public.noctua_enable_academic_[cde]\(.*?\$\$;',text,re.S)
  self.assertEqual(len(actual),3)
  for body,(name,file)in zip(actual,[('c','202611020003_phase_d_scope.sql'),('d','202611020003_phase_d_scope.sql'),('e','202611030001_phase_e_paging.sql')]):
   old=(ROOT/'supabase/migrations'/file).read_text();start=old.index('CREATE OR REPLACE FUNCTION public.noctua_enable_academic_'+name+'(');end=old.index('$$;',start)+3
   self.assertEqual(body,old[start:end].replace("ERRCODE='40001'","ERRCODE='PT409'"))
  self.assertNotIn('GRANT ',text);self.assertNotIn('ALTER TABLE',text)
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
