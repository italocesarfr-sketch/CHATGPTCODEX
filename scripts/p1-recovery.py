#!/usr/bin/env python3
"""P1 opt-in, private input and two brand-new CI AVDs. No HTTP in this Python runner."""
import argparse,base64,hashlib,importlib.util,json,os,re,shutil,stat,subprocess,time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('h05',ROOT/'scripts/h05-recovery.py');h05=importlib.util.module_from_spec(spec);spec.loader.exec_module(h05)
auth=h05.auth
REPORT=ROOT/'build/p1/result.json'
class Blocked(Exception):pass
def need(ok,code):
 if not ok:raise Blocked(code)
def context(env):
 auth.scope(env);mode=env.get('NOCTUA_P1_MODE','PREFLIGHT');need(mode in ('LOCAL','PREFLIGHT','HOSTED'),'MODE')
 if mode=='HOSTED':need(env.get('NOCTUA_P1_AUTHORIZED')=='YES','P1_AUTHORIZATION_REQUIRED')
 return mode
def folder(env):return auth.private_dir(env).parent/'noctua-p1-private'
def public(env,status,**extra):
 mode=env.get('NOCTUA_P1_MODE','PREFLIGHT')
 out=dict(case='P1',mode=mode if mode in ('LOCAL','HOSTED','PREFLIGHT')else None,overall=status,
  project_ref=auth.TARGET if mode!='LOCAL'else None,credentials_redacted=True,
  source_sha=env.get('GITHUB_SHA')if re.fullmatch('[0-9a-f]{40}',env.get('GITHUB_SHA',''))else None,
  hosted_requests=False if mode!='HOSTED' or status=='PREREQUISITES_READY_NOT_EXECUTED'else None,**extra)
 REPORT.parent.mkdir(parents=True,exist_ok=True);REPORT.write_text(json.dumps(out,indent=2)+'\n')
 if env.get('GITHUB_STEP_SUMMARY'):Path(env['GITHUB_STEP_SUMMARY']).write_text('## P1 — '+status+'\n\nOnly sanitized counts, hashes and fixed diagnostics.\n')
def prepare(env):
 mode=context(env)
 if mode=='LOCAL':
  data=dict(accounts=[dict(email='a@example.invalid',password='Synthetic-P1-2026',expectedId='88888888-8888-4888-8888-888888888888'),dict(email='b@example.invalid',password='Synthetic-P1-2026',expectedId='99999999-9999-4999-8999-999999999999')],fixtureClassId='d-source')
 else:
  gradle=(ROOT/'app/build.gradle.kts').read_text();models=(ROOT/'app/src/main/java/br/com/diariodeclasse/auth/AuthModels.kt').read_text()
  prod=re.search(r'val productionPublishable = "([^"]+)"',gradle).group(1);legacy=re.search(r'const val LEGACY_OWNER_ID = "([^"]+)"',models).group(1)
  value=auth.validate(env.get('NOCTUA_HOMOLOGACAO_PUBLISHABLE_KEY',''),env.get('NOCTUA_HOMOLOGACAO_AUTH_TEST_JSON',''),prod,legacy,require_zero_probe=False)
  data={k:value[k]for k in ('accounts','fixtureClassId')}
 if mode=='PREFLIGHT':public(env,'PREREQUISITES_READY_NOT_EXECUTED');return
 p=folder(env);p.mkdir(mode=0o700);(p/'.private-p1').touch(mode=0o600)
 data.update(mode=mode,consentP1='YES')
 fd=os.open(p/'input.json',os.O_CREAT|os.O_EXCL|os.O_WRONLY,0o600)
 with os.fdopen(fd,'w')as stream:json.dump(data,stream)
 with open(env['GITHUB_ENV'],'a')as stream:stream.write('NOCTUA_P1_INPUT_FILE='+str(p/'input.json')+'\n')
 public(env,'PREPARED_NOT_EXECUTED')
FLAGS={'clean_install_verified','ui_login_a_verified','ui_login_b_verified','automatic_startup_verified','ids_payloads_equal','revisions_equal','receipts_equal','references_valid','queues_empty','remote_unchanged','audits_unchanged','a_b_a_isolated','sessions_cleanup_verified','private_input_removed'}
NUMBERS={'a_head','b_head','a_records','b_records','total_ms','blocked_writes','read_calls','login_calls','logout_calls'}
STAGES={'INPUT','CLEAN','LOGIN_A','RECOVER_A','VERIFY_A','LOGIN_B','RECOVER_B','VERIFY_B','RETURN_A','VERIFY_RETURN','DONE'}
CODES={'AUTH','IDENTITY','DATA','REVISION','RECEIPT','QUEUE','LEGACY','REFERENCES','WRITE_ATTEMPT','DEVICE','NOT_CLEAN','FIXTURE','ISOLATION','REMOTE_CHANGED','CLEANUP','UNEXPECTED','TIMEOUT','P1_AUTHORIZATION_REQUIRED','SCOPE','READ_LIMIT','REPORT_LIMIT','TRANSPORT','RESPONSE','INPUT','ACCOUNT','AUTH_LIMIT','SESSION','READ_HTTP','AUTH_SCOPE','LIMIT'}
def proof(raw,mode):
 need(len(raw)<=65536,'REPORT_LIMIT')
 try:j=json.loads(raw)
 except Exception:raise Blocked('REPORT_JSON')from None
 allowed=FLAGS|NUMBERS|{'case','mode','status','stage','failure_code','timings_ms','a_hash','b_hash','types_a','types_b','eligibility'}
 need(isinstance(j,dict)and not set(j)-allowed,'REPORT_FIELDS')
 need(j.get('case')=='P1'and j.get('mode')==mode and j.get('status')in ('PASSED','FAILED'),'REPORT_IDENTITY')
 need(j.get('stage')in STAGES and j.get('failure_code')in {None}|CODES|{'P1_'+x for x in CODES},'REPORT_STAGE')
 if 'eligibility'in j:
  observed=j['eligibility'];need(isinstance(observed,dict)and not set(observed)-{'A','B'},'REPORT_ELIGIBILITY')
  for row in observed.values():
   need(isinstance(row,dict)and set(row)=={'protocol','scope','test_allowed','ready'},'REPORT_ELIGIBILITY')
   need(row['protocol']in ('V1','V2','OTHER')and row['scope']in ('B','C','D','E','OTHER'),'REPORT_ELIGIBILITY')
   need(type(row['test_allowed'])is bool and type(row['ready'])is bool,'REPORT_ELIGIBILITY')
   need(row['ready']==(row['protocol']=='V2'and row['scope']=='E'and row['test_allowed']),'REPORT_ELIGIBILITY')
  if j['status']=='PASSED':need(set(observed)=={'A','B'}and all(v['ready']for v in observed.values()),'REPORT_ELIGIBILITY')
 for k in FLAGS&j.keys():need(type(j[k])is bool,'REPORT_FLAG')
 for k in NUMBERS&j.keys():need(type(j[k])is int and 0<=j[k]<=600000,'REPORT_NUMBER')
 for k in {'a_hash','b_hash'}&j.keys():need(type(j[k])is str and bool(re.fullmatch('[0-9a-f]{64}',j[k])),'REPORT_HASH')
 t=j.get('timings_ms');need(isinstance(t,dict)and not set(t)-STAGES and all(type(v)is int and 0<=v<=300000 for v in t.values()),'REPORT_TIMINGS')
 for key in {'types_a','types_b'}&j.keys():
  rows=j[key];need(isinstance(rows,list)and len(rows)==15 and all(isinstance(r,dict)for r in rows)and {r.get('kind')for r in rows}==h05.KINDS,'REPORT_TYPES')
  for r in rows:
   need(set(r)=={'kind','local_count','remote_count','local_hash','remote_hash','present'},'REPORT_TYPE_FIELDS')
   need(all(type(r[k])is int and 0<=r[k]<=1000 for k in ('local_count','remote_count')),'REPORT_COUNTS')
   need(type(r['present'])is bool and r['present']==(r['remote_count']>0),'REPORT_PRESENT')
   need(all(type(r[k])is str and bool(re.fullmatch('[0-9a-f]{64}',r[k]))for k in ('local_hash','remote_hash')),'REPORT_HASH')
   need(r['local_count']==r['remote_count']and r['local_hash']==r['remote_hash'],'REPORT_MISMATCH')
 if j['status']=='PASSED':
  need(FLAGS<=j.keys()and all(j[k]is True for k in FLAGS)and NUMBERS<=j.keys(),'INCOMPLETE_PROOF')
  need(j['stage']=='DONE'and j['failure_code']is None and j['blocked_writes']==0 and j['login_calls']==3 and j['logout_calls']==3 and j['read_calls']<=512,'INCOMPLETE_PROOF')
  need(1<=j['a_records']<=1000 and 0<=j['b_records']<=1000 and 1<=j['a_head']<=256 and 0<=j['b_head']<=256,'INCOMPLETE_PROOF')
  for side in ('a','b'):need('types_'+side in j and sum(r['local_count']for r in j['types_'+side])==j[side+'_records'],'INCOMPLETE_PROOF')
 return j

def identities(env):
 need(env.get('RUNNER_ENVIRONMENT')=='github-hosted'and env.get('CI')=='true','DISPOSABLE_RUNNER_REQUIRED')
 mode=context(env).lower();prefix=f'noctua_p1_{mode}_{env.get("GITHUB_RUN_ID","")}_{env.get("GITHUB_RUN_ATTEMPT","")}'
 need(bool(re.fullmatch(r'noctua_p1_(local|hosted)_[0-9]+_[0-9]+',prefix)),'AVD_NAME')
 return [prefix+'_first',prefix+'_second']
def execute(env):
 mode=context(env);need(mode in ('LOCAL','HOSTED'),'PREFLIGHT_CANNOT_EXECUTE');names=identities(env);p=folder(env);source=p/'input.json'
 need(env.get('NOCTUA_P1_INPUT_FILE')==str(source)and source.is_file()and stat.S_IMODE(source.stat().st_mode)==0o600,'PRIVATE_INPUT')
 def command(args,*,input=None,timeout=60,check=True):
  r=subprocess.run(args,input=input,capture_output=True,timeout=timeout)
  if check:need(r.returncode==0,'COMMAND_FAILED')
  return r.stdout
 def adb(*args,**kw):return command(['adb',*args],**kw)
 def devices():return [x.split()for x in adb('devices').decode().splitlines()[1:]if x.strip()]
 def device(serial,name):
  need(adb('-s',serial,'shell','getprop','ro.kernel.qemu').decode().strip()=='1','NOT_EMULATOR')
  need(adb('-s',serial,'emu','avd','name').decode().splitlines()[0].strip()==name,'WRONG_AVD')
 need(devices()==[['emulator-5554','device']],'UNEXPECTED_DEVICE');device('emulator-5554',names[0])
 image=Path(env['ANDROID_HOME'])/'system-images/android-30';tags=[d.name for d in sorted(image.iterdir())if(d/'x86_64/package.xml').is_file()];need(bool(tags),'IMAGE_MISSING')
 need(not(Path(env.get('ANDROID_AVD_HOME',str(Path.home()/'.android/avd')))/(names[1]+'.avd')).exists(),'AVD_ALREADY_EXISTS')
 command(['avdmanager','create','avd','--name',names[1],'--package','system-images;android-30;'+tags[0]+';x86_64'],input=b'no\n')
 output=open(p/'secondary-private.log','wb')
 process=subprocess.Popen([str(Path(env['ANDROID_HOME'])/'emulator/emulator'),'-avd',names[1],'-port','5556','-no-window','-no-audio','-no-boot-anim','-no-snapshot','-gpu','swiftshader_indirect','-feature','-Vulkan','-memory','1536','-cores','2'],stdin=subprocess.DEVNULL,stdout=output,stderr=subprocess.STDOUT)
 reports=[];native=[]
 try:
  adb('-s','emulator-5556','wait-for-device',timeout=180);deadline=time.monotonic()+180;stable=0
  while time.monotonic()<deadline:
   try:
    ready=adb('-s','emulator-5556','shell','getprop','sys.boot_completed',timeout=5).strip()==b'1'
    float(adb('-s','emulator-5556','shell','settings','get','global','window_animation_scale',timeout=5).strip())
    ready=ready and b'package:'in adb('-s','emulator-5556','shell','pm','path','android',timeout=5)
    stable=stable+1 if ready else 0
    if stable>=2:break
   except (Blocked,ValueError,subprocess.TimeoutExpired):stable=0
   time.sleep(1)
  need(stable>=2,'SECONDARY_BOOT');need(devices()==[['emulator-5554','device'],['emulator-5556','device']],'UNEXPECTED_DEVICE')
  package='br.com.diariodeclasse.homologacao'if mode=='HOSTED'else'br.com.diariodeclasse';flavor='homologacao'if mode=='HOSTED'else'debug'
  target=ROOT/f'app/build/outputs/apk/{flavor}/app-{flavor}.apk';test=ROOT/f'app/build/outputs/apk/androidTest/{flavor}/app-{flavor}-androidTest.apk'
  apk_hash=hashlib.sha256(target.read_bytes()).hexdigest();test_hash=hashlib.sha256(test.read_bytes()).hexdigest()
  tools=Path(env['ANDROID_HOME'])/'build-tools/35.0.0'
  badging=command([str(tools/'aapt'),'dump','badging',str(target)]).decode()
  identity=re.search(r"package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'",badging)
  need(identity is not None and identity[1]==package,'APK_IDENTITY')
  certs=[]
  for apk in (target,test):
   verify=command([str(tools/'apksigner'),'verify','--print-certs',str(apk)]).decode()
   cert=re.findall(r'Signer #1 certificate SHA-256 digest: ([0-9a-f]{64})',verify)
   need(len(cert)==1,'APK_SIGNATURE');certs.append(cert[0])
  need(certs[0]==certs[1],'TARGET_TEST_CERTIFICATE_DIFFER')
  apk_identity=dict(application_id=package,version_code=int(identity[2]),certificate_sha256=certs[0],signature_verified=True)
  # Future HOSTED target must pass the existing endpoint/DEX/manifest admission too.
  if mode=='HOSTED':command(['python3','scripts/validate-homologacao.py','--apk',str(target),'--build-tools',str(tools)])
  public(env,'ATTEMPT_STARTED')
  for index,serial in enumerate(('emulator-5554','emulator-5556')):
   device(serial,names[index])
   for identity in {'br.com.diariodeclasse','br.com.diariodeclasse.test','br.com.diariodeclasse.homologacao','br.com.diariodeclasse.homologacao.test'}:need(b'package:'not in adb('-s',serial,'shell','pm','path',identity,check=False),'APP_ALREADY_INSTALLED')
   adb('-s',serial,'shell','input','keyevent','82')
   for option in ('window_animation_scale','transition_animation_scale','animator_duration_scale'):adb('-s',serial,'shell','settings','put','global',option,'0')
   adb('-s',serial,'install',str(target),timeout=120);adb('-s',serial,'install','-t',str(test),timeout=120)
   adb('-s',serial,'shell','run-as',package,'mkdir','-p','files')
   adb('-s',serial,'shell','run-as',package,'sh','-c','"umask 077; cat > files/p1-private.json; chmod 600 files/p1-private.json"',input=source.read_bytes())
   need(adb('-s',serial,'shell','run-as',package,'stat','-c','%a','files/p1-private.json').decode().strip()=='600','DEVICE_INPUT_PERMISSIONS')
   cases='br.com.diariodeclasse.P1RecoveryProbeTest'+(',br.com.diariodeclasse.P1GuardTest'if mode=='LOCAL'else'')
   raw=adb('-s',serial,'shell','am','instrument','-w','-e','timeout_msec','360000','-e','p1Mode',mode,'-e','p1Authorized','YES'if mode=='HOSTED'else'NO','-e','p1Device','SECOND'if index else'FIRST','-e','class',cases,package+'.test/br.com.diariodeclasse.AuthTestRunner',timeout=420,check=False)
   (p/f'private-native-{index}.log').write_bytes(raw)
   diag=h05.native_diagnostics(raw,6 if mode=='LOCAL'else 1);native.append(diag)
   encoded=adb('-s',serial,'exec-out','run-as',package,'base64','files/p1-result.json')
   digest=adb('-s',serial,'exec-out','run-as',package,'sha256sum','files/p1-result.json')
   m=re.fullmatch(rb'([0-9a-f]{64})  files/p1-result\.json\s*',digest);need(m is not None and len(encoded)<=100000,'REPORT_DIGEST')
   decoded=base64.b64decode(b''.join(encoded.split()),validate=True);need(hashlib.sha256(decoded).hexdigest()==m.group(1).decode(),'REPORT_DIGEST')
   record=proof(decoded,mode);reports.append(record)
   public(env,'RUNNING',proofs=reports,instrumentation=native,apk_sha256=apk_hash,test_apk_sha256=test_hash,apk_identity=apk_identity)
   need(diag['ok_marker']and not any(diag[k]for k in ('process_crashed','instrumentation_failed','failures','exception_types'))and record['status']=='PASSED'and b'Assumption'not in raw,'ANDROID_PROOF_FAILED')
  need(all(set(r.get('eligibility',{}))=={'A','B'}and all(v['ready']for v in r['eligibility'].values())for r in reports),'ELIGIBILITY_EVIDENCE_MISSING')
  for key in ('a_hash','b_hash','a_head','b_head','a_records','b_records','types_a','types_b'):need(reports[0][key]==reports[1][key],'INSTALLATIONS_DIFFER')
  public(env,'PASSED_LOCAL_DISPOSABLE'if mode=='LOCAL'else'PASSED_HOSTED_PRESENT_TYPES',proofs=reports,instrumentation=native,apk_sha256=apk_hash,test_apk_sha256=test_hash,apk_identity=apk_identity,hosted_requests_executed=mode=='HOSTED',two_independent_installations=True)
 finally:
  for serial in ('emulator-5554','emulator-5556'):
   if 'package' in locals():adb('-s',serial,'shell','run-as',package,'rm','-f','files/p1-private.json',check=False)
  # Only our own secondary process, never an existing device/app.
  if process.poll()is None:
   process.terminate()
   try:process.wait(timeout=20)
   except subprocess.TimeoutExpired:process.kill();process.wait(timeout=10)
  output.close()
def clean(env):
 p=folder(env)
 if p.is_dir():need((p/'.private-p1').is_file(),'UNOWNED_TEMP');shutil.rmtree(p)
def main():
 parser=argparse.ArgumentParser();parser.add_argument('action',choices=['prepare','execute','clean']);args=parser.parse_args()
 try:globals()[args.action](os.environ)
 except Exception as e:
  code=str(e)if isinstance(e,(Blocked,auth.Blocked))and re.fullmatch('[A-Z0-9_]{1,80}',str(e))else'UNEXPECTED'
  # Preserve only a previously sanitized proof, never raw exception/log text.
  previous=json.loads(REPORT.read_text())if REPORT.is_file()else{}
  public(os.environ,'BLOCKED',blocking_code=code,**{k:previous[k]for k in ('proofs','instrumentation','apk_sha256','test_apk_sha256','apk_identity')if k in previous})
  print('P1_BLOCKED_'+code);raise SystemExit(1)from None
if __name__=='__main__':main()
