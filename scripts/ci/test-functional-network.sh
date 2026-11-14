#!/usr/bin/env bash
# Two disposable emulator installations, never an owner's device or a hosted project.
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${CI:-}" == true ]] || exit 1
primary="$(adb get-serialno)"
[[ "$primary" == emulator-* && "$(adb -s "$primary" shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || exit 1
secondary=emulator-5556
mkdir -p build/functional-network
cleanup(){ adb -s "$secondary" emu kill >/dev/null 2>&1 || true; }
trap cleanup EXIT
probe(){
  local device="$1" phase="$2" side="$3"
  adb -s "$device" shell am force-stop br.com.diariodeclasse
  timeout --signal=INT --kill-after=10s 3m adb -s "$device" shell am instrument -w -e timeout_msec 120000 \
    -e phaseBNetworkPhase "$phase" -e phaseBDevice "$side" -e class br.com.diariodeclasse.FunctionalNetworkProbeTest \
    br.com.diariodeclasse.test/br.com.diariodeclasse.AuthTestRunner > "build/functional-network/$side-$phase.txt"
  cat "build/functional-network/$side-$phase.txt"
  python3 - "$side" "$phase" <<'PY'
import sys
from pathlib import Path
t=Path(f'build/functional-network/{sys.argv[1]}-{sys.argv[2]}.txt').read_text()
assert 'OK (1 test)' in t and all(x not in t for x in ['FAILURES','INSTRUMENTATION_FAILED','Process crashed']),t
PY
}
# Fresh AVD, independent userdata image and app installation, same signed local fixture identity.
image="$(python3 - <<'PY'
import os
from pathlib import Path
p=Path(os.environ['ANDROID_HOME'])/'system-images/android-30'
for tag in sorted(p.iterdir()):
    if (tag/'x86_64/package.xml').is_file():
        print('system-images;android-30;'+tag.name+';x86_64');break
else:raise SystemExit('Expected installed API 30 image')
PY
)"
echo no | avdmanager create avd --force --name noctua_functional_secondary --package "$image"
"$ANDROID_HOME/emulator/emulator" -avd noctua_functional_secondary -port 5556 -no-window -no-audio -no-boot-anim -no-snapshot \
  -gpu swiftshader_indirect -feature -Vulkan -memory 1536 -cores 2 > build/functional-network/secondary-emulator.txt 2>&1 </dev/null &
timeout 180 adb -s "$secondary" wait-for-device
python3 - "$secondary" <<'PY'
import subprocess,sys,time
for _ in range(120):
    if subprocess.check_output(['adb','-s',sys.argv[1],'shell','getprop','sys.boot_completed'],text=True).strip()=='1':break
    time.sleep(1)
else:raise SystemExit('Secondary emulator did not boot')
PY
adb -s "$secondary" shell input keyevent 82
for option in window_animation_scale transition_animation_scale animator_duration_scale; do adb -s "$secondary" shell settings put global "$option" 0; done
adb -s "$secondary" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$secondary" install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

# Only this workflow-created primary AVD: discard the preceding synthetic UI fixtures.
adb -s "$primary" shell pm clear br.com.diariodeclasse
probe "$primary" c-functional-seed primary
probe "$secondary" c-functional-recover secondary
probe "$primary" c-functional-clear-offline primary
probe "$primary" c-functional-reopen primary
adb -s "$primary" shell svc wifi enable
adb -s "$primary" shell svc data enable
probe "$primary" c-functional-sent primary
probe "$secondary" c-functional-receive-remove secondary
probe "$primary" c-functional-final primary
probe "$primary" c-functional-activities primary
probe "$secondary" c-functional-activities-recover secondary
probe "$primary" c-functional-activities-final primary
printf '%s\n' '10 functional P/F phases passed on two disposable AVDs and real local HTTP.' > build/functional-network/summary.txt
