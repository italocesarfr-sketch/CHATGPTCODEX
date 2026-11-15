#!/usr/bin/env python3
"""Real Java proof with an EXISTING local debug fixture. No key generation or APK signing.
Not part of the protected workflow; missing fixture fails instead of reporting a skip/pass.
"""
import hashlib,os,subprocess,tempfile,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
PIN='6dbbba2d930eb7f7fbeff1ee82512f656251bbec9f4b31b6c5614528d29f1aae'
class JavaProof(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.source=Path(os.environ['NOCTUA_EXISTING_DEBUG_FIXTURE']).resolve()
  assert cls.source.name=='debug.keystore' and cls.source.is_file()
  cls.before=hashlib.sha256(cls.source.read_bytes()).digest();cls.folder=tempfile.TemporaryDirectory(prefix='noctua-existing-debug-proof-');cls.p=Path(cls.folder.name)
  cls.env=dict(os.environ,FIXTURE_STORE_PASSWORD='android');cls.alias='androiddebugkey'
  def call(args):return subprocess.run(list(map(str,args)),capture_output=True,check=True,env=cls.env,timeout=60).stdout
  cert=call(['keytool','-exportcert','-keystore',cls.source,'-alias',cls.alias,'-storepass:env','FIXTURE_STORE_PASSWORD'])
  cls.fingerprint=hashlib.sha256(cert).hexdigest();assert cls.fingerprint!=PIN
  # Convert only an existing debug fixture; never generate/import an official key.
  call(['keytool','-importkeystore','-noprompt','-srckeystore',cls.source,'-srcalias',cls.alias,'-srcstorepass:env','FIXTURE_STORE_PASSWORD','-srckeypass:env','FIXTURE_STORE_PASSWORD','-destkeystore',cls.p/'fixture.p12','-deststoretype','PKCS12','-destalias',cls.alias,'-deststorepass:env','FIXTURE_STORE_PASSWORD','-destkeypass:env','FIXTURE_STORE_PASSWORD'])
  (cls.p/'classes').mkdir();call(['javac','-d',cls.p/'classes',ROOT/'scripts/signing/SigningPreflight.java'])
 @classmethod
 def tearDownClass(cls):
  assert hashlib.sha256(cls.source.read_bytes()).digest()==cls.before
  cls.folder.cleanup()
 def verify(self,changes=None):
  e=dict(self.env,DIARY_RELEASE_STORE_FILE=str(self.p/'fixture.p12'),DIARY_RELEASE_STORE_PASSWORD='android',DIARY_RELEASE_KEY_ALIAS=self.alias,DIARY_RELEASE_KEY_PASSWORD='android',NOCTUA_EXPECTED_CERT=self.fingerprint)
  e.update(changes or {})
  r=subprocess.run(['java','-cp',str(self.p/'classes'),'SigningPreflight'],capture_output=True,env=e,timeout=30)
  self.assertEqual(b'',r.stderr);self.assertNotIn('android',r.stdout.decode().lower());return r
 def test_existing_fixture_key_proof_succeeds(self):
  r=self.verify();self.assertEqual(0,r.returncode);self.assertEqual(b'SIGNING_PREFLIGHT_OK\n',r.stdout)
 def test_fixture_can_never_match_permanent_certificate(self):
  r=self.verify(dict(NOCTUA_EXPECTED_CERT=PIN));self.assertEqual(1,r.returncode);self.assertIn(b'CERTIFICATE_MISMATCH',r.stdout)
 def test_wrong_store_password_is_redacted(self):self.assertEqual(1,self.verify(dict(DIARY_RELEASE_STORE_PASSWORD='wrong-private-fixture-input')).returncode)
 def test_wrong_key_password_is_redacted(self):self.assertEqual(1,self.verify(dict(DIARY_RELEASE_KEY_PASSWORD='wrong-private-fixture-input')).returncode)
 def test_unknown_alias_is_redacted(self):self.assertEqual(1,self.verify(dict(DIARY_RELEASE_KEY_ALIAS='private-fixture-alias')).returncode)
 def test_missing_password_is_rejected(self):self.assertEqual(1,self.verify(dict(DIARY_RELEASE_KEY_PASSWORD='')).returncode)
 def test_corrupt_store_is_redacted(self):
  path=self.p/'invalid.p12';path.write_bytes(b'not-a-key-store')
  self.assertEqual(1,self.verify(dict(DIARY_RELEASE_STORE_FILE=str(path))).returncode)
if __name__=='__main__':unittest.main()
