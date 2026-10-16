#!/usr/bin/env bash
# Testes/capturas apenas no emulador descartável do Actions.
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${CI:-}" == true ]] || { echo 'Somente no CI.' >&2; exit 1; }
[[ "$(adb get-serialno)" == emulator-* ]] || { echo 'Somente em emulador descartável.' >&2; exit 1; }
status=0
./gradlew --no-daemon --stacktrace connectedDebugAndroidTest || status=$?
mkdir -p build
if adb shell run-as br.com.diariodeclasse test -d files/etapa2-screenshots; then
    adb exec-out run-as br.com.diariodeclasse tar -cf - files/etapa2-screenshots > build/etapa2-telas.tar
fi
if (( status != 0 )); then exit "$status"; fi
python3 - <<'PY'
import tarfile
with tarfile.open('build/etapa2-telas.tar') as images:
    assert len([x for x in images.getmembers() if x.name.endswith('.png')]) >= 4
print('Capturas sintéticas das quatro páginas coletadas.')
PY
./scripts/ci/test-upgrade.sh
