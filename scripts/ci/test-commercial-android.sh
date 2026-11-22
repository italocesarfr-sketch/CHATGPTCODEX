#!/usr/bin/env bash
# Shared commercial regression, only fresh AVDs provisioned by this CI job.
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${NOCTUA_COMMERCIAL_DISPOSABLE:-}" == YES ]] || exit 1
mkdir -p build/commercial-tests/android
if [[ "${NOCTUA_ONBOARDING_LOCAL:-}" == YES ]]; then
    python3 scripts/ci/test-launcher-icon.py --apk app/build/outputs/apk/debug/app-debug.apk --package br.com.diariodeclasse --label Noctua --output build/commercial-tests/android/launcher
else
    adb install app/build/outputs/apk/debug/app-debug.apk
fi
adb install -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
bash scripts/ci/test-commercial-network.sh
classes=br.com.diariodeclasse.CommercialBootstrapRoomTest,br.com.diariodeclasse.CommercialSyncPanelTest,br.com.diariodeclasse.AcademicAutomaticRoomTest,br.com.diariodeclasse.PhaseBRoomTest,br.com.diariodeclasse.PhaseCRoomTest,br.com.diariodeclasse.PhaseDRoomTest,br.com.diariodeclasse.PhaseEReconciliationTest,br.com.diariodeclasse.AuthIsolationInstrumentedTest
if [[ "${NOCTUA_ONBOARDING_LOCAL:-}" == YES ]]; then
    classes+=,br.com.diariodeclasse.AuthUiInstrumentedTest,br.com.diariodeclasse.SignupConfirmationUiTest,br.com.diariodeclasse.AcademicAuxiliaryCaptureTest
fi
adb shell am instrument -w -r -e class "$classes" br.com.diariodeclasse.test/br.com.diariodeclasse.AuthTestRunner > build/commercial-tests/android/room.txt
python3 - <<'PY'
from pathlib import Path
import os
t=Path('build/commercial-tests/android/room.txt').read_text()
expected=84 if os.environ.get('NOCTUA_ONBOARDING_LOCAL')=='YES' else 72
assert f'OK ({expected} tests)' in t and all(x not in t for x in ('FAILURES','INSTRUMENTATION_FAILED','Process crashed','INSTRUMENTATION_STATUS_CODE: -3','INSTRUMENTATION_STATUS_CODE: -4')),t
PY
adb shell settings put system font_scale 1.6
adb shell am instrument -w -r -e expectedSystemFontScale 1.6 -e class br.com.diariodeclasse.CommercialSyncPanelTest br.com.diariodeclasse.test/br.com.diariodeclasse.AuthTestRunner > build/commercial-tests/android/font16.txt
python3 -c "from pathlib import Path; t=Path('build/commercial-tests/android/font16.txt').read_text(); assert 'OK (2 tests)' in t and 'FAILURES' not in t,t"
adb exec-out run-as br.com.diariodeclasse cat files/commercial-ui.png > build/commercial-tests/android/commercial-ui-font16.png
if [[ "${NOCTUA_ONBOARDING_LOCAL:-}" == YES ]]; then
    adb shell am instrument -w -r -e expectedSystemFontScale 1.6 -e class br.com.diariodeclasse.SignupConfirmationUiTest#confirmationRemainsReachableWithNativeFontScale br.com.diariodeclasse.test/br.com.diariodeclasse.AuthTestRunner > build/commercial-tests/android/font16-otp.txt
    python3 -c "from pathlib import Path; t=Path('build/commercial-tests/android/font16-otp.txt').read_text(); assert 'OK (1 test)' in t and all(x not in t for x in ('FAILURES','INSTRUMENTATION_STATUS_CODE: -3','INSTRUMENTATION_STATUS_CODE: -4')),t"
    adb exec-out run-as br.com.diariodeclasse cat files/signup-otp-1.6.png > build/commercial-tests/android/signup-otp-font16.png
fi
adb shell settings put system font_scale 1.0
