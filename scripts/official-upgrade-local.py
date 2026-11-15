#!/usr/bin/env python3
"""Original c40 bytecode + disposable signing ONLY. No official APK artifact or hosted HTTP."""
import hashlib,importlib.util,json,os,re,subprocess,sys,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'scripts'))
from apk_identity import identity
spec=importlib.util.spec_from_file_location('h05',ROOT/'scripts/h05-recovery.py');h=importlib.util.module_from_spec(spec);spec.loader.exec_module(h)
PIN=json.loads((ROOT/'docs/baseline/noctua-release-0.21.0.json').read_text())
STEP='INPUT'
ADB_ERRORS=[]
PASSWORD='discardable-c40-ci-only-not-a-permanent-secret'
def need(ok,code):
 if not ok:raise RuntimeError(code)
def scope():
 h.auth.scope(os.environ);need(os.environ.get('CI')=='true'and os.environ.get('RUNNER_ENVIRONMENT')=='github-hosted','CI_ONLY')
 need(os.environ.get('GITHUB_RUN_ATTEMPT')=='1','NO_AUTOMATIC_REPLAY')
def paths():
 return Path(os.environ['RUNNER_TEMP'])/'noctua-c40-upgrade-private',Path(os.environ['ANDROID_HOME'])/'build-tools/35.0.0'
def run(args,**kwargs):return subprocess.run(list(map(str,args)),check=True,capture_output=True,**kwargs).stdout
def payloads(path):
 with zipfile.ZipFile(path)as z:return {n:hashlib.sha256(z.read(n)).hexdigest()for n in z.namelist()}
def original(path,tools):
 actual=identity(path);need(all(actual[k]==PIN[k]for k in ('apkSha256','certificateSha256')),'ORIGINAL_HASH_OR_CERT')
 run([tools/'apksigner','verify',path]);badging=run([tools/'aapt','dump','badging',path]).decode()
 need("name='br.com.diariodeclasse' versionCode='40' versionName='0.21.0'"in badging,'ORIGINAL_IDENTITY')
 return actual
def prepare():
 scope();private,tools=paths();private.mkdir(mode=0o700,exist_ok=False)
 source=ROOT/'.c40-baseline/app-release.apk';old=original(source,tools)
 env=dict(os.environ,NOCTUA_C40_PASSWORD=PASSWORD)
 run(['keytool','-genkeypair','-noprompt','-storetype','PKCS12','-keystore',private/'disposable.p12','-alias','disposable','-keyalg','RSA','-keysize','2048','-validity','2','-dname','CN=Noctua isolated c40 update test','-storepass:env','NOCTUA_C40_PASSWORD','-keypass:env','NOCTUA_C40_PASSWORD'],env=env)
 cert=run(['keytool','-exportcert','-keystore',private/'disposable.p12','-alias','disposable','-storepass:env','NOCTUA_C40_PASSWORD'],env=env)
 fingerprint=hashlib.sha256(cert).hexdigest();need(fingerprint!=PIN['certificateSha256'],'DISPOSABLE_REQUIRED')
 env.update(DIARY_RELEASE_STORE_FILE=str(private/'disposable.p12'),DIARY_RELEASE_STORE_PASSWORD=PASSWORD,DIARY_RELEASE_KEY_ALIAS='disposable',DIARY_RELEASE_KEY_PASSWORD=PASSWORD,DIARY_RELEASE_CERT_SHA256=fingerprint,NOCTUA_AUTH_LIVE_FILE='',NOCTUA_HOMOLOGACAO_AUTH_TEST_FILE='',NOCTUA_HOMOLOGACAO_AUTH_TEST_AUTHORIZED='')
 with(private/'build.log').open('wb')as log:
  result=subprocess.run([str(ROOT/'gradlew'),'--no-daemon','assembleRelease','assembleDebugAndroidTest'],cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT)
 need(result.returncode==0,'LOCAL_BUILD_FAILED')
 def sign(source,target):
  run([tools/'apksigner','sign','--v1-signing-enabled','false','--v2-signing-enabled','true','--v3-signing-enabled','false','--v4-signing-enabled','false','--ks',private/'disposable.p12','--ks-type','PKCS12','--ks-key-alias','disposable','--ks-pass','env:NOCTUA_C40_PASSWORD','--key-pass','env:NOCTUA_C40_PASSWORD','--out',target,source],env=env);run([tools/'apksigner','verify',target])
 sign(source,private/'old.apk');need(payloads(source)==payloads(private/'old.apk'),'ORIGINAL_PAYLOAD_CHANGED')
 sign(ROOT/'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk',private/'tests.apk')
 candidate=ROOT/'app/build/outputs/apk/release/app-release.apk';new=identity(candidate);need(new['certificateSha256']==fingerprint,'LOCAL_CERT')
 run([tools/'apksigner','verify',candidate]);meta=run([tools/'aapt','dump','badging',candidate]).decode()
 need("name='br.com.diariodeclasse'"in meta and 'application-debuggable'not in meta,'RELEASE_VARIANT_REQUIRED')
 version=int(re.search(r"versionCode='([0-9]+)'",meta)[1]);need(version>40,'VERSION')
 out=ROOT/'build/official-upgrade';out.mkdir(parents=True,exist_ok=True)
 (out/'identity.json').write_text(json.dumps(dict(source_sha=os.environ['GITHUB_SHA'],original=old,original_version_code=40,candidate=new,candidate_version_code=version,signing='DISPOSABLE_ONLY',original_payloads_unchanged=True,official_signature_tested=False,hosted_requests=False),indent=2)+'\n')
 print('C40_LOCAL_PREPARED_NO_PERMANENT_KEY')
def execute():
 scope();private,tools=paths();out=ROOT/'build/official-upgrade';device='emulator-5554'
 def state(value):
  global STEP
  STEP=value
 def adb(*args,check=True):
  r=subprocess.run(['adb',*args],capture_output=True,timeout=180)
  if check and r.returncode!=0:
   (private/'adb-failure.log').write_bytes(r.stdout+r.stderr)
   ADB_ERRORS.extend(s for s in ('SecurityException','Permission Denial','INSTALL_FAILED_UPDATE_INCOMPATIBLE','INSTALL_FAILED_VERSION_DOWNGRADE','INSTALL_FAILED_INVALID_APK','INSTALL_FAILED_TEST_ONLY','device offline')if s.encode()in r.stdout+r.stderr)
   raise RuntimeError('ADB')
  return r.stdout
 state('DISPOSABLE_GUARD');h.disposable(os.environ,device,adb)
 state('CHECK_EMPTY_INSTALLATION')
 for pkg in ('br.com.diariodeclasse','br.com.diariodeclasse.test','br.com.diariodeclasse.homologacao'):
  need(b'package:'not in adb('-s',device,'shell','pm','path',pkg,check=False),'EXISTING_APP_STOP')
 state('WIFI_OFF');adb('-s',device,'shell','svc','wifi','disable')
 state('DATA_OFF');adb('-s',device,'shell','svc','data','disable')
 state('INSTALL_ORIGINAL');adb('-s',device,'install',str(private/'old.apk'))
 state('INSTALL_TESTS');adb('-s',device,'install','-t',str(private/'tests.apk'))
 stages=[]
 def probe(stage,argument,phase,klass):
  state(stage)
  raw=adb('-s',device,'shell','am','instrument','-w','-r','-e',argument,phase,'-e','class','br.com.diariodeclasse.'+klass,'br.com.diariodeclasse.test/br.com.diariodeclasse.AuthTestRunner',check=False)
  (private/(stage+'.log')).write_bytes(raw);diag=h.native_diagnostics(raw,1)
  diag['source_locations']=sorted(set(re.findall(r'(?:C40BoundUpgradeProbeTest|StableUpgradeProbeTest|BackupManager|AccountWorkspace|DiaryRepository|AuthTestRunner)\.kt:[0-9]+',raw.decode(errors='ignore'))))
  ok=diag['ok_marker']and not any(diag[k]for k in ('process_crashed','instrumentation_failed','failures','exception_types'))and b'Assumption'not in raw
  stages.append(dict(stage=stage,passed=ok,native=diag))
  (out/'android.json').write_text(json.dumps(dict(source_sha=os.environ['GITHUB_SHA'],signing='DISPOSABLE_ONLY',hosted_requests=False,stages=stages,passed=False),indent=2)+'\n')
  need(ok,'PROBE_'+stage)
 def stable(stage,phase):probe(stage,'stableUpgradePhase',phase,'StableUpgradeProbeTest')
 probe('OFFLINE_GUARD','c40BoundPhase','offline','C40BoundUpgradeProbeTest')
 stable('SEED_C40','seed');probe('BOUND_C40','c40BoundPhase','seed','C40BoundUpgradeProbeTest')
 adb('-s',device,'shell','am','force-stop','br.com.diariodeclasse')
 state('INSTALL_CANDIDATE');adb('-s',device,'install','-r',str(ROOT/'app/build/outputs/apk/release/app-release.apk'))
 stable('CHECK_MIGRATED','check');probe('CHECK_BOUND_CONFLICT','c40BoundPhase','check','C40BoundUpgradeProbeTest')
 for stage,phase in [('RESTORE','restore'),('REOPEN','check'),('DELETE_HISTORY','delete-history'),('REOPEN_HISTORY','reopen-history'),('DELETE_STUDENT','delete-student'),('REOPEN_STUDENT','reopen-student')]:
  adb('-s',device,'shell','am','force-stop','br.com.diariodeclasse');stable(stage,phase)
 need(len(stages)==11,'COUNT')
 result=json.loads((out/'android.json').read_text());result.update(passed=True,phases_passed=11,official_signature_tested=False,original_re_signed_copy=True)
 (out/'android.json').write_text(json.dumps(result,indent=2)+'\n');print('C40_DISPOSABLE_UPDATE_PASSED_NOT_PERMANENT_SIGNATURE')
if __name__=='__main__':
 try:
  need(len(sys.argv)==2 and sys.argv[1]in ('prepare','execute'),'MODE');globals()[sys.argv[1]]()
 except Exception as e:
  code=str(e)if type(e)is RuntimeError and re.fullmatch('[A-Z0-9_]+',str(e))else 'UNEXPECTED'
  out=ROOT/'build/official-upgrade';out.mkdir(parents=True,exist_ok=True)
  (out/'failure.json').write_text(json.dumps(dict(source_sha=os.environ.get('GITHUB_SHA')if re.fullmatch('[0-9a-f]{40}',os.environ.get('GITHUB_SHA',''))else None,stage=sys.argv[1]if len(sys.argv)==2 and sys.argv[1]in ('prepare','execute')else 'INPUT',blocking_code=code,operation=STEP,adb_error_types=sorted(set(ADB_ERRORS)),signing='DISPOSABLE_ONLY',hosted_requests=False,passed=False),indent=2)+'\n')
  print('C40_LOCAL_BLOCKED_'+code);raise SystemExit(1)from None
