#!/usr/bin/env bash
# Testes/capturas apenas no emulador descartável do Actions.
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${CI:-}" == true ]] || { echo 'Somente no CI.' >&2; exit 1; }
[[ "$(adb get-serialno)" == emulator-* ]] || { echo 'Somente em emulador descartável.' >&2; exit 1; }
status=0
./gradlew --no-daemon --stacktrace -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true connectedDebugAndroidTest || status=$?
mkdir -p build
if adb shell run-as br.com.diariodeclasse test -d files/etapa2-screenshots; then
    adb exec-out run-as br.com.diariodeclasse tar -cf - files/etapa2-screenshots > build/etapa2-telas.tar
fi
if adb shell run-as br.com.diariodeclasse test -d files/etapa3-screenshots; then
    adb exec-out run-as br.com.diariodeclasse tar -cf - files/etapa3-screenshots > build/etapa3-telas.tar
fi
if (( status != 0 )); then exit "$status"; fi
python3 - <<'PY'
import tarfile
with tarfile.open('build/etapa2-telas.tar') as images:
    assert len([x for x in images.getmembers() if x.name.endswith('.png')]) >= 4
print('Capturas sintéticas das quatro páginas coletadas.')
PY
for phase in write read; do
    if [[ "$phase" == read ]]; then adb shell am force-stop br.com.diariodeclasse; fi
    adb shell am instrument -w -e frequencyProbePhase "$phase" -e class br.com.diariodeclasse.FrequencyProcessProbeTest br.com.diariodeclasse.test/androidx.test.runner.AndroidJUnitRunner > "build/frequency-process-$phase.txt"
    python3 - "$phase" <<'PYPROBE'
import sys
from pathlib import Path
text=Path('build/frequency-process-'+sys.argv[1]+'.txt').read_text()
assert 'OK (1 test)' in text and 'FAILURES' not in text and 'INSTRUMENTATION_FAILED' not in text
print('Sonda de frequência/reinício aprovada:',sys.argv[1])
PYPROBE
done
./scripts/ci/test-upgrade.sh
