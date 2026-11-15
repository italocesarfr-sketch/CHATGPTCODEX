#!/usr/bin/env python3
import importlib.util,json,tempfile,unittest,zipfile
from pathlib import Path
import yaml
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('upgrade',ROOT/'scripts/official-upgrade-local.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
class Safety(unittest.TestCase):
 def test_original_is_c40_and_not_c39(self):
  self.assertEqual(m.PIN['versionCode'],40);self.assertEqual(m.PIN['artifactId'],13053517551);self.assertEqual(m.PIN['runId'],41228898103)
 def test_original_zip_entries_must_remain_identical_when_resigned(self):
  with tempfile.TemporaryDirectory()as d:
   a=Path(d)/'a.zip';b=Path(d)/'b.zip'
   for p,data in ((a,b'original-dex'),(b,b'altered-dex')):
    with zipfile.ZipFile(p,'w')as z:z.writestr('classes.dex',data)
   self.assertNotEqual(m.payloads(a),m.payloads(b));self.assertEqual(m.payloads(a),m.payloads(a))
 def test_workflow_has_no_environment_secrets_or_apk_artifact(self):
  t=(ROOT/'.github/workflows/official-upgrade.yml').read_text();w=yaml.safe_load(t);job=w['jobs']['local-only']
  self.assertNotIn('environment',job);self.assertNotIn('secrets.',t)
  upload=[s for s in job['steps']if s.get('uses')=='actions/upload-artifact@v4'][0]
  self.assertEqual(upload['with']['path'],'build/official-upgrade/*.json')
 def test_only_new_avd_no_clear_uninstall_downgrade_or_existing_devices(self):
  s=(ROOT/'scripts/official-upgrade-local.py').read_text()
  self.assertIn('h.disposable(os.environ,device,adb)',s);self.assertIn('EXISTING_APP_STOP',s)
  self.assertNotIn("'uninstall'",s);self.assertNotIn("'clear'",s);self.assertNotIn("'install','-r','-d'",s)
 def test_native_approval_requires_success_without_skips(self):
  s=(ROOT/'scripts/official-upgrade-local.py').read_text();self.assertIn("b'Assumption'not in raw",s);self.assertIn("need(len(stages)==10",s)
 def test_release_eligibility_and_legacy_conflict_remain_protected(self):
  s=(ROOT/'app/src/androidTest/java/br/com/diariodeclasse/C40BoundUpgradeProbeTest.kt').read_text()
  for check in ('assertFalse(br.com.diariodeclasse.sync.academic.academicTestEligible(w))','assertNull(w.database.academicDao().control())','frozen-synthetic-payload','KeystoreAuthVault','manager.needsClaim','NET_CAPABILITY_INTERNET'):self.assertIn(check,s)
 def test_preparation_never_uses_permanent_key_environment(self):
  s=(ROOT/'scripts/official-upgrade-local.py').read_text();self.assertNotIn('DIARY_KEYSTORE_BASE64',s);self.assertIn("fingerprint!=PIN['certificateSha256']",s)
if __name__=='__main__':unittest.main()
