#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${CI:-}" == true && "${RUNNER_ENVIRONMENT:-}" == github-hosted ]] || exit 1
python3 - <<'PY'
import os,subprocess
assert os.environ['NOCTUA_H13_AVD_NAME']=='noctua_h13_'+os.environ['GITHUB_RUN_ID']+'_'+os.environ['GITHUB_RUN_ATTEMPT']
def adb(*a):return subprocess.check_output(['adb',*a],text=True).strip()
assert adb('devices').splitlines()[1:]==['emulator-5554\tdevice']
assert adb('-s','emulator-5554','shell','getprop','ro.kernel.qemu')=='1'
assert adb('-s','emulator-5554','emu','avd','name').splitlines()[0]==os.environ['NOCTUA_H13_AVD_NAME']
for package in ('br.com.diariodeclasse','br.com.diariodeclasse.test'):
 result=subprocess.run(['adb','-s','emulator-5554','shell','pm','path',package],capture_output=True,text=True)
 assert result.returncode in (0,1) and 'package:'not in result.stdout
PY
adb -s emulator-5554 install -t app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
# Reuse the controlled LOCAL test startup, no login/fixture secrets/normal jobs.
timeout --signal=INT --kill-after=10s 5m adb -s emulator-5554 shell am instrument -w -e timeout_msec 240000 -e h05Mode LOCAL -e h13Benchmark YES -e class br.com.diariodeclasse.H13RoomBenchmarkTest br.com.diariodeclasse.test/br.com.diariodeclasse.AuthTestRunner > build/h13/android-instrumentation.txt
python3 - <<'PY'
import base64,hashlib,json,re,subprocess
from pathlib import Path
text=Path('build/h13/android-instrumentation.txt').read_text()
assert 'OK (1 test)'in text and all(k not in text for k in ('Assumption','FAILURES','INSTRUMENTATION_FAILED','Process crashed'))
def adb(*a):return subprocess.check_output(['adb','-s','emulator-5554','shell','-T','run-as','br.com.diariodeclasse',*a])
raw=base64.b64decode(b''.join(adb('base64','files/h13-room.json').split()),validate=True)
assert len(raw)<65536 and hashlib.sha256(raw).hexdigest()==adb('sha256sum','files/h13-room.json').decode().split()[0]
j=json.loads(raw);assert set(j)=={'case','layer','samples'} and j['case']=='H13' and len(j['samples'])==12
expected={'records','parts','trial','serialize_ms','decode_ms','receive_wall_ms','receive_process_cpu_ms','last_part_apply_ms','staging_parts_ms','confirmed_replay_ms','scenario_process_cpu_ms','sampled_process_pss_kib','java_heap_used_bytes','java_heap_limit_bytes','sha256','snapshot_fingerprint','ids_payloads_equal','queues_empty','cursor','http_measured','pss_is_sample_not_peak'}
assert sorted((r['records'],r['trial'])for r in j['samples'])==[(n,t)for n in (41,171,671,5171)for t in (1,2,3)]
for r in j['samples']:
 assert set(r)==expected and r['ids_payloads_equal']is True and r['queues_empty']is True and r['cursor']==1 and r['http_measured']is False and r['pss_is_sample_not_peak']is True
 assert r['parts']==(r['records']+127)//128 and all(re.fullmatch('[0-9a-f]{64}',r[k])for k in ('sha256','snapshot_fingerprint'))
for n in (41,171,671,5171):assert len({r['sha256']for r in j['samples']if r['records']==n})==1
Path('build/h13/android.json').write_bytes(raw)
PY
