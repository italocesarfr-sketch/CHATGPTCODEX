#!/usr/bin/env python3
"""One new LOCAL CI AVD only. Reuse H05 disposable-device and native-evidence guards."""
import importlib.util,json,os,re,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('h05',ROOT/'scripts/h05-recovery.py');h=importlib.util.module_from_spec(spec);spec.loader.exec_module(h)
def main():
 h.auth.scope(os.environ);assert os.environ.get('CI')=='true'and os.environ.get('PGHOST')=='127.0.0.1'and os.environ.get('PGDATABASE')=='noctua_phase_b_test'
 out=ROOT/'build/b-transition';out.mkdir(parents=True,exist_ok=True);private=Path(os.environ['RUNNER_TEMP'])/'noctua-b-transition-private';private.mkdir(mode=0o700,exist_ok=True)
 def adb(*args,timeout=60,check=True):
  r=subprocess.run(['adb',*args],capture_output=True,timeout=timeout)
  assert not check or r.returncode==0,'ADB';return r.stdout
 device='emulator-5554';h.disposable(os.environ,device,adb)
 for pkg in ('br.com.diariodeclasse','br.com.diariodeclasse.test','br.com.diariodeclasse.homologacao'):
  assert b'package:'not in adb('-s',device,'shell','pm','path',pkg,check=False),'EXISTING_PACKAGE'
 for apk in ('app/build/outputs/apk/debug/app-debug.apk','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'):adb('-s',device,'install','-t',str(ROOT/apk),timeout=120)
 classes=['BTransitionNetworkTest','PhaseCRoomTest','PhaseDRoomTest','PhaseERoomTest']
 expected=sum(len(re.findall(r'@Test\b',(ROOT/f'app/src/androidTest/java/br/com/diariodeclasse/{name}.kt').read_text()))for name in classes)
 raw=adb('-s',device,'shell','am','instrument','-w','-r','-e','timeout_msec','240000','-e','h05Mode','LOCAL','-e','bTransitionLocal','YES','-e','class',','.join('br.com.diariodeclasse.'+name for name in classes),'br.com.diariodeclasse.test/br.com.diariodeclasse.AuthTestRunner',timeout=300,check=False)
 (private/'native.log').write_bytes(raw);diag=h.native_diagnostics(raw,expected)
 codes={'OWNER','CONSENT','ORDER','ELIGIBILITY','LOCAL_STAGE','REMOTE_SCOPE','CONFLICT_OR_RESTORE','QUEUES','V1','CURSOR','UNACKNOWLEDGED','UNSYNCED_LOCAL','LIMIT','RECEIPTS','REVISION','REFERENCES','STALE','REPEAT','CONFIRMATION','WRITE_BLOCKED','DECISION_BLOCKED'}
 diag['source_locations']=sorted(set(re.findall(r'(?:BTransitionNetworkTest|BTransitionPreparation|AcademicStore|PhaseDRoomTest|BackupCodec)\.kt:[0-9]+',raw.decode(errors='ignore'))))
 diag['guard_codes']=sorted(set(re.findall(r'BT_([A-Z_]+)',raw.decode(errors='ignore')))&codes)
 # Export only source-controlled case names and fixed crash indicators, never native messages.
 known_cases={(name,method)for name in classes for method in re.findall(r'@Test\s+fun\s+([A-Za-z0-9_]+)',(ROOT/f'app/src/androidTest/java/br/com/diariodeclasse/{name}.kt').read_text())}
 current={};completed=[];started=None
 for line in raw.decode(errors='ignore').splitlines():
  m=re.fullmatch(r'INSTRUMENTATION_STATUS: (class|test)=(.*)',line.strip())
  if m:current[m[1]]=m[2]
  m=re.fullmatch(r'INSTRUMENTATION_STATUS_CODE: (-?[0-9]+)',line.strip())
  if m:
   case=(current.get('class','').split('.')[-1],current.get('test',''))
   if case in known_cases:
    if int(m[1])==1:started='.'.join(case)
    elif int(m[1])==0:completed.append('.'.join(case))
   current={}
 diag['completed_cases']=sorted(set(completed));diag['last_started_case']=started
 if not diag['ok_marker']:
  crash=adb('-s',device,'logcat','-b','crash','-d','-t','200',check=False)
  (private/'crash.log').write_bytes(crash)
  allowed_types=('OutOfMemoryError','SQLiteException','SQLiteConstraintException','SQLiteDatabaseLockedException','SQLiteCantOpenDatabaseException','SQLiteBlobTooBigException','IllegalArgumentException','IllegalStateException','SecurityException','NullPointerException','RejectedExecutionException','AssertionError')
  diag['crash_types']=[s for s in allowed_types if s.encode()in crash]
  diag['crash_signals']=[s for s in ('pthread_create','Too many open files','unable to create native thread','FATAL EXCEPTION','SQLITE_BUSY','SQLITE_FULL')if s.encode()in crash]
  diag['crash_source_locations']=sorted(set(re.findall(r'(?:BTransitionNetworkTest|BTransitionPreparation|AcademicStore|PhaseCRoomTest|PhaseDRoomTest|PhaseERoomTest|BackupCodec|Database|DiaryRepository)\.kt:[0-9]+',crash.decode(errors='ignore'))))
 report=adb('-s',device,'exec-out','run-as','br.com.diariodeclasse','cat','files/b-transition-result.json',check=False)
 proof=None
 if len(report)<=4096:
  try:
   j=json.loads(report);known={'case','passed','hosted_requests','records','sequence','hash','scopes','negative_checks','academic_writes','pending_preserved','all_15_dirty_types_preserved','conflict_restore_owner_blocked','backup_verified','receipts_revisions_equal','lost_reply_reopen_verified','old_client_preserved_blocked','stage'}
   assert isinstance(j,dict)and not set(j)-known and j.get('case')=='B_TRANSITION_LOCAL'
   for k,v in j.items():
    if k=='case':continue
    if k=='stage':assert v in {'START','DIRTY','PENDING','OUTBOX','GUARDS','CAS','LOST_REPLY','RESUME','FINAL_VERIFY'}
    elif k=='hash':assert isinstance(v,str)and re.fullmatch('[0-9a-f]{64}',v)
    elif k=='scopes':assert v==[3,4,5]
    elif k in ('records','sequence','negative_checks','academic_writes'):assert type(v)is int and 0<=v<=1000
    else:assert type(v)is bool
   proof=j
  except Exception:pass
 passed=diag['ok_marker']and not any(diag[k]for k in ('failures','process_crashed','instrumentation_failed','exception_types'))and b'Assumption'not in raw and proof is not None and proof.get('passed')is True
 if passed:
  assert len(set(completed))==expected,'NATIVE_CASE_COUNT'
  assert proof['records']==41 and proof['sequence']==1 and proof['academic_writes']==0 and proof['negative_checks']>=19 and proof['hosted_requests']is False
  assert all(proof.get(k)is True for k in ('pending_preserved','all_15_dirty_types_preserved','conflict_restore_owner_blocked','backup_verified','receipts_revisions_equal','lost_reply_reopen_verified','old_client_preserved_blocked'))
 (out/'android.json').write_text(json.dumps(dict(source_sha=os.environ['GITHUB_SHA'],passed=passed,hosted_requests=False,native=diag,proof=proof),indent=2)+'\n')
 assert passed,'LOCAL_PROOF_FAILED'
if __name__=='__main__':main()
