#!/usr/bin/env bash
# Same two disposable AVDs and existing transport/queue, fifteen types, >5,000 rows.
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${CI:-}" == true && $# == 2 ]] || exit 1
primary="$1"; secondary="$2"
for device in "$primary" "$secondary"; do
 [[ "$device" == emulator-* && "$(adb -s "$device" shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || exit 1
 adb -s "$device" shell svc wifi enable; adb -s "$device" shell svc data enable
 adb -s "$device" shell pm clear br.com.diariodeclasse >/dev/null
done
mkdir -p build/phasee-network
archive(){
 for side in primary secondary; do
  local device="$primary"; [[ "$side" == secondary ]] && device="$secondary"
  if adb -s "$device" shell run-as br.com.diariodeclasse test -d files/phasee-network; then
   adb -s "$device" exec-out run-as br.com.diariodeclasse tar -cf - files/phasee-network > "build/phasee-network/$side-captures.tar" || true
  fi
 done
}
trap archive EXIT
probe(){
 local device="$1" phase="$2" side="$3"
 adb -s "$device" shell am force-stop br.com.diariodeclasse
 timeout --signal=INT --kill-after=10s 4m adb -s "$device" shell am instrument -w -e timeout_msec 210000 \
  -e phaseBNetworkPhase "$phase" -e phaseBDevice "$side" -e class br.com.diariodeclasse.PhaseENetworkProbeTest \
  br.com.diariodeclasse.test/br.com.diariodeclasse.AuthTestRunner > "build/phasee-network/$side-$phase.txt"
 cat "build/phasee-network/$side-$phase.txt"
 python3 - "$side" "$phase" <<'PY'
from pathlib import Path
import sys
text=Path(f'build/phasee-network/{sys.argv[1]}-{sys.argv[2]}.txt').read_text()
assert 'OK (1 test)' in text and all(x not in text for x in ['FAILURES','INSTRUMENTATION_FAILED','Process crashed']),text
PY
}
probe "$primary" e-seed primary
adb -s "$primary" shell svc wifi enable; adb -s "$primary" shell svc data enable
probe "$primary" e-lost primary
probe "$primary" e-replay primary
# Expected real process death after a durable fragment, before any academic apply.
set +e
timeout --signal=INT --kill-after=10s 4m adb -s "$secondary" shell am instrument -w -e timeout_msec 210000 \
 -e phaseBNetworkPhase e-crash-part -e phaseBDevice secondary -e class br.com.diariodeclasse.PhaseENetworkProbeTest \
 br.com.diariodeclasse.test/br.com.diariodeclasse.AuthTestRunner > build/phasee-network/secondary-e-crash-part.txt
kill_status=$?
set -e
[[ "$kill_status" != 124 && "$kill_status" != 137 ]] || exit 1
python3 - <<'PY'
from pathlib import Path
text=Path('build/phasee-network/secondary-e-crash-part.txt').read_text()
assert ('Process crashed' in text or 'Process has crashed' in text) and 'OK (1 test)' not in text,text
PY
adb -s "$secondary" exec-out run-as br.com.diariodeclasse cat files/phasee-kill.txt > build/phasee-network/kill-checkpoint.txt
probe "$secondary" e-recover secondary
probe "$secondary" e-grade-secondary secondary
probe "$primary" e-observe-grade primary
probe "$primary" e-offline-reconnect primary
probe "$secondary" e-observe-journal secondary
probe "$primary" e-conflict-local primary
probe "$secondary" e-conflict-remote secondary
adb -s "$primary" shell svc wifi enable; adb -s "$primary" shell svc data enable
probe "$primary" e-conflict-observe primary
python3 scripts/test-phase-e-http.py | tee build/phasee-network/http-security.txt
printf '%s\n' '11 Android E1 HTTP phases plus a required process kill after durable staging passed; 5,171 records, 15 kinds, two independent AVDs; local PostgreSQL/PostgREST and synthetic Auth only.' > build/phasee-network/summary.txt
