#!/usr/bin/env bash
# Only the two already booted disposable AVDs; keep all phase B evidence/server variants.
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${CI:-}" == true && $# == 2 ]] || exit 1
primary="$1"; secondary="$2"
for device in "$primary" "$secondary"; do
 [[ "$device" == emulator-* && "$(adb -s "$device" shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || exit 1
 adb -s "$device" shell svc wifi enable
 adb -s "$device" shell svc data enable
 adb -s "$device" shell pm clear br.com.diariodeclasse >/dev/null
done
mkdir -p build/phased-network
probe(){
 local device="$1" phase="$2" side="$3"
 adb -s "$device" shell am force-stop br.com.diariodeclasse
 timeout --signal=INT --kill-after=10s 3m adb -s "$device" shell am instrument -w -e timeout_msec 120000 \
  -e phaseBNetworkPhase "$phase" -e phaseBDevice "$side" -e class br.com.diariodeclasse.PhaseDNetworkProbeTest \
  br.com.diariodeclasse.test/br.com.diariodeclasse.AuthTestRunner > "build/phased-network/$side-$phase.txt"
 cat "build/phased-network/$side-$phase.txt"
 python3 - "$side" "$phase" <<'PY'
import sys
from pathlib import Path
t=Path(f'build/phased-network/{sys.argv[1]}-{sys.argv[2]}.txt').read_text()
assert 'OK (1 test)' in t and all(x not in t for x in ['FAILURES','INSTRUMENTATION_FAILED','Process crashed']),t
PY
}
probe "$primary" d-seed primary
adb -s "$primary" shell svc wifi enable; adb -s "$primary" shell svc data enable
probe "$primary" d-lost primary
probe "$primary" d-retry primary
probe "$secondary" d-recover secondary
probe "$secondary" d-reorder-secondary secondary
probe "$primary" d-observe-reorder primary
probe "$secondary" d-edit-secondary secondary
probe "$primary" d-observe-secondary primary
probe "$primary" d-offline-reconnect primary
probe "$primary" d-independent-local primary
probe "$secondary" d-independent-remote secondary
adb -s "$primary" shell svc wifi enable; adb -s "$primary" shell svc data enable
probe "$primary" d-merge primary
probe "$primary" d-arm-receive primary
probe "$secondary" d-rename-remote secondary
# The only expected process crash: require both the framework's death report and
# the persisted checkpoint, then prove exact rollback and replay in the next phase.
adb -s "$primary" shell am force-stop br.com.diariodeclasse
set +e
timeout --signal=INT --kill-after=10s 3m adb -s "$primary" shell am instrument -w -e timeout_msec 120000 \
 -e phaseBNetworkPhase d-crash-receive -e phaseBDevice primary -e class br.com.diariodeclasse.PhaseDNetworkProbeTest \
 br.com.diariodeclasse.test/br.com.diariodeclasse.AuthTestRunner > build/phased-network/primary-d-crash-receive.txt
kill_status=$?
set -e
[[ "$kill_status" != 124 && "$kill_status" != 137 ]] || { echo 'Unexpected timeout in kill proof' >&2; exit 1; }
python3 - <<'PYKILL'
from pathlib import Path
text=Path('build/phased-network/primary-d-crash-receive.txt').read_text()
assert 'Process crashed' in text or 'Process has crashed' in text,text
assert 'OK (1 test)' not in text,text
PYKILL
adb -s "$primary" exec-out run-as br.com.diariodeclasse cat files/phased-kill-reached.txt > build/phased-network/kill-checkpoint.txt
probe "$primary" d-recover-receive primary
probe "$primary" d-new-timetable primary
probe "$secondary" d-observe-timetable secondary
probe "$secondary" d-new-relocation secondary
probe "$primary" d-observe-relocation primary
for side in primary secondary; do
 device="$primary"; [[ "$side" == secondary ]] && device="$secondary"
 adb -s "$device" exec-out run-as br.com.diariodeclasse cat files/phased-movement-hash.txt > "build/phased-network/$side-movement.sha256"
done
cmp build/phased-network/primary-movement.sha256 build/phased-network/secondary-movement.sha256
probe "$primary" d-delete-source primary
probe "$secondary" d-observe-deletion secondary
for side in primary secondary; do
 device="$primary"; [[ "$side" == secondary ]] && device="$secondary"
 adb -s "$device" exec-out run-as br.com.diariodeclasse cat files/phased-deleted-hash.txt > "build/phased-network/$side-deleted.sha256"
done
cmp build/phased-network/primary-deleted.sha256 build/phased-network/secondary-deleted.sha256
probe "$primary" d-conflict-local primary
probe "$secondary" d-conflict-remote secondary
adb -s "$primary" shell svc wifi enable; adb -s "$primary" shell svc data enable
probe "$primary" d-observe-conflict primary
for side in primary secondary; do
 device="$primary"; [[ "$side" == secondary ]] && device="$secondary"
 adb -s "$device" exec-out run-as br.com.diariodeclasse tar -cf - files/phased-network > "build/phased-network/$side-captures.tar"
done
python3 scripts/test-phase-d-http.py | tee build/phased-network/http-security.txt
printf '%s\n' '24 Android HTTP phases D passed plus one deliberate mid-receive process kill/rollback proof; two independent emulators, PostgreSQL/PostgREST local; signed Auth fixtures, not hosted Supabase.' > build/phased-network/summary.txt
