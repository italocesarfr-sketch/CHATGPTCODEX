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
mkdir -p build/phasec-network
probe(){
 local device="$1" phase="$2" side="$3"
 adb -s "$device" shell am force-stop br.com.diariodeclasse
 timeout --signal=INT --kill-after=10s 3m adb -s "$device" shell am instrument -w -e timeout_msec 120000 \
  -e phaseBNetworkPhase "$phase" -e phaseBDevice "$side" -e class br.com.diariodeclasse.PhaseCNetworkProbeTest \
  br.com.diariodeclasse.test/br.com.diariodeclasse.AuthTestRunner > "build/phasec-network/$side-$phase.txt"
 cat "build/phasec-network/$side-$phase.txt"
 python3 - "$side" "$phase" <<'PY'
import sys
from pathlib import Path
t=Path(f'build/phasec-network/{sys.argv[1]}-{sys.argv[2]}.txt').read_text()
assert 'OK (1 test)' in t and all(x not in t for x in ['FAILURES','INSTRUMENTATION_FAILED','Process crashed']),t
PY
}
probe "$primary" c-seed primary
adb -s "$primary" shell svc wifi enable; adb -s "$primary" shell svc data enable
probe "$primary" c-lost primary
probe "$primary" c-retry primary
probe "$secondary" c-recover secondary
probe "$secondary" c-edit-secondary secondary
probe "$primary" c-observe-secondary primary
probe "$primary" c-offline-reconnect primary
probe "$primary" c-independent-local primary
probe "$secondary" c-independent-remote secondary
adb -s "$primary" shell svc wifi enable; adb -s "$primary" shell svc data enable
probe "$primary" c-merge primary
probe "$primary" c-conflict-local primary
probe "$secondary" c-conflict-remote secondary
adb -s "$primary" shell svc wifi enable; adb -s "$primary" shell svc data enable
probe "$primary" c-observe-conflict primary
for side in primary secondary; do
 device="$primary"; [[ "$side" == secondary ]] && device="$secondary"
 adb -s "$device" exec-out run-as br.com.diariodeclasse tar -cf - files/phasec-network > "build/phasec-network/$side-captures.tar"
done
printf '%s\n' '13 Android HTTP phases C passed; two independent emulators, PostgreSQL/PostgREST local; signed Auth fixtures, not hosted Supabase.' > build/phasec-network/summary.txt
