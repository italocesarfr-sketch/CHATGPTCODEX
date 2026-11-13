#!/usr/bin/env python3
"""H05 private preparation + disposable CI ADB + strict public report. No hosted HTTP here."""
import argparse,importlib.util,json,os,re,shutil,stat,subprocess
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('hosted_checks',ROOT/'scripts/hosted-auth-checks.py')
auth=importlib.util.module_from_spec(spec);spec.loader.exec_module(auth)
REPORT=ROOT/'build/h05/result.json'
KINDS={'classes','terms','students','lessons','activities','marks','grades','recompositions','relocations','import_drafts','export_rows','class_archives','lesson_records','preserved_histories','weekly_timetables'}
STAGES={'INPUT','CLEAN_INSTALL','AUTH','ELIGIBILITY','BASELINE','CONSENT','RECOVERY','VERIFY','REOPEN','NO_REMOTE_CHANGE','CLEANUP','DONE'}
CODES={'STATE','SCOPE_NOT_READY','LIMIT','REMOTE_CHANGED','INVALID_DATA','DATA_MISMATCH','QUEUE_NOT_EMPTY','REVISION_MISMATCH','RECEIPT_MISMATCH','LEGACY_QUEUE','OUTBOUND_BLOCKED','DISPOSABLE_EMULATOR_REQUIRED','INPUT_REQUIRED','CONSENT_REQUIRED','WRONG_IDENTITY','NOT_CLEAN','AUTH_FAILED','FIXTURE_MISSING','CONFLICT','RECOVERY_FAILED','REOPEN_MISMATCH','CLEANUP_FAILED','TIMEOUT','TRANSPORT_FAILED','UNEXPECTED'}
FLAGS={'clean_install_verified','consent_recorded','ids_and_payloads_equal','revisions_equal','references_valid','queues_empty','remote_unchanged','reopened_equal','private_input_removed','authenticated'}
NUMBERS={'total_ms','records','cursor','receipts','tombstones','blocked_writes','read_calls','transport_ms'}
TEXT={'case','mode','status','stage','failure_code','remote_hash','local_hash'}
MODES=('PREFLIGHT','LOCAL','HOSTED')
class Blocked(Exception):pass

def need(ok,code):
 if not ok:raise Blocked(code)
def context(env):
 auth.scope(env)
 mode=env.get('NOCTUA_H05_MODE','PREFLIGHT');need(mode in MODES,'INVALID_MODE')
 if mode=='HOSTED':need(env.get('NOCTUA_H05_AUTHORIZED')=='YES','H05_AUTHORIZATION_REQUIRED')
 return mode
def folder(env):
 return auth.private_dir(env).parent/'noctua-h05-private'
def write_public(env,overall,proof=None,code=None):
 value=dict(case='H05',mode=env.get('NOCTUA_H05_MODE','PREFLIGHT') if env.get('NOCTUA_H05_MODE','PREFLIGHT') in MODES else None,
  source_sha=env.get('GITHUB_SHA') if re.fullmatch('[0-9a-f]{40}',env.get('GITHUB_SHA','')) else None,
  overall=overall,blocking_code=code,credentials_redacted=True,
  hosted_requests=False if env.get('NOCTUA_H05_MODE')!='HOSTED' or overall=='PREREQUISITES_READY_NOT_EXECUTED' else None)
 if proof:
  value['proof']=proof
  if proof.get('authenticated') and proof.get('mode')=='HOSTED':value['hosted_requests']=True
 REPORT.parent.mkdir(parents=True,exist_ok=True);REPORT.write_text(json.dumps(value,indent=2)+'\n')
 summary=env.get('GITHUB_STEP_SUMMARY')
 if summary:Path(summary).write_text('## H05 — '+overall+'\n\nSomente relatório sanitizado; nenhum dado de conta/aluno.\n'+('Bloqueio: '+code+'\n' if code else ''))

def prepare(env):
 mode=context(env)
 if mode=='LOCAL':
  # Synthetic fixture only. Never consume an Environment secret in LOCAL mode.
  data=dict(email='professor@example.invalid',password='Fixture-Password-2026',expectedId='88888888-8888-4888-8888-888888888888',fixtureClassId='d-source')
 else:
  gradle=(ROOT/'app/build.gradle.kts').read_text();models=(ROOT/'app/src/main/java/br/com/diariodeclasse/auth/AuthModels.kt').read_text()
  prod=re.search(r'val productionPublishable = "([^"]+)"',gradle).group(1)
  legacy=re.search(r'const val LEGACY_OWNER_ID = "([^"]+)"',models).group(1)
  secret=auth.validate(env.get('NOCTUA_HOMOLOGACAO_PUBLISHABLE_KEY',''),env.get('NOCTUA_HOMOLOGACAO_AUTH_TEST_JSON',''),prod,legacy,require_zero_probe=False)
  data=dict(secret['accounts'][0],fixtureClassId=secret['fixtureClassId'])
 if mode=='PREFLIGHT':write_public(env,'PREREQUISITES_READY_NOT_EXECUTED');return
 p=folder(env);p.mkdir(mode=0o700);(p/'.private-h05').touch(mode=0o600)
 data.update(mode=mode,consentE='YES') # HOSTED already required the separate explicit H05 flag.
 fd=os.open(p/'input.json',os.O_CREAT|os.O_EXCL|os.O_WRONLY,0o600)
 with os.fdopen(fd,'w') as f:json.dump(data,f)
 with open(env['GITHUB_ENV'],'a') as f:f.write('NOCTUA_H05_INPUT_FILE='+str(p/'input.json')+'\n')
 write_public(env,'PREPARED_NOT_EXECUTED')

def redacted_proof(raw,mode):
 need(len(raw)<=65536,'INVALID_REPORT')
 try:
  j=json.loads(raw)
  need(isinstance(j,dict) and not set(j)-FLAGS-NUMBERS-TEXT-{'types','timings_ms'},'INVALID_REPORT')
  need(j.get('case')=='H05' and j.get('mode')==mode and j.get('status') in ('PASSED','FAILED'),'INVALID_REPORT')
  need(j.get('stage') in STAGES and j.get('failure_code') in CODES|{None},'INVALID_REPORT')
  for k in FLAGS&j.keys():need(type(j[k]) is bool,'INVALID_REPORT')
  for k in NUMBERS&j.keys():need(type(j[k]) is int and 0<=j[k]<=10_000_000,'INVALID_REPORT')
  for k in {'local_hash','remote_hash'}&j.keys():need(bool(re.fullmatch('[0-9a-f]{64}',j[k])),'INVALID_REPORT')
  need(isinstance(j.get('timings_ms'),dict) and not set(j['timings_ms'])-STAGES,'INVALID_REPORT')
  need(all(type(v) is int and 0<=v<=600000 for v in j['timings_ms'].values()),'INVALID_REPORT')
  if 'types' in j:
   rows=j['types'];need(isinstance(rows,list) and len(rows)==15 and {r.get('kind')for r in rows}==KINDS,'INVALID_REPORT')
   for r in rows:
    need(set(r)=={'kind','local_count','remote_count','local_hash','remote_hash','present'},'INVALID_REPORT')
    need(all(type(r[k]) is int and 0<=r[k]<=1000 for k in ('local_count','remote_count')),'INVALID_REPORT')
    need(type(r['present']) is bool and r['present']==(r['remote_count']>0),'INVALID_REPORT')
    need(all(bool(re.fullmatch('[0-9a-f]{64}',r[k])) for k in ('local_hash','remote_hash')),'INVALID_REPORT')
    need(r['local_count']==r['remote_count'] and r['local_hash']==r['remote_hash'],'INVALID_REPORT')
  if j['status']=='PASSED':
   need(FLAGS<=j.keys() and all(j[k] is True for k in FLAGS),'INCOMPLETE_PROOF')
   need(NUMBERS<=j.keys() and j['blocked_writes']==0 and j['records'] in range(1,1001),'INCOMPLETE_PROOF')
   need(j['cursor']==j['receipts'] and 1<=j['cursor']<=256,'INCOMPLETE_PROOF')
   need(j['stage']=='DONE' and j['failure_code'] is None and j.get('local_hash')==j.get('remote_hash') and 'types'in j,'INCOMPLETE_PROOF')
   need(sum(r['local_count']for r in j['types'])==j['records'],'INCOMPLETE_PROOF')
  return j
 except Blocked:raise
 except Exception:raise Blocked('INVALID_REPORT')from None

def disposable(env,serial,adb):
 need(env.get('RUNNER_ENVIRONMENT')=='github-hosted' and env.get('CI')=='true','DISPOSABLE_GITHUB_RUNNER_REQUIRED')
 need(serial=='emulator-5554','WRONG_DEVICE')
 expected=env.get('NOCTUA_H05_AVD_NAME','')
 need(bool(re.fullmatch(r'noctua_h05_(local|hosted)_[0-9]+_[0-9]+',expected)),'WRONG_AVD')
 devices=adb('devices').decode().splitlines()
 need([s.split() for s in devices[1:] if s.strip()]==[[serial,'device']],'UNEXPECTED_DEVICE')
 need(adb('-s',serial,'shell','getprop','ro.kernel.qemu').decode().strip()=='1','WRONG_DEVICE')
 need(adb('-s',serial,'emu','avd','name').decode().splitlines()[0].strip()==expected,'WRONG_AVD')

def execute(env):
 mode=context(env);need(mode in ('LOCAL','HOSTED'),'PREFLIGHT_CANNOT_EXECUTE')
 p=folder(env);source=p/'input.json'
 need(env.get('NOCTUA_H05_INPUT_FILE')==str(source) and source.is_file() and stat.S_IMODE(source.stat().st_mode)==0o600,'PRIVATE_INPUT_REQUIRED')
 serial=env.get('ANDROID_SERIAL','emulator-5554')
 def adb(*args,input=None,check=True,timeout=60):
  result=subprocess.run(['adb',*args],input=input,capture_output=True,timeout=timeout)
  if check:need(result.returncode==0,'ADB_STEP_FAILED')
  return result.stdout
 disposable(env,serial,adb)
 package='br.com.diariodeclasse.homologacao' if mode=='HOSTED' else 'br.com.diariodeclasse'
 app=ROOT/f'app/build/outputs/apk/{"homologacao" if mode=="HOSTED" else "debug"}/app-{"homologacao" if mode=="HOSTED" else "debug"}.apk'
 test=ROOT/f'app/build/outputs/apk/androidTest/{"homologacao" if mode=="HOSTED" else "debug"}/app-{"homologacao" if mode=="HOSTED" else "debug"}-androidTest.apk'
 for identity in (package,package+'.test'):
  need(b'package:' not in adb('-s',serial,'shell','pm','path',identity,check=False),'APP_ALREADY_INSTALLED')
 write_public(env,'RECOVERY_ATTEMPT_STARTED')
 for apk in (app,test):
  need(apk.is_file(),'APK_MISSING');adb('-s',serial,'install','-t',str(apk),timeout=120) # Fresh install, never -r, clear or uninstall.
 adb('-s',serial,'shell','run-as',package,'mkdir','-p','files')
 adb('-s',serial,'shell','run-as',package,'sh','-c',"'umask 077; cat > files/h05-private.json'",input=source.read_bytes())
 need(adb('-s',serial,'shell','run-as',package,'stat','-c','%a','files/h05-private.json').decode().strip()=='600','DEVICE_INPUT_PERMISSIONS')
 try:
  raw=adb('-s',serial,'shell','am','instrument','-w','-e','timeout_msec','360000','-e','h05Mode',mode,'-e','h05Authorized','YES' if mode=='HOSTED' else 'NO','-e','class','br.com.diariodeclasse.H05RecoveryProbeTest'+(',br.com.diariodeclasse.H05ReadOnlyGuardTest' if mode=='LOCAL' else ''),package+'.test/br.com.diariodeclasse.AuthTestRunner',timeout=420)
  (p/'instrumentation-private.log').write_bytes(raw)
  result=adb('-s',serial,'exec-out','run-as',package,'cat','files/h05-result.json')
  proof=redacted_proof(result,mode)
  passed=proof['status']=='PASSED' and (b'OK (3 tests)' if mode=='LOCAL' else b'OK (1 test)') in raw and not any(v in raw for v in (b'FAILURES',b'INSTRUMENTATION_FAILED',b'Process crashed',b'Assumption'))
  write_public(env,'PASSED_LOCAL_DISPOSABLE' if passed and mode=='LOCAL' else 'PASSED_HOSTED_PRESENT_TYPES' if passed else 'NOT_APPROVED',proof)
  need(passed,'H05_NOT_APPROVED')
 finally:
  adb('-s',serial,'shell','run-as',package,'rm','-f','files/h05-private.json',check=False)

def clean(env):
 auth.scope(env)
 p=folder(env)
 if p.is_dir() and (p/'.private-h05').is_file():shutil.rmtree(p)

def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('action',choices=['prepare','execute','clean']);args=parser.parse_args();env=os.environ
 try:
  if args.action=='prepare':prepare(env)
  elif args.action=='execute':execute(env)
  else:clean(env)
  return 0
 except Blocked as e:
  # Preserve an already sanitized proof when the test itself failed.
  if str(e)!='H05_NOT_APPROVED':write_public(env,'BLOCKED_NOT_APPROVED',code=str(e))
 except Exception:write_public(env,'BLOCKED_NOT_APPROVED',code='LOCAL_RUNNER_ERROR')
 print('H05 not approved; see sanitized result only.');return 1
if __name__=='__main__':raise SystemExit(main())
