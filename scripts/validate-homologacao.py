#!/usr/bin/env python3
"""Offline validation. No hosted requests, key values or session credentials printed."""
import argparse,hashlib,json,re,subprocess,zipfile
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--apk',type=Path)
p.add_argument('--build-tools',type=Path)
p.add_argument('--allow-missing-key',action='store_true',help='Static source/manifest only, never an APK admission')
a=p.parse_args()
app=ROOT/'app';identity='br.com.diariodeclasse.homologacao';project='sscexzxtqdcsqdlcytum'
source=(app/'build/generated/source/buildConfig/homologacao/br/com/diariodeclasse/BuildConfig.java').read_text()
def field(name):
 m=re.search(r'\b'+name+r'\s*=\s*"([^"\n]*)"',source)
 assert m,'Missing build field '+name
 return m.group(1)
assert field('APPLICATION_ID')==identity
assert field('SUPABASE_PROJECT_REF')==project
assert field('SUPABASE_URL')=='https://'+project+'.supabase.co'
assert field('AUTH_CALLBACK_SCHEME')==identity
assert re.search(r'\bHOMOLOGACAO\s*=\s*true',source)
assert re.search(r'\bDEBUG\s*=\s*Boolean.parseBoolean\("true"\)',source)
key=field('SUPABASE_PUBLISHABLE_KEY')
key_valid=bool(re.fullmatch(r'sb_publishable_[A-Za-z0-9_-]{16,200}',key)) and hashlib.sha256(key.encode()).hexdigest()!=field('PRODUCTION_KEY_FINGERPRINT')
assert key_valid or (a.allow_missing_key and not key)
assert not (a.apk and a.allow_missing_key)
manifest=app/'build/intermediates/merged_manifests/homologacao/processHomologacaoManifest/AndroidManifest.xml'
r=ET.parse(manifest).getroot();ns='{http://schemas.android.com/apk/res/android}'
assert r.get('package')==identity and r.get(ns+'sharedUserId')is None
application=r.find('application');assert application is not None
assert application.get(ns+'allowBackup')=='false' and application.get(ns+'debuggable')=='true'
assert application.get(ns+'usesCleartextTraffic')=='false'
assert application.get(ns+'networkSecurityConfig')is None,'Homologation must not inherit the local HTTP debug override'
assert application.find('provider').get(ns+'authorities')==identity+'.exports'
callback=[x for x in application.findall('activity/intent-filter/data')if x.get(ns+'host')=='auth']
assert len(callback)==1 and callback[0].get(ns+'scheme')==identity and callback[0].get(ns+'path')=='/callback'
result=dict(application_id=identity,project_ref=project,callback=identity+'://auth/callback',public_key_configured=key_valid,
            production_fallback=False,shared_user_id=False,allow_backup=False,cleartext=False,
            local_storage='Android application UID sandbox + existing per-account workspaces',
            apk_verified=False,hosted_tests_executed=False)
if a.apk:
 assert key_valid and a.build_tools and a.apk.is_file()
 badging=subprocess.check_output([str(a.build_tools/'aapt'),'dump','badging',str(a.apk)],text=True)
 assert "package: name='"+identity+"'"in badging
 assert "application-label:'NOcTuA Homologação'"in badging
 subprocess.run([str(a.build_tools/'apksigner'),'verify',str(a.apk)],check=True,stdout=subprocess.DEVNULL)
 gradle=(app/'build.gradle.kts').read_text()
 old_key=re.search(r'val productionPublishable = "([^"]+)"',gradle).group(1).encode()
 with zipfile.ZipFile(a.apk)as z:
  dex=b''.join(z.read(n)for n in z.namelist()if n.endswith('.dex'))
  assert project.encode()in dex and b'mpsgdchpgyxoqowfjoqm'not in dex and old_key not in dex
  for marker in [b'sb_secret_',b'BEGIN PRIVATE KEY',b'PrincipalTestClient',b'AcademicTestApplication',b'fixture-access-not-real']:
   assert marker not in dex,'Prohibited marker detected'
 result.update(apk_verified=True,apk_sha256=hashlib.sha256(a.apk.read_bytes()).hexdigest())
print(json.dumps(result,ensure_ascii=False,indent=2))
