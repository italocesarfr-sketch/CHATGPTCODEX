#!/usr/bin/env bash
# APENAS chaves DESCARTÁVEIS, em emulador descartável. Não produz release permanente.
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${CI:-}" == true ]] || { echo 'Somente no CI.' >&2; exit 1; }
[[ "$(adb get-serialno)" == emulator-* && "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || { echo 'Somente em emulador descartável.' >&2; exit 1; }
ROOT=$(cd -- "$(dirname -- "$0")/../.." && pwd)
cd "$ROOT"
mkdir -p build/noctua-signing
TEST_DIR=$(mktemp -d "$RUNNER_TEMP/noctua-disposable.XXXXXX")
trap 'rm -rf "$TEST_DIR"; rm -f app/build/outputs/apk/release/app-release.apk' EXIT
export NOCTUA_TEST_PASSWORD='discardable-ci-only-not-a-permanent-secret'
TOOLS="$ANDROID_HOME/build-tools/35.0.0"
for alias in controlled incompatible; do
    keytool -genkeypair -noprompt -storetype PKCS12 -keystore "$TEST_DIR/$alias.p12" -alias "$alias" -keyalg RSA -keysize 2048 -validity 2 -dname "CN=Noctua disposable CI test" -storepass:env NOCTUA_TEST_PASSWORD -keypass:env NOCTUA_TEST_PASSWORD >/dev/null 2>&1
done
export DIARY_RELEASE_STORE_FILE="$TEST_DIR/controlled.p12"
export DIARY_RELEASE_STORE_PASSWORD="$NOCTUA_TEST_PASSWORD"
export DIARY_RELEASE_KEY_PASSWORD="$NOCTUA_TEST_PASSWORD"
export DIARY_RELEASE_KEY_ALIAS=controlled
export DIARY_RELEASE_CERT_SHA256
DIARY_RELEASE_CERT_SHA256=$(keytool -exportcert -keystore "$TEST_DIR/controlled.p12" -alias controlled -storepass:env NOCTUA_TEST_PASSWORD 2>/dev/null | sha256sum | cut -d ' ' -f1)
# O fingerprint incorreto deve bloquear release mesmo com uma chave válida.
if DIARY_RELEASE_CERT_SHA256=0000000000000000000000000000000000000000000000000000000000000000 ./gradlew --no-daemon preReleaseBuild > build/noctua-signing/wrong-certificate.txt 2>&1; then
    echo 'ERRO: certificado incorreto foi aceito.' >&2; exit 1
fi
python3 - <<'PY'
from pathlib import Path
assert 'Release bloqueado: chave ou certificado esperado não confere.' in Path('build/noctua-signing/wrong-certificate.txt').read_text()
PY
./gradlew --no-daemon assembleRelease
RELEASE=app/build/outputs/apk/release/app-release.apk
"$TOOLS/apksigner" verify --verbose "$RELEASE" > build/noctua-signing/release-verification.txt
EXPECTED_CERT="$DIARY_RELEASE_CERT_SHA256" python3 scripts/verify-release-certificate.py "$RELEASE"
python3 scripts/apk_identity.py "$RELEASE" > build/noctua-signing/release-test-identity.json
sign_test() {
    "$TOOLS/apksigner" sign --ks "$TEST_DIR/$1.p12" --ks-key-alias "$1" --ks-pass env:NOCTUA_TEST_PASSWORD --key-pass env:NOCTUA_TEST_PASSWORD --out "$3" "$2"
    "$TOOLS/apksigner" verify "$3"
}
sign_test controlled .baseline-release-source/app/build/outputs/apk/debug/app-debug.apk "$TEST_DIR/old.apk"
sign_test controlled app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk "$TEST_DIR/tests.apk"
sign_test incompatible "$RELEASE" "$TEST_DIR/incompatible.apk"
python3 scripts/check-apk-update.py "$TEST_DIR/old.apk" "$RELEASE" --build-tools "$TOOLS" > build/noctua-signing/compatible-identity.txt
# Instalações de testes anteriores são descartadas SOMENTE neste emulador do Actions.
adb uninstall br.com.diariodeclasse.test
adb uninstall br.com.diariodeclasse
adb install "$TEST_DIR/old.apk"
adb install "$TEST_DIR/tests.apk"
python3 scripts/create-fixture-v1.py --database build/synthetic-release-v1.db
adb push build/synthetic-release-v1.db /data/local/tmp/noctua-synthetic.db >/dev/null
adb shell run-as br.com.diariodeclasse mkdir -p databases
adb shell run-as br.com.diariodeclasse sh -c '"cat /data/local/tmp/noctua-synthetic.db > databases/diario.db"'
probe() {
    local log=$1
    shift
    adb shell am instrument -w "$@" br.com.diariodeclasse.test/androidx.test.runner.AndroidJUnitRunner > "build/noctua-signing/$log.txt"
    python3 - "build/noctua-signing/$log.txt" <<'PY'
import sys
from pathlib import Path
text=Path(sys.argv[1]).read_text()
assert 'OK (1 test)' in text and 'FAILURES' not in text and 'INSTRUMENTATION_FAILED' not in text, text
print('Sonda aprovada:', Path(sys.argv[1]).name)
PY
}
probe before -e upgradeProbe true -e class br.com.diariodeclasse.UpgradeProbeTest
probe seed -e releaseProbePhase seed -e class br.com.diariodeclasse.ReleaseContinuityProbeTest
adb shell am force-stop br.com.diariodeclasse
adb install -r "$RELEASE"
probe after -e upgradeProbe true -e class br.com.diariodeclasse.UpgradeProbeTest
probe backup -e releaseProbePhase verify -e class br.com.diariodeclasse.ReleaseContinuityProbeTest
# Não desinstalar nem limpar após a rejeição: comprovar preservação da instalação válida.
if adb install -r "$TEST_DIR/incompatible.apk" > build/noctua-signing/incompatible-install.txt 2>&1; then
    echo 'ERRO: assinatura incompatível foi aceita.' >&2; exit 1
fi
python3 - <<'PY'
from pathlib import Path
assert 'INSTALL_FAILED_UPDATE_INCOMPATIBLE' in Path('build/noctua-signing/incompatible-install.txt').read_text()
PY
adb shell am force-stop br.com.diariodeclasse
probe after-rejection -e upgradeProbe true -e class br.com.diariodeclasse.UpgradeProbeTest
probe backup-after-rejection -e releaseProbePhase verify -e class br.com.diariodeclasse.ReleaseContinuityProbeTest
echo 'Release de TESTE: atualização 0.4.0 para Noctua, banco/preferências/backup preservados e assinatura diferente recusada.'
