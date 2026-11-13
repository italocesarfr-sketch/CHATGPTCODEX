#!/usr/bin/env python3
"""Execute existing D/E/decision suites with H13.2 ACTUALLY installed throughout.
No test skipped/rewritten. Existing domain JVM fixture remains mandatory.
"""
import json,sys,unittest
import common as c
b,f,bench,lab=c.fixtures();b.reset()
decisions=c.load('h132_decisions','scripts/test-phase-e-decisions.py')
f.execute(c.CANDIDATE.read_text())
expected=f.execute("SELECT md5(string_agg(prosrc,E'\n' ORDER BY proname)) FROM pg_proc WHERE pronamespace='public'::regnamespace AND proname IN('noctua_d_get','noctua_d_history_guard','noctua_d_graph');")
suite=unittest.TestSuite()
for cls in (f.d.PhaseDPostgresTest,f.PhaseEPostgresTest,decisions.PhaseEDecisionPostgresTest):suite.addTests(unittest.defaultTestLoader.loadTestsFromTestCase(cls))
result=unittest.TextTestRunner(verbosity=2).run(suite)
actual=f.execute("SELECT md5(string_agg(prosrc,E'\n' ORDER BY proname)) FROM pg_proc WHERE pronamespace='public'::regnamespace AND proname IN('noctua_d_get','noctua_d_history_guard','noctua_d_graph');")
assert expected==actual,'Regression suite replaced candidate helpers'
(c.ROOT/'build/h132/regressions.json').write_text(json.dumps(dict(tests=result.testsRun,failures=len(result.failures),errors=len(result.errors),skipped=len(result.skipped),candidate_installed_throughout=True),indent=2)+'\n')
b.reset();sys.exit(0 if result.wasSuccessful()else 1)
