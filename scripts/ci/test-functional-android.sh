#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${CI:-}" == true ]] || exit 1
[[ "$(adb get-serialno)" == emulator-* && "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || exit 1
mkdir -p build/functional-tests
collect(){ adb exec-out run-as br.com.diariodeclasse tar -C files -cf - fj-flow-screenshots prompt2-screenshots activity-copies-screenshots etapa4a-screenshots > "build/functional-tests/screenshots-$1.tar" 2>/dev/null || true; }
trap 'collect final' EXIT
classes=br.com.diariodeclasse.PromptTwoRoomInstrumentedTest,br.com.diariodeclasse.PhaseCRoomTest,br.com.diariodeclasse.FjFlowInstrumentedTest,br.com.diariodeclasse.FjPopupInstrumentedTest,br.com.diariodeclasse.PromptTwoUiInstrumentedTest,br.com.diariodeclasse.ActivityCopiesRoomTest,br.com.diariodeclasse.ActivityCopiesUiTest,br.com.diariodeclasse.ActivityChangesInstrumentedTest,br.com.diariodeclasse.ActivityChangesUiInstrumentedTest
adb shell am instrument -w -e class "$classes" br.com.diariodeclasse.test/br.com.diariodeclasse.AuthTestRunner > build/functional-tests/android.txt
python3 scripts/check-instrumentation.py build/functional-tests/android.txt
collect normal
# Native compact display + system large font, fresh runner resources; same fictitious fixture app.
adb shell wm size 720x1280
adb shell wm density 320
adb shell settings put system font_scale 1.5
adb shell am force-stop br.com.diariodeclasse
adb shell am instrument -w -e expectedSystemFontScale 1.5 -e class br.com.diariodeclasse.ActivityCopiesUiTest br.com.diariodeclasse.test/br.com.diariodeclasse.AuthTestRunner > build/functional-tests/compact-large-font.txt
python3 scripts/check-instrumentation.py build/functional-tests/compact-large-font.txt
collect compact
adb shell settings put system font_scale 1.0
adb shell wm size reset
adb shell wm density reset
