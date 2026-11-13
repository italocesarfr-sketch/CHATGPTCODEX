#!/usr/bin/env python3
"""H05/H12 private preparation + disposable CI ADB + strict public report. No hosted HTTP here."""
import argparse,base64,hashlib,importlib.util,json,os,re,shutil,stat,subprocess
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('hosted_checks',ROOT/'scripts/hosted-auth-checks.py')
auth=importlib.util.module_from_spec(spec);spec.loader.exec_module(auth)
CASE='H05'
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

H12_FLAGS={'clean_install_verified','authenticated','encrypted_backup_verified','wrong_account_rejected','restore_paused','safety_copy_verified','explicit_review_verified','remote_unchanged','ids_and_payloads_equal','revisions_equal','references_valid','queues_empty','audit_verified','reopened_equal','private_input_removed'}
H12_NUMBERS={'total_ms','records','backup_records','remote_only_records','cursor','receipts','decision_writes','lost_replies','blocked_writes','read_calls','transport_ms'}
H12_STAGES={'INPUT','AUTH','BASELINE','RECOVERY','BACKUP','WRONG_ACCOUNT','PEER_CHANGE','CURRENT','RESTORE','REVIEW','DECISION','REOPEN','DONE'}
H12_CODES={'CLEAN_INPUT_REQUIRED','CONSENT_REQUIRED','AUTHORIZATION_REQUIRED','WRONG_IDENTITY','NOT_CLEAN','DISPOSABLE_ONLY','FIXTURE_MISSING','RECOVERY_MISMATCH','RECOVERY_PENDING','NO_SAFE_OLD_BASELINE','WRONG_ACCOUNT_ACCEPTED','HOSTED_MUTATION_FORBIDDEN','PEER_FAILED','NO_POST_BACKUP_CHANGE','BACKUP_MISMATCH','RESTORE_NOT_PAUSED','AUTOMATIC_RECONCILIATION','BACKUP_NOT_RESTORED','SAFETY_COPY_MISSING','REMOTE_CHANGED','REVIEW_MISSING','ACADEMIC_WRITE_PROPOSED','REVIEW_INCOMPLETE','LOST_REPLY_NOT_PRESERVED','DATA_MISMATCH','DUPLICATE','STATE','QUEUE_NOT_EMPTY','DECISION_MISMATCH','AUDIT_INCOMPLETE','REVISION_MISMATCH','BASE_MISMATCH','RECEIPT_MISMATCH','INVALID_REFERENCES','LEGACY_QUEUE','UNEXPECTED_WRITE','REPLAY_NOT_TESTED','TIMEOUT','UNEXPECTED','CLEANUP_FAILED'}
RECOVERY_CODES={'SCOPE_NOT_READY','LIMIT','REMOTE_CHANGED','INVALID_DATA'}
def configure(case):
 global CASE,REPORT
 need(case in ('H05','H12'),'INVALID_CASE');CASE=case;REPORT=ROOT/('build/'+case.lower()+'/result.json')
def envname(suffix):return 'NOCTUA_'+CASE+'_'+suffix
def native_tests(mode):
 return 4 if mode=='LOCAL' and CASE=='H05' else 14 if mode=='LOCAL' else 1
def h12_proof(raw,mode):
 need(len(raw)<=65536,'INVALID_REPORT')
 try:j=json.loads(raw)
 except Exception:raise Blocked('REPORT_JSON_DECODE')from None
 need(isinstance(j,dict) and not set(j)-H12_FLAGS-H12_NUMBERS-{'case','mode','status','stage','failure_code','local_hash','remote_hash','backup_hash','types','timings_ms','historical_backup_reconstructed'},'REPORT_FIELDS')
 need(j.get('case')=='H12' and j.get('mode')==mode and j.get('status')in('PASSED','FAILED'),'REPORT_IDENTITY')
 need(j.get('stage')in H12_STAGES and j.get('failure_code')in {None}|{'H12_'+c for c in H12_CODES}|{'RECOVERY_'+c for c in RECOVERY_CODES},'REPORT_STAGE_CODE')
 for k in H12_FLAGS&j.keys():need(type(j[k])is bool,'REPORT_FLAG_TYPE')
 for k in H12_NUMBERS&j.keys():need(type(j[k])is int and 0<=j[k]<=10_000_000,'REPORT_NUMBER_TYPE')
 for k in {'local_hash','remote_hash','backup_hash'}&j.keys():need(type(j[k])is str and bool(re.fullmatch('[0-9a-f]{64}',j[k])),'REPORT_HASH')
 timings=j.get('timings_ms');need(isinstance(timings,dict) and not set(timings)-H12_STAGES and all(type(v)is int and 0<=v<=600000 for v in timings.values()),'REPORT_TIMING_TYPE')
 if 'historical_backup_reconstructed'in j:need(type(j['historical_backup_reconstructed'])is bool,'REPORT_FLAG_TYPE')
 if 'types'in j:
  rows=j['types'];need(isinstance(rows,list) and len(rows)==15 and {r.get('kind')for r in rows}==KINDS,'REPORT_TYPE_SET')
  for r in rows:
   need(set(r)=={'kind','local_count','remote_count','local_hash','remote_hash','present'},'REPORT_TYPE_FIELDS')
   need(all(type(r[k])is int and 0<=r[k]<=1000 for k in ('local_count','remote_count')),'REPORT_TYPE_COUNTS')
   need(type(r['present'])is bool and r['present']==(r['remote_count']>0),'REPORT_TYPE_PRESENT')
   need(all(type(r[k])is str and bool(re.fullmatch('[0-9a-f]{64}',r[k]))for k in ('local_hash','remote_hash')),'REPORT_TYPE_HASH')
   need(r['local_count']==r['remote_count'] and r['local_hash']==r['remote_hash'],'REPORT_TYPE_MISMATCH')
 if j['status']=='PASSED':
  need(H12_FLAGS<=j.keys() and all(j[k]is True for k in H12_FLAGS),'INCOMPLETE_PROOF')
  need(H12_NUMBERS<=j.keys() and j['decision_writes']==1 and j['blocked_writes']==0,'INCOMPLETE_PROOF')
  need(j['lost_replies']==(1 if mode=='LOCAL' else 0) and j.get('historical_backup_reconstructed')==(mode=='HOSTED'),'INCOMPLETE_PROOF')
  need(j['stage']=='DONE' and j['failure_code']is None and 1<=j['backup_records']<j['records']<=1000 and j['remote_only_records']>0,'INCOMPLETE_PROOF')
  need(j['cursor']==j['receipts'] and 1<=j['cursor']<=256 and j.get('local_hash')==j.get('remote_hash') and j.get('backup_hash')!=j.get('remote_hash'),'INCOMPLETE_PROOF')
  need('types'in j and sum(r['local_count']for r in j['types'])==j['records'],'INCOMPLETE_PROOF')
 return j

def context(env):
 auth.scope(env)
 mode=env.get(envname('MODE'),'PREFLIGHT');need(mode in MODES,'INVALID_MODE')
 if mode=='HOSTED':need(env.get(envname('AUTHORIZED'))=='YES',CASE+'_AUTHORIZATION_REQUIRED')
 return mode
def folder(env):
 return auth.private_dir(env).parent/('noctua-'+CASE.lower()+'-private')
def write_public(env,overall,proof=None,code=None,schema=None,native=None):
 value=dict(case=CASE,mode=env.get(envname('MODE'),'PREFLIGHT') if env.get(envname('MODE'),'PREFLIGHT') in MODES else None,
  source_sha=env.get('GITHUB_SHA') if re.fullmatch('[0-9a-f]{40}',env.get('GITHUB_SHA','')) else None,
  overall=overall,blocking_code=code,credentials_redacted=True,
  hosted_requests=False if env.get(envname('MODE'))!='HOSTED' or overall=='PREREQUISITES_READY_NOT_EXECUTED' else None)
 if schema is not None:value['report_shape']=schema
 if native is not None:value['instrumentation']=native
 if proof:
  value['proof']=proof
  if proof.get('authenticated') and proof.get('mode')=='HOSTED':value['hosted_requests']=True
 REPORT.parent.mkdir(parents=True,exist_ok=True);REPORT.write_text(json.dumps(value,indent=2)+'\n')
 summary=env.get('GITHUB_STEP_SUMMARY')
 if summary:Path(summary).write_text('## '+CASE+' — '+overall+'\n\nSomente relatório sanitizado; nenhum dado de conta/aluno.\n'+('Bloqueio: '+code+'\n' if code else ''))

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
 p=folder(env);p.mkdir(mode=0o700);(p/('.private-'+CASE.lower())).touch(mode=0o600)
 data.update(mode=mode,consentE='YES')
 if CASE=='H12':
  data['consentReconciliation']='KEEP_REMOTE'
  data['otherId']='99999999-9999-4999-8999-999999999999' if mode=='LOCAL' else secret['accounts'][1]['expectedId']
 fd=os.open(p/'input.json',os.O_CREAT|os.O_EXCL|os.O_WRONLY,0o600)
 with os.fdopen(fd,'w') as f:json.dump(data,f)
 with open(env['GITHUB_ENV'],'a') as f:f.write(envname('INPUT_FILE')+'='+str(p/'input.json')+'\n')
 write_public(env,'PREPARED_NOT_EXECUTED')

def redacted_proof(raw,mode):
 if CASE=='H12':return h12_proof(raw,mode)
 need(len(raw)<=65536,'INVALID_REPORT')
 try:
  try:j=json.loads(raw)
  except Exception:raise Blocked('REPORT_JSON_DECODE')from None
  need(isinstance(j,dict) and not set(j)-FLAGS-NUMBERS-TEXT-{'types','timings_ms'},'REPORT_FIELDS')
  need(j.get('case')=='H05' and j.get('mode')==mode and j.get('status') in ('PASSED','FAILED'),'REPORT_IDENTITY')
  need(j.get('stage') in STAGES and j.get('failure_code') in CODES|{None},'REPORT_STAGE_CODE')
  for k in FLAGS&j.keys():need(type(j[k]) is bool,'REPORT_FLAG_TYPE_'+k)
  for k in NUMBERS&j.keys():need(type(j[k]) is int and 0<=j[k]<=10_000_000,'REPORT_NUMBER_TYPE_'+k)
  for k in {'local_hash','remote_hash'}&j.keys():need(bool(re.fullmatch('[0-9a-f]{64}',j[k])),'REPORT_HASH_'+k)
  need(isinstance(j.get('timings_ms'),dict) and not set(j['timings_ms'])-STAGES,'REPORT_TIMING_STAGES')
  need(all(type(v) is int and 0<=v<=600000 for v in j['timings_ms'].values()),'REPORT_TIMING_TYPE')
  if 'types' in j:
   rows=j['types'];need(isinstance(rows,list) and len(rows)==15 and {r.get('kind')for r in rows}==KINDS,'REPORT_TYPE_SET')
   for r in rows:
    need(set(r)=={'kind','local_count','remote_count','local_hash','remote_hash','present'},'REPORT_TYPE_FIELDS')
    need(all(type(r[k]) is int and 0<=r[k]<=1000 for k in ('local_count','remote_count')),'REPORT_TYPE_COUNTS')
    need(type(r['present']) is bool and r['present']==(r['remote_count']>0),'REPORT_TYPE_PRESENT')
    need(all(bool(re.fullmatch('[0-9a-f]{64}',r[k])) for k in ('local_hash','remote_hash')),'REPORT_TYPE_HASH')
    need(r['local_count']==r['remote_count'] and r['local_hash']==r['remote_hash'],'REPORT_TYPE_MISMATCH')
  if j['status']=='PASSED':
   need(FLAGS<=j.keys() and all(j[k] is True for k in FLAGS),'INCOMPLETE_PROOF')
   need(NUMBERS<=j.keys() and j['blocked_writes']==0 and j['records'] in range(1,1001),'INCOMPLETE_PROOF')
   need(j['cursor']==j['receipts'] and 1<=j['cursor']<=256,'INCOMPLETE_PROOF')
   need(j['stage']=='DONE' and j['failure_code'] is None and j.get('local_hash')==j.get('remote_hash') and 'types'in j,'INCOMPLETE_PROOF')
   need(sum(r['local_count']for r in j['types'])==j['records'],'INCOMPLETE_PROOF')
  return j
 except Blocked:raise
 except Exception:raise Blocked('INVALID_REPORT')from None

def report_shape(raw):
 """Only known field names/types and counts; no values or unknown key names."""
 try:
  j=json.loads(raw)
  if not isinstance(j,dict):return {'json_object':False}
  known=FLAGS|NUMBERS|TEXT|H12_FLAGS|H12_NUMBERS|{'types','timings_ms','backup_hash','historical_backup_reconstructed'}
  def typename(v):
   return 'null' if v is None else 'bool' if type(v)is bool else 'int' if type(v)is int else 'string' if type(v)is str else 'list' if type(v)is list else 'object' if type(v)is dict else 'other'
  return dict(json_object=True,known_field_types={k:typename(j[k])for k in sorted(set(j)&known)},unknown_fields=len(set(j)-known))
 except Exception:return {'json_decodable':False}

def disposable(env,serial,adb):
 need(env.get('RUNNER_ENVIRONMENT')=='github-hosted' and env.get('CI')=='true','DISPOSABLE_GITHUB_RUNNER_REQUIRED')
 need(serial=='emulator-5554','WRONG_DEVICE')
 expected=env.get(envname('AVD_NAME'),'')
 need(bool(re.fullmatch(r'noctua_'+CASE.lower()+r'_(local|hosted)_[0-9]+_[0-9]+',expected)),'WRONG_AVD')
 devices=adb('devices').decode().splitlines()
 need([s.split() for s in devices[1:] if s.strip()]==[[serial,'device']],'UNEXPECTED_DEVICE')
 need(adb('-s',serial,'shell','getprop','ro.kernel.qemu').decode().strip()=='1','WRONG_DEVICE')
 need(adb('-s',serial,'emu','avd','name').decode().splitlines()[0].strip()==expected,'WRONG_AVD')

def native_diagnostics(raw,expected):
 """Never publish an instrumentation message/stack; only fixed markers/classes."""
 known=('ClassCastException','ClassNotFoundException','IllegalStateException','IllegalArgumentException','SecurityException','AssertionError','NoClassDefFoundError')
 return dict(expected_tests=expected,ok_marker=(f'OK ({expected} tests)' if expected!=1 else 'OK (1 test)').encode()in raw,
   process_crashed=b'Process crashed'in raw,instrumentation_failed=b'INSTRUMENTATION_FAILED'in raw,
   failures=b'FAILURES'in raw,exception_types=[name for name in known if name.encode()in raw])

def decode_device_report(encoded,digest):
 need(len(encoded)<=100000,'REPORT_ENCODING')
 match=re.fullmatch(rb'([0-9a-f]{64})  files/'+CASE.lower().encode()+rb'-result\.json\s*',digest)
 need(match is not None,'DEVICE_REPORT_DIGEST')
 try:raw=base64.b64decode(b''.join(encoded.split()),validate=True)
 except Exception:raise Blocked('REPORT_ENCODING')from None
 need(len(raw)<=65536 and hashlib.sha256(raw).hexdigest()==match.group(1).decode(),'DEVICE_REPORT_DIGEST')
 return raw

def execute(env):
 mode=context(env);need(mode in ('LOCAL','HOSTED'),'PREFLIGHT_CANNOT_EXECUTE')
 p=folder(env);source=p/'input.json'
 need(env.get(envname('INPUT_FILE'))==str(source) and source.is_file() and stat.S_IMODE(source.stat().st_mode)==0o600,'PRIVATE_INPUT_REQUIRED')
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
 adb('-s',serial,'shell','run-as',package,'sh','-c',f"'umask 077; cat > files/{CASE.lower()}-private.json'",input=source.read_bytes())
 need(adb('-s',serial,'shell','run-as',package,'stat','-c','%a',f'files/{CASE.lower()}-private.json').decode().strip()=='600','DEVICE_INPUT_PERMISSIONS')
 try:
  classes=('br.com.diariodeclasse.H05RecoveryProbeTest'+(',br.com.diariodeclasse.H05ReadOnlyGuardTest' if mode=='LOCAL' else '')) if CASE=='H05' else ('br.com.diariodeclasse.H12RestoreProbeTest'+(',br.com.diariodeclasse.H12GuardTest,br.com.diariodeclasse.PhaseEReconciliationTest' if mode=='LOCAL' else ''))
  raw=adb('-s',serial,'shell','am','instrument','-w','-e','timeout_msec','360000','-e',CASE.lower()+'Mode',mode,'-e',CASE.lower()+'Authorized','YES' if mode=='HOSTED' else 'NO','-e','class',classes,package+'.test/br.com.diariodeclasse.AuthTestRunner',timeout=420)
  (p/'instrumentation-private.log').write_bytes(raw)
  native=native_diagnostics(raw,native_tests(mode))
  present=adb('-s',serial,'shell','-T','run-as',package,'sh','-c',f"'test -f files/{CASE.lower()}-result.json && echo RECOVERY_REPORT_PRESENT'",check=False)
  if present.strip()!=b'RECOVERY_REPORT_PRESENT':
   write_public(env,'BLOCKED_NOT_APPROVED',code='NATIVE_REPORT_MISSING',native=native)
   raise Blocked(CASE+'_REPORT_REJECTED')
  encoded=adb('-s',serial,'shell','-T','run-as',package,'base64',f'files/{CASE.lower()}-result.json')
  digest=adb('-s',serial,'shell','-T','run-as',package,'sha256sum',f'files/{CASE.lower()}-result.json')
  result=decode_device_report(encoded,digest)
  try:proof=redacted_proof(result,mode)
  except Blocked as e:
   write_public(env,'BLOCKED_NOT_APPROVED',code=str(e),schema=report_shape(result),native=native)
   raise Blocked(CASE+'_REPORT_REJECTED')from None
  passed=proof['status']=='PASSED' and (f'OK ({native_tests(mode)} tests)' if native_tests(mode)!=1 else 'OK (1 test)').encode() in raw and not any(v in raw for v in (b'FAILURES',b'INSTRUMENTATION_FAILED',b'Process crashed',b'Assumption'))
  write_public(env,'PASSED_LOCAL_DISPOSABLE' if passed and mode=='LOCAL' else 'PASSED_HOSTED_PRESENT_TYPES' if passed else 'NOT_APPROVED',proof,native=native)
  need(passed,CASE+'_NOT_APPROVED')
 finally:
  adb('-s',serial,'shell','run-as',package,'rm','-f',f'files/{CASE.lower()}-private.json',check=False)

def clean(env):
 auth.scope(env)
 p=folder(env)
 if p.is_dir() and (p/('.private-'+CASE.lower())).is_file():shutil.rmtree(p)

def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('action',choices=['prepare','execute','clean']);parser.add_argument('--case',choices=['H05','H12'],default='H05');args=parser.parse_args();configure(args.case);env=os.environ
 try:
  if args.action=='prepare':prepare(env)
  elif args.action=='execute':execute(env)
  else:clean(env)
  return 0
 except Blocked as e:
  # Preserve an already sanitized proof when the test itself failed.
  if str(e) not in (CASE+'_NOT_APPROVED',CASE+'_REPORT_REJECTED'):write_public(env,'BLOCKED_NOT_APPROVED',code=str(e))
 except Exception:write_public(env,'BLOCKED_NOT_APPROVED',code='LOCAL_RUNNER_ERROR')
 print(CASE+' not approved; see sanitized result only.');return 1
if __name__=='__main__':raise SystemExit(main())
