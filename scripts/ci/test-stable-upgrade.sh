#!/usr/bin/env bash
# Apenas emulador novo do CI; utiliza a assinatura JÁ EXISTENTE, nunca gera chaves.
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${CI:-}" == true ]] || { echo 'Somente no CI.' >&2; exit 1; }
[[ "$(adb get-serialno)" == emulator-* && "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || { echo 'Somente emulador descartável.' >&2; exit 1; }
mkdir -p build/stable-upgrade
if adb shell pm path br.com.diariodeclasse | tr -d '\r' | grep -q '^package:'; then
    echo 'Este teste exige emulador novo, sem aplicativo instalado. Nada será apagado.' >&2; exit 1
fi
OLD=.stable-baseline/app-release.apk
NEW=app/build/outputs/apk/release/app-release.apk
python3 scripts/check-apk-update.py "$OLD" "$NEW" --build-tools "$ANDROID_HOME/build-tools/35.0.0" > build/stable-upgrade/compatibilidade.txt
adb install "$OLD"
adb install -t "$RUNNER_TEMP/stable-instrumentation.apk"
probe() {
    adb shell am instrument -w -e stableUpgradePhase "$1" -e class br.com.diariodeclasse.StableUpgradeProbeTest br.com.diariodeclasse.test/androidx.test.runner.AndroidJUnitRunner > "build/stable-upgrade/$2.txt"
    python3 - "build/stable-upgrade/$2.txt" <<'PY'
import sys
from pathlib import Path
text=Path(sys.argv[1]).read_text()
assert 'OK (1 test)' in text and 'FAILURES' not in text and 'INSTRUMENTATION_FAILED' not in text, text
print('Preservação aprovada:',Path(sys.argv[1]).name)
PY
}
probe seed before
adb shell am force-stop br.com.diariodeclasse
adb install -r "$NEW"
probe check after
probe restore backup
adb shell am force-stop br.com.diariodeclasse
probe check reopened
probe delete-history deleted-history
adb shell am force-stop br.com.diariodeclasse
probe reopen-history reopened-history

probe delete-student deleted-student
adb shell am force-stop br.com.diariodeclasse
probe reopen-student reopened-student
