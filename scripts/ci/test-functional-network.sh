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
  local device="$1" phase="$2" side="$3" testcase="${4:-FunctionalNetworkProbeTest}"
  printf 'probe=%s side=%s\n' "$phase" "$side"
  adb -s "$device" shell am force-stop br.com.diariodeclasse
  timeout --signal=INT --kill-after=10s 3m adb -s "$device" shell am instrument -w -e timeout_msec 120000 \
    -e phaseBNetworkPhase "$phase" -e phaseBDevice "$side" -e class "br.com.diariodeclasse.$testcase" \
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
serial=sys.argv[1];deadline=time.monotonic()+180
stable=0
while time.monotonic()<deadline:
    try:
        def shell(*args):return subprocess.run(['adb','-s',serial,'shell',*args],capture_output=True,text=True,timeout=5)
        boot=shell('getprop','sys.boot_completed')
        settings=shell('settings','get','global','window_animation_scale')
        package=shell('pm','path','android')
        ready=boot.returncode==0 and boot.stdout.strip()=='1' and settings.returncode==0 and package.returncode==0 and 'package:' in package.stdout
        if ready:float(settings.stdout.strip()) # Binder success, not merely an attached adb transport.
        stable=stable+1 if ready else 0
        if stable>=2:break
    except (subprocess.TimeoutExpired,ValueError):stable=0
    time.sleep(1) # Bounded polling of observed services, not a fixed boot delay.
else:raise SystemExit('Secondary Android boot/settings/package services not ready')
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

# A separate synthetic tenant and two fresh Room workspaces; no hosted credentials.
probe "$primary" e-auto-seed primary AcademicAutomaticNetworkProbeTest
probe "$secondary" e-auto-recover secondary AcademicAutomaticNetworkProbeTest
probe "$primary" e-auto-observe primary AcademicAutomaticNetworkProbeTest
probe "$primary" e-auto-offline-logout primary AcademicAutomaticNetworkProbeTest
probe "$primary" e-auto-reopen-offline primary AcademicAutomaticNetworkProbeTest
probe "$primary" e-auto-reconnect-lost primary AcademicAutomaticNetworkProbeTest
probe "$primary" e-auto-reconnect primary AcademicAutomaticNetworkProbeTest
probe "$secondary" e-auto-receive-offline secondary AcademicAutomaticNetworkProbeTest
printf '%s\n' '10 functional + 8 automatic phases passed on two disposable AVDs and real local HTTP.' > build/functional-network/summary.txt
