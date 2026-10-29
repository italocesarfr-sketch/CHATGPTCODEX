#!/usr/bin/env bash
# EXCLUSIVO de emulador descartável do GitHub Actions. Nunca usar em celular de professor.
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${CI:-}" == true ]] || { echo 'Somente no CI.' >&2; exit 1; }
DEVICE=$(adb get-serialno)
[[ "$DEVICE" == emulator-* ]] || { echo 'Somente em emulador descartável.' >&2; exit 1; }
ROOT=$(cd -- "$(dirname -- "$0")/../.." && pwd)
cd "$ROOT"
OLD=.baseline-source/app/build/outputs/apk/debug/app-debug.apk
NEW=app/build/outputs/apk/debug/app-debug.apk
python3 scripts/check-apk-update.py "$OLD" "$NEW" --build-tools "$ANDROID_HOME/build-tools/35.0.0" > build/upgrade-identity.json
adb install -r -d "$OLD"
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
# Limpeza SOMENTE do sandbox de teste descartável; não integra o aplicativo/serviço de backup.
adb shell pm clear br.com.diariodeclasse >/dev/null
python3 scripts/create-fixture-v1.py --database build/synthetic-v1.db
adb push build/synthetic-v1.db /data/local/tmp/diario-synthetic-v1.db >/dev/null
adb shell run-as br.com.diariodeclasse mkdir -p databases
adb shell run-as br.com.diariodeclasse sh -c '"cat /data/local/tmp/diario-synthetic-v1.db > databases/diario.db"'
RUNNER=br.com.diariodeclasse.AuthTestRunner
probe() {
    adb shell am instrument -w -e upgradeProbe true -e class br.com.diariodeclasse.UpgradeProbeTest br.com.diariodeclasse.test/"$RUNNER" > "$1"
    cat "$1"
    python3 - "$1" <<'PYTEST'
import sys
text=open(sys.argv[1]).read()
assert 'OK (1 test)' in text and 'FAILURES' not in text and 'INSTRUMENTATION_FAILED' not in text
PYTEST
}
probe build/upgrade-before.txt
python3 scripts/rescue-debug-data.py --serial "$DEVICE" --output build/synthetic-rescue
adb install -r "$NEW"
probe build/upgrade-after.txt
# Compara a cópia recuperada com a fixture, sem publicar o banco binário.
python3 - <<'PY'
import json,hashlib
from pathlib import Path
backup=json.loads(Path('build/synthetic-rescue/resgate-diario.json').read_text())
assert hashlib.sha256(backup['payload'].encode()).hexdigest()==backup['payloadSha256']
assert json.loads(backup['payload'])==json.loads(Path('test-fixtures/baseline-v1.json').read_text())
print('Resgate local sintético reproduziu todas as oito tabelas.')
PY
