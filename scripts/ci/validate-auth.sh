#!/usr/bin/env bash
# Somente fixtures fictícias no emulador descartável. Nenhuma conta online é criada.
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${CI:-}" == true ]] || exit 1
[[ "$(adb get-serialno)" == emulator-* && "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || exit 1
RUNNER=br.com.diariodeclasse.test/br.com.diariodeclasse.AuthTestRunner
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
    timeout --signal=INT --kill-after=10s 5m adb shell am instrument -w -e timeout_msec 90000 -e class br.com.diariodeclasse.AuthIsolationInstrumentedTest,br.com.diariodeclasse.AuthUiInstrumentedTest,br.com.diariodeclasse.AuthClaimLayoutInstrumentedTest,br.com.diariodeclasse.FinalUxEntryInstrumentedTest "$RUNNER" > build/auth-focused.txt
    cat build/auth-focused.txt
    check_result build/auth-focused.txt 18
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
            adb reboot
            timeout 180s adb wait-for-device
            timeout 180s bash -c 'until [[ "$(adb shell getprop sys.boot_completed | tr -d "\r")" == 1 ]]; do sleep 2; done'
            adb shell input keyevent 82
        fi
        timeout --signal=INT --kill-after=10s 2m adb shell am instrument -w -e authProcessPhase "$phase" -e class br.com.diariodeclasse.AuthProcessProbeTest "$RUNNER" > "build/auth-process-$phase.txt"
        cat "build/auth-process-$phase.txt"
        check_result "build/auth-process-$phase.txt" 1
    done
fi
