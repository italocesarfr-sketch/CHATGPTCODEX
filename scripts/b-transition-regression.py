#!/usr/bin/env python3
"""Run existing PostgreSQL C/D/E suites with the candidate installed AFTER imports."""
import os,sys,importlib.util,io,json,unittest
from pathlib import Path
root=Path(__file__).resolve().parents[1]
assert os.environ.get('GITHUB_ACTIONS')=='true'and os.environ.get('CI')=='true'and os.environ.get('PGHOST')=='127.0.0.1'
assert len(sys.argv)==2 and sys.argv[1]in ('c','d','e');phase=sys.argv[1]
assert os.environ.get('PGDATABASE')=='noctua_phase_'+phase+'_test'
spec=importlib.util.spec_from_file_location('previous_phase',root/f'scripts/test-phase-{phase}-postgres.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
m.execute((root/'supabase/migrations/202611150001_scope_transition_guard.sql').read_text())
suite=unittest.defaultTestLoader.loadTestsFromModule(m)
def install_before_case(suite):
 for case in suite:
  if isinstance(case,unittest.TestSuite):install_before_case(case)
  else:
   original=case.setUp
   def setup(original=original):
    original();m.execute((root/'supabase/migrations/202611150001_scope_transition_guard.sql').read_text())
   case.setUp=setup
install_before_case(suite) # The original C reapplication test deliberately reinstalls the older function.
stream=io.StringIO();result=unittest.TextTestRunner(stream=stream).run(suite)
report=dict(phase=phase,failed_cases=[t._testMethodName for t,_ in result.failures+result.errors],tests=result.testsRun,failed=len(result.failures)+len(result.errors),skipped=len(result.skipped),passed=result.testsRun-len(result.failures)-len(result.errors)-len(result.skipped),hosted_requests=False)
p=root/'build/b-transition';p.mkdir(parents=True,exist_ok=True);(p/(phase+'-regression.json')).write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report));assert result.wasSuccessful()and not result.skipped
