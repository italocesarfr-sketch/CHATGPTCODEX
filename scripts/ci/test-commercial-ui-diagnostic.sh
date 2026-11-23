#!/usr/bin/env bash
# Isolated launcher and session-transition proof. New job-owned AVD and local fixtures only.
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${CI:-}" == true && "${NOCTUA_COMMERCIAL_DISPOSABLE:-}" == YES && "${NOCTUA_ONBOARDING_LOCAL:-}" == YES ]]
[[ "$(adb emu avd name | head -1 | tr -d '\r')" == "noctua_commercial_auto_${GITHUB_RUN_ID}_${GITHUB_RUN_ATTEMPT}" ]]
mkdir -p build/commercial-tests/android
collect(){
    adb exec-out run-as br.com.diariodeclasse tar cf - -C files commercial-final-screenshots > build/commercial-tests/android/ui-diagnostic-screenshots.tar || true
    adb shell run-as br.com.diariodeclasse ls files | tr -d '\r' | grep -E '^commercial-final-(transition-[0-2](-[AB]\.json|\.txt)|login-diagnostic\.txt|e-auto-final-ui-transition\.json)$' | while read -r name; do
        adb exec-out run-as br.com.diariodeclasse cat "files/$name" > "build/commercial-tests/android/$name"
    done
}
trap collect EXIT
launcher_status=0
python3 scripts/ci/test-launcher-icon.py --apk app/build/outputs/apk/debug/app-debug.apk --package br.com.diariodeclasse --label Noctua --output build/commercial-tests/android/launcher || launcher_status=$?
adb install -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
for phase in e-auto-commercial-init-lost e-auto-commercial-seed e-auto-final-isolation e-auto-final-ui-transition; do
    class=CommercialAutomaticNetworkProbeTest
    [[ "$phase" != e-auto-final-* ]] || class=CommercialFinalNetworkProbeTest
    adb shell am force-stop br.com.diariodeclasse
    timeout --signal=INT --kill-after=10s 3m adb shell am instrument -w -r -e timeout_msec 120000 -e phaseBNetworkPhase "$phase" -e phaseBDevice primary -e class "br.com.diariodeclasse.$class" br.com.diariodeclasse.test/br.com.diariodeclasse.AuthTestRunner > "build/commercial-tests/android/$phase.txt"
    python3 - "$phase" <<'PY'
from pathlib import Path
import sys
t=Path('build/commercial-tests/android/'+sys.argv[1]+'.txt').read_text()
assert 'OK (1 test)' in t and all(x not in t for x in ('FAILURES','INSTRUMENTATION_FAILED','Process crashed','INSTRUMENTATION_STATUS_CODE: -3','INSTRUMENTATION_STATUS_CODE: -4')),t
print(sys.argv[1]+': passed')
PY
done
collect
python3 scripts/test-commercial-ui-evidence.py
exit "$launcher_status"
