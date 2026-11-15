#!/usr/bin/env python3
"""Protected permanent signing preparation. Never call this to bypass Environment policy.
Only policy/compile/unit checks run during preparation; real modes require new explicit authorization.
"""
import base64,hashlib,importlib.util,json,os,re,shutil,subprocess,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
PIN=json.loads((ROOT/'docs/baseline/noctua-release-0.21.0.json').read_text())
CERT='6dbbba2d930eb7f7fbeff1ee82512f656251bbec9f4b31b6c5614528d29f1aae'
REPO='italocesarfr-sketch/CHATGPTCODEX'
BRANCH='sync/academic-complete-0220'
WORKFLOW='h131-investigation.yml'
class Blocked(Exception):pass
def need(value,code):
 if not value:raise Blocked(code)
def context(env):
 need(env.get('GITHUB_REPOSITORY')==REPO and env.get('GITHUB_REF')=='refs/heads/'+BRANCH,'BRANCH_OR_REPOSITORY')
 need(env.get('GITHUB_EVENT_NAME')=='workflow_dispatch'and env.get('RUNNER_ENVIRONMENT')=='github-hosted','MANUAL_GITHUB_RUNNER_REQUIRED')
 need(env.get('GITHUB_RUN_ATTEMPT')=='1','NO_AUTOMATIC_REPLAY')
 need(env.get('GITHUB_WORKFLOW_REF')==REPO+'/.github/workflows/'+WORKFLOW+'@refs/heads/'+BRANCH,'WORKFLOW')
 need(re.fullmatch('[0-9a-f]{40}',env.get('NOCTUA_REVIEWED_SHA',''))is not None and env['NOCTUA_REVIEWED_SHA']==env.get('GITHUB_SHA'),'REVIEWED_SHA')
 mode=env.get('NOCTUA_SIGNING_MODE');need(mode in ('PREFLIGHT','CANDIDATE'),'MODE')
 need(env.get('NOCTUA_AUTHORIZE_PREFLIGHT')=='true','PREFLIGHT_AUTHORIZATION_REQUIRED')
 need(env.get('NOCTUA_AUTHORIZE_CANDIDATE')==('true'if mode=='CANDIDATE'else'false'),'CANDIDATE_AUTHORIZATION')
 return mode
def policy(environment,branches):
 need(isinstance(environment,dict)and environment.get('name')=='release','ENVIRONMENT_REQUIRED')
 need(environment.get('can_admins_bypass')is False,'ADMIN_BYPASS_MUST_BE_DISABLED')
 p=environment.get('deployment_branch_policy')
 need(isinstance(p,dict)and p.get('protected_branches')is False and p.get('custom_branch_policies')is True,'SELECTED_BRANCHES_REQUIRED')
 need(isinstance(branches,dict)and type(branches.get('total_count'))is int and isinstance(branches.get('branch_policies'),list),'BRANCH_POLICY_UNVERIFIED')
 rules=branches['branch_policies'];need(branches['total_count']==len(rules),'BRANCH_POLICY_PAGINATION')
 need(bool(rules)and all(r.get('type')=='branch'and r.get('name')in ('main',BRANCH)for r in rules)and any(r.get('name')==BRANCH for r in rules),'EXACT_BRANCH_POLICIES_REQUIRED')
 # Owner decision: no Required reviewers / Prevent self-review predicate here.
 # Manual event, reviewed SHA and separate authorizations remain mandatory in context().
 return dict(environment='release',selected_branch_verified=True,manual_authorization_required=True,independent_review_required_by_workflow=False,admin_bypass_disabled=True)
def child_env(extra=None,github_token=False):
 env={k:v for k,v in os.environ.items()if k not in ('STORE_B64','STORE_PASS','KEY_ALIAS','KEY_PASS','CERT_SHA','GH_TOKEN','GITHUB_TOKEN')and not k.startswith('DIARY_RELEASE_')}
 if github_token and os.environ.get('GH_TOKEN'):env['GH_TOKEN']=os.environ['GH_TOKEN']
 if extra:env.update(extra)
 return env
def github(path):
 need(path.startswith('repos/'+REPO+'/environments/release'),'GITHUB_ROUTE')
 r=subprocess.run(['gh','api','-H','Accept: application/vnd.github+json','-H','X-GitHub-Api-Version: 2022-11-28',path],capture_output=True,timeout=30,env=child_env(github_token=True))
 need(r.returncode==0,'ENVIRONMENT_POLICY_UNREADABLE');need(len(r.stdout)<=1024*1024,'POLICY_SIZE')
 try:return json.loads(r.stdout)
 except Exception:raise Blocked('POLICY_JSON')from None
def protect():
 context(os.environ)
 return policy(github('repos/'+REPO+'/environments/release'),github('repos/'+REPO+'/environments/release/deployment-branch-policies?per_page=100'))
def out():
 p=ROOT/'build/official-signed';p.mkdir(parents=True,exist_ok=True);return p
def publish(name,**fields):
 value=dict(source_sha=os.environ.get('GITHUB_SHA')if re.fullmatch('[0-9a-f]{40}',os.environ.get('GITHUB_SHA',''))else None,hosted_supabase_requests=False,**fields)
 (out()/(name+'.json')).write_text(json.dumps(value,indent=2)+'\n')
def private():return Path(os.environ['RUNNER_TEMP'])/'noctua-c40-upgrade-private'
def run(args,**kwargs):
 kwargs.setdefault('env',child_env())
 return subprocess.run(list(map(str,args)),check=True,capture_output=True,timeout=kwargs.pop('timeout',120),**kwargs).stdout
def cert_env(env):
 names=('STORE_B64','STORE_PASS','KEY_ALIAS','KEY_PASS','CERT_SHA')
 need(all(isinstance(env.get(k),str)and env[k]for k in names),'SIGNING_SECRETS_UNAVAILABLE')
 need(env['CERT_SHA'].replace(':','').lower()==CERT==PIN['certificateSha256'],'EXPECTED_CERTIFICATE_CONFIGURATION')
 need(len(env['STORE_B64'])<=4*1024*1024,'KEYSTORE_SIZE')
 try:raw=base64.b64decode(env['STORE_B64'],validate=True)
 except Exception:raise Blocked('KEYSTORE_ENCODING')from None
 need(0<len(raw)<=3*1024*1024 and raw[0]==0x30,'PKCS12_REQUIRED')
 return raw
def load_existing():
 mode=context(os.environ);protect()
 p=private();p.mkdir(mode=0o700,exist_ok=False)
 store=p/'existing.p12';raw=cert_env(os.environ)
 fd=os.open(store,os.O_CREAT|os.O_EXCL|os.O_WRONLY,0o600)
 with os.fdopen(fd,'wb')as f:f.write(raw)
 del raw
 env=child_env(dict(DIARY_RELEASE_STORE_FILE=str(store),DIARY_RELEASE_STORE_PASSWORD=os.environ['STORE_PASS'],DIARY_RELEASE_KEY_ALIAS=os.environ['KEY_ALIAS'],DIARY_RELEASE_KEY_PASSWORD=os.environ['KEY_PASS'],DIARY_RELEASE_CERT_SHA256=CERT,NOCTUA_EXPECTED_CERT=CERT))
 (p/'classes').mkdir(mode=0o700)
 run(['javac','-d',p/'classes',ROOT/'scripts/signing/SigningPreflight.java'],env=child_env())
 result=subprocess.run(['java','-cp',str(p/'classes'),'SigningPreflight'],env=env,capture_output=True,timeout=30)
 code=result.stdout.decode(errors='replace').strip()
 allowed={'SIGNING_PREFLIGHT_BLOCKED_KEYSTORE_OR_CREDENTIALS','SIGNING_PREFLIGHT_BLOCKED_CERTIFICATE_MISMATCH','SIGNING_PREFLIGHT_BLOCKED_KEY_PROOF'}
 need(result.returncode==0 and code=='SIGNING_PREFLIGHT_OK',code if code in allowed else'KEYSTORE_VERIFICATION')
 publish('preflight',passed=True,mode=mode,existing_key_available=True,certificate_matches_original=True,private_key_proof_verified=True,permanent_certificate_sha256=CERT)
 return env
def driver():
 spec=importlib.util.spec_from_file_location('device_driver',ROOT/'scripts/official-upgrade-local.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
 m.run=run # Public APK inspection never inherits the step's raw signing Secrets.
 return m
def prepare():
 need(context(os.environ)=='CANDIDATE','CANDIDATE_ONLY')
 store=private()/'existing.p12'
 try:
  env=load_existing();m=driver();tools=Path(os.environ['ANDROID_HOME'])/'build-tools/35.0.0'
  original=ROOT/'.c40-baseline/app-release.apk';old=m.original(original,tools)
  # Original permanent signature is preserved, NEVER resigned.
  shutil.copyfile(original,private()/'old.apk');need(m.identity(private()/'old.apk')==old,'ORIGINAL_CHANGED')
  env.update(NOCTUA_AUTH_LIVE_FILE='',NOCTUA_HOMOLOGACAO_AUTH_TEST_FILE='',NOCTUA_HOMOLOGACAO_AUTH_TEST_AUTHORIZED='')
  # Do not forward GitHub's base64/duplicate passwords to Gradle subprocesses.
  for k in ('STORE_B64','STORE_PASS','KEY_ALIAS','KEY_PASS','CERT_SHA','GH_TOKEN','GITHUB_TOKEN'):env.pop(k,None)
  with(private()/'build.log').open('wb')as log:
   result=subprocess.run([str(ROOT/'gradlew'),'--no-daemon','-PnoctuaOfficialCandidate=true','assembleRelease','assembleDebugAndroidTest'],cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT,timeout=1200)
  need(result.returncode==0,'SIGNED_BUILD_FAILED')
  candidate=ROOT/'app/build/outputs/apk/release/app-release.apk';new=m.identity(candidate)
  need(new['certificateSha256']==old['certificateSha256']==CERT,'CANDIDATE_CERTIFICATE')
  run([tools/'apksigner','verify','--verbose',candidate]);metadata=run([tools/'aapt','dump','badging',candidate]).decode()
  need("name='br.com.diariodeclasse' versionCode='45' versionName='0.22.0'"in metadata and 'application-debuggable'not in metadata,'CANDIDATE_IDENTITY')
  run(['python3',ROOT/'scripts/validate-auth-apk.py',candidate])
  run([tools/'apksigner','sign','--v1-signing-enabled','false','--v2-signing-enabled','true','--v3-signing-enabled','false','--v4-signing-enabled','false','--ks',store,'--ks-type','PKCS12','--ks-key-alias',env['DIARY_RELEASE_KEY_ALIAS'],'--ks-pass','env:DIARY_RELEASE_STORE_PASSWORD','--key-pass','env:DIARY_RELEASE_KEY_PASSWORD','--out',private()/'tests.apk',ROOT/'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'],env=env)
  run([tools/'apksigner','verify',private()/'tests.apk']);need(m.identity(private()/'tests.apk')['certificateSha256']==CERT,'INSTRUMENTATION_CERTIFICATE')
  publish('identity',passed=True,original=old,candidate=new,application_id='br.com.diariodeclasse',version_name='0.22.0',version_code=45,signing='PERMANENT',candidate_android_update_tested=False)
 finally:store.unlink(missing_ok=True) # Remove the key before running emulator/actions.
def preflight():
 try:load_existing()
 finally:(private()/'existing.p12').unlink(missing_ok=True)
def execute():
 need(context(os.environ)=='CANDIDATE','CANDIDATE_ONLY');protect()
 need(not(private()/'existing.p12').exists(),'KEY_MUST_BE_REMOVED')
 m=driver()
 os.environ.pop('GH_TOKEN',None);os.environ.pop('GITHUB_TOKEN',None)
 try:m.execute(signing='PERMANENT')
 except Exception:
  stage=m.STEP if re.fullmatch('[A-Z_]+',m.STEP)else'UNKNOWN'
  publish('device-blocked',passed=False,stage=stage)
  raise Blocked('SIGNED_DEVICE_PROOF_FAILED')from None
 finally:
  report=ROOT/'build/official-upgrade/android.json'
  if report.exists():shutil.copyfile(report,out()/'android.json')
def approved_proof(report,source_sha):
 need(isinstance(report,dict)and report.get('source_sha')==source_sha and report.get('signing')=='PERMANENT','PROOF_IDENTITY')
 need(report.get('passed')is True and report.get('phases_passed')==11 and report.get('official_signature_tested')is True and report.get('original_re_signed_copy')is False,'SIGNED_ANDROID_PROOF_REQUIRED')
 stages=report.get('stages');expected=['OFFLINE_GUARD','SEED_C40','BOUND_C40','CHECK_MIGRATED','CHECK_BOUND_CONFLICT','RESTORE','REOPEN','DELETE_HISTORY','REOPEN_HISTORY','DELETE_STUDENT','REOPEN_STUDENT']
 need(isinstance(stages,list)and [s.get('stage')for s in stages]==expected,'ALL_STAGES_REQUIRED')
 for s in stages:
  d=s.get('native',{})
  need(s.get('passed')is True and d.get('expected_tests')==1 and d.get('ok_marker')is True and d.get('process_crashed')is False and d.get('instrumentation_failed')is False and d.get('failures')is False and d.get('exception_types')==[],'NATIVE_PROOF_REQUIRED')
 return True
def approve():
 need(context(os.environ)=='CANDIDATE','CANDIDATE_ONLY');protect()
 m=driver();candidate=ROOT/'app/build/outputs/apk/release/app-release.apk'
 report=json.loads((ROOT/'build/official-upgrade/android.json').read_text());prepared=json.loads((out()/'identity.json').read_text())
 approved_proof(report,os.environ['GITHUB_SHA'])
 need(prepared.get('source_sha')==os.environ['GITHUB_SHA'],'PREPARED_SHA')
 need(m.identity(candidate)==prepared['candidate']and prepared['candidate']['certificateSha256']==CERT,'APK_CHANGED_AFTER_PROOF')
 run([Path(os.environ['ANDROID_HOME'])/'build-tools/35.0.0/apksigner','verify',candidate])
 final=out()/'candidate';final.mkdir(mode=0o700,exist_ok=False)
 shutil.copyfile(candidate,final/'noctua-0.22.0-c45-candidato.apk')
 (final/'apk.sha256').write_text(prepared['candidate']['apkSha256']+'  noctua-0.22.0-c45-candidato.apk\n')
 prepared.update(candidate_android_update_tested=True,production_distribution_approved=False,owner_device_tested=False)
 (final/'identity.json').write_text(json.dumps(prepared,indent=2)+'\n')
 shutil.copyfile(ROOT/'build/official-upgrade/android.json',out()/'android.json')
 publish('approval',passed=True,candidate_ready_for_owner_review=True,production_distribution_approved=False,permanent_signature_tested=True)
if __name__=='__main__':
 try:
  need(len(sys.argv)==2 and sys.argv[1]in ('policy','preflight','prepare','execute','approve'),'COMMAND')
  command=sys.argv[1]
  if command=='policy':publish('policy',passed=True,**protect())
  else:globals()[command]()
 except Exception as e:
  code=str(e)if isinstance(e,Blocked)and re.fullmatch('[A-Z0-9_]+',str(e))else'UNEXPECTED'
  publish('blocked',passed=False,blocking_code=code)
  print('OFFICIAL_SIGNING_BLOCKED_'+code);raise SystemExit(1)from None
