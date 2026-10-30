#!/usr/bin/env bash
# Dedicated, fictitious fixtures only. No real backend or real user device is touched.
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${CI:-}" == true ]] || exit 1
[[ "$(adb get-serialno)" == emulator-* && "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || exit 1
RUNNER=br.com.diariodeclasse.test/br.com.diariodeclasse.AuthTestRunner
check_result() {
  python3 - "$1" "$2" <<'PY'
from pathlib import Path
import sys
text=Path(sys.argv[1]).read_text()
expected=f'OK ({sys.argv[2]} '+('test)' if sys.argv[2]=='1' else 'tests)')
assert expected in text and all(x not in text for x in ('FAILURES','INSTRUMENTATION_FAILED','Process crashed')),text
print('Fila e integridade aprovadas:',Path(sys.argv[1]).name)
PY
}
timeout --signal=INT --kill-after=10s 4m adb shell am instrument -w -e class br.com.diariodeclasse.ClassSyncInstrumentedTest,br.com.diariodeclasse.SyncUiInstrumentedTest,br.com.diariodeclasse.SyncWorkspaceLifecycleTest "$RUNNER" > build/sync-focused.txt
cat build/sync-focused.txt
check_result build/sync-focused.txt 23
for phase in write read; do
  if [[ "$phase" == read ]]; then adb shell am force-stop br.com.diariodeclasse; fi
  adb shell am instrument -w -e syncProbePhase "$phase" -e class br.com.diariodeclasse.SyncProcessProbeTest "$RUNNER" > "build/sync-process-$phase.txt"
  check_result "build/sync-process-$phase.txt" 1
done
adb exec-out run-as br.com.diariodeclasse tar -cf - files/sync-screenshots > build/sync-telas.tar
