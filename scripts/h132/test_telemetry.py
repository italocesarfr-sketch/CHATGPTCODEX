import importlib.util,json,os,tempfile,unittest,subprocess,sys
from pathlib import Path
spec=importlib.util.spec_from_file_location('telemetry',Path(__file__).resolve().parents[1]/'benchmark_telemetry.py')
t=importlib.util.module_from_spec(spec);spec.loader.exec_module(t)
class TelemetryTest(unittest.TestCase):
 def test_checkpoint_survives_assertion_and_preserves_stage_volume_profile(self):
  with tempfile.TemporaryDirectory()as d:
   p=Path(d)/'journal.json';j=t.Journal(p,'synthetic')
   try:
    j.event(stage='commit-result',records=5171,parts=41,measurement=dict(outcome='57014-atomic-rollback'),profile=[dict(funcname='noctua_d_graph',calls=1)],profile_partial=True)
    raise AssertionError('simulated capacity blocker')
   except AssertionError:pass
   r=json.loads(p.read_text());self.assertEqual(5171,r['events'][0]['records']);self.assertEqual('commit-result',r['events'][0]['stage']);self.assertTrue(r['events'][0]['profile_partial'])
   self.assertEqual(0o600,p.stat().st_mode&0o777)
   j.event(stage='rollback-verified',rollback_verified=True)
   self.assertEqual(2,len(json.loads(p.read_text())['events']));self.assertEqual([p],list(Path(d).iterdir()))
 def test_checkpoint_survives_real_process_exit(self):
  with tempfile.TemporaryDirectory()as d:
   p=Path(d)/'journal.json'
   script="import importlib.util,os,sys; s=importlib.util.spec_from_file_location('t',sys.argv[1]);t=importlib.util.module_from_spec(s);s.loader.exec_module(t);j=t.Journal(sys.argv[2],'synthetic');j.event(stage='commit-start',records=5171,parts=41);os._exit(7)"
   process=subprocess.run([sys.executable,'-c',script,str(Path(__file__).resolve().parents[1]/'benchmark_telemetry.py'),str(p)],capture_output=True)
   self.assertEqual(7,process.returncode);self.assertEqual('commit-start',json.loads(p.read_text())['events'][0]['stage']);self.assertEqual([p],list(Path(d).iterdir()))
 def test_private_payload_fields_are_not_accepted(self):
  with tempfile.TemporaryDirectory()as d:
   j=t.Journal(Path(d)/'journal.json','synthetic')
   for field in ('token','payload','email','password','request','response'):
    with self.assertRaises(AssertionError):j.event(**{field:'fictitious-not-a-secret'})
if __name__=='__main__':unittest.main(verbosity=2)
