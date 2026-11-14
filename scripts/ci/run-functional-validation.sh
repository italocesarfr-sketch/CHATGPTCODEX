#!/usr/bin/env bash
# Execute independent proofs even when one fails; the aggregate remains failed.
set -uo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${CI:-}" == true ]] || exit 1
[[ "$(adb get-serialno)" == emulator-* && "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || exit 1
status=0
./scripts/ci/test-functional-network.sh || status=1
# The primary was created by this workflow. These are solely our completed synthetic network fixtures.
adb shell pm clear br.com.diariodeclasse >/dev/null || exit 1
mkdir -p build/functional-tests
adb shell am instrument -w -e class br.com.diariodeclasse.PromptTwoRoomInstrumentedTest,br.com.diariodeclasse.PhaseCRoomTest,br.com.diariodeclasse.FjFlowInstrumentedTest,br.com.diariodeclasse.FjPopupInstrumentedTest,br.com.diariodeclasse.PromptTwoUiInstrumentedTest br.com.diariodeclasse.test/br.com.diariodeclasse.AuthTestRunner > build/functional-tests/android.txt
adb exec-out run-as br.com.diariodeclasse tar -C files -cf - fj-flow-screenshots prompt2-screenshots > build/functional-tests/screenshots.tar 2>/dev/null || true
python3 scripts/check-instrumentation.py build/functional-tests/android.txt || status=1
exit "$status"
