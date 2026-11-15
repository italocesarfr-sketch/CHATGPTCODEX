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
 raw=adb('-s',device,'shell','am','instrument','-w','-e','timeout_msec','240000','-e','h05Mode','LOCAL','-e','bTransitionLocal','YES','-e','class',','.join('br.com.diariodeclasse.'+name for name in classes),'br.com.diariodeclasse.test/br.com.diariodeclasse.AuthTestRunner',timeout=300,check=False)
 (private/'native.log').write_bytes(raw);diag=h.native_diagnostics(raw,expected)
 codes={'OWNER','CONSENT','ORDER','ELIGIBILITY','LOCAL_STAGE','REMOTE_SCOPE','CONFLICT_OR_RESTORE','QUEUES','V1','CURSOR','UNACKNOWLEDGED','UNSYNCED_LOCAL','LIMIT','RECEIPTS','REVISION','REFERENCES','STALE','REPEAT','CONFIRMATION','WRITE_BLOCKED','DECISION_BLOCKED'}
 diag['source_locations']=sorted(set(re.findall(r'(?:BTransitionNetworkTest|BTransitionPreparation|AcademicStore|PhaseDRoomTest|BackupCodec)\.kt:[0-9]+',raw.decode(errors='ignore'))))
 diag['guard_codes']=sorted(set(re.findall(r'BT_([A-Z_]+)',raw.decode(errors='ignore')))&codes)
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
  assert proof['records']==41 and proof['sequence']==1 and proof['academic_writes']==0 and proof['negative_checks']>=19 and proof['hosted_requests']is False
  assert all(proof.get(k)is True for k in ('pending_preserved','all_15_dirty_types_preserved','conflict_restore_owner_blocked','backup_verified','receipts_revisions_equal','lost_reply_reopen_verified','old_client_preserved_blocked'))
 (out/'android.json').write_text(json.dumps(dict(source_sha=os.environ['GITHUB_SHA'],passed=passed,hosted_requests=False,native=diag,proof=proof),indent=2)+'\n')
 assert passed,'LOCAL_PROOF_FAILED'
if __name__=='__main__':main()
