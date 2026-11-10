#!/usr/bin/env bash
# Somente fixtures fictícias no emulador descartável. Nenhuma conta online é criada.
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${CI:-}" == true ]] || exit 1
[[ "$(adb get-serialno)" == emulator-* && "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || exit 1
RUNNER=br.com.diariodeclasse.test/br.com.diariodeclasse.AuthTestRunner
# Synthetic CI AVD only. Preserve the cause before emulator-runner shuts it down.
# Never retry a crashed test into a pass or change its success assertions.
capture_failure() {
    local status=$?
    if [[ "$status" != 0 ]]; then
        timeout --signal=KILL 10s adb logcat -b crash -d -v threadtime > build/auth-crash.txt 2>&1 || true
        timeout --signal=KILL 10s adb logcat -b events -d -v brief -s am_crash am_anr am_kill am_proc_died > build/auth-process-events.txt 2>&1 || true
        timeout --signal=KILL 10s adb shell dumpsys activity exit-info br.com.diariodeclasse > build/auth-exit-info.txt 2>&1 || true
    fi
}
trap capture_failure EXIT
check_result() {
    python3 - "$1" "$2" <<'PY'
import sys
from pathlib import Path
text=Path(sys.argv[1]).read_text()
assert f'OK ({sys.argv[2]} test'+(')' if sys.argv[2]=='1' else 's)') in text, text
assert all(x not in text for x in ('FAILURES','INSTRUMENTATION_FAILED','Process crashed')), text
print('Autenticação aprovada:',Path(sys.argv[1]).name)
PY
}
if [[ "${1:-focused}" == focused ]]; then
    timeout --signal=INT --kill-after=10s 5m adb shell am instrument -w -r -e timeout_msec 90000 -e class br.com.diariodeclasse.AuthIsolationInstrumentedTest,br.com.diariodeclasse.AuthUiInstrumentedTest,br.com.diariodeclasse.AuthClaimLayoutInstrumentedTest,br.com.diariodeclasse.FinalUxEntryInstrumentedTest "$RUNNER" > build/auth-focused.txt
    cat build/auth-focused.txt
    check_result build/auth-focused.txt 19
    # Regression for the unexplained process death in run 42732848997.
    # Both repetitions must pass; no retry/ignore path for a failure.
    for pass in 1 2; do
        timeout --signal=INT --kill-after=10s 3m adb shell am instrument -w -r -e timeout_msec 90000 -e class br.com.diariodeclasse.AuthIsolationInstrumentedTest "$RUNNER" > "build/auth-isolation-repeat-$pass.txt"
        check_result "build/auth-isolation-repeat-$pass.txt" 8
    done
    for font in 1.3 1.6; do
        adb shell settings put system font_scale "$font"
        timeout --signal=INT --kill-after=10s 2m adb shell am instrument -w -e timeout_msec 90000 -e expectedSystemFontScale "$font" -e class br.com.diariodeclasse.AuthUiInstrumentedTest#allFormsRemainScrollableAndReachableWithTheNativeFontScale,br.com.diariodeclasse.AuthClaimLayoutInstrumentedTest "$RUNNER" > "build/auth-font-$font.txt"
        cat "build/auth-font-$font.txt"
        check_result "build/auth-font-$font.txt" 2
    done
    adb shell settings put system font_scale 1.0
    adb exec-out run-as br.com.diariodeclasse tar -cf - files/auth-screenshots > build/auth-telas.tar
else
    # Escrita e leitura em processos separados, com reinício real do emulador entre ambos.
    for phase in write read; do
        if [[ "$phase" == read ]]; then
            python3 scripts/reboot-disposable-avd.py > build/auth-reboot-output.json
            timeout --signal=KILL 10s adb shell input keyevent 82
        fi
        timeout --signal=INT --kill-after=10s 2m adb shell am instrument -w -e authProcessPhase "$phase" -e class br.com.diariodeclasse.AuthProcessProbeTest "$RUNNER" > "build/auth-process-$phase.txt"
        cat "build/auth-process-$phase.txt"
        check_result "build/auth-process-$phase.txt" 1
    done
fi
