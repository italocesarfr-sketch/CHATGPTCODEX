#!/usr/bin/env bash
# Two disposable emulator installations, never an owner's device or a hosted project.
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${CI:-}" == true ]] || exit 1
primary="$(adb get-serialno)"
[[ "$primary" == emulator-* && "$(adb -s "$primary" shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || exit 1
secondary=emulator-5556
mkdir -p build/phaseb-network
cleanup(){ adb -s "$secondary" emu kill >/dev/null 2>&1 || true; }
trap cleanup EXIT
probe(){
  local device="$1" phase="$2" side="$3"
  adb -s "$device" shell am force-stop br.com.diariodeclasse
  timeout --signal=INT --kill-after=10s 3m adb -s "$device" shell am instrument -w -e timeout_msec 120000 \
    -e phaseBNetworkPhase "$phase" -e phaseBDevice "$side" -e class br.com.diariodeclasse.PhaseBNetworkProbeTest \
    br.com.diariodeclasse.test/br.com.diariodeclasse.AuthTestRunner > "build/phaseb-network/$side-$phase.txt"
  cat "build/phaseb-network/$side-$phase.txt"
  python3 - "$side" "$phase" <<'PY'
import sys
from pathlib import Path
t=Path(f'build/phaseb-network/{sys.argv[1]}-{sys.argv[2]}.txt').read_text()
assert 'OK (1 test)' in t and all(x not in t for x in ['FAILURES','INSTRUMENTATION_FAILED','Process crashed']),t
PY
}
# Clear ONLY the synthetic CI fixture after the entire preceding regression captured its evidence.
adb -s "$primary" shell pm clear br.com.diariodeclasse >/dev/null
probe "$primary" seed primary
adb -s "$primary" shell svc wifi enable; adb -s "$primary" shell svc data enable
probe "$primary" lost primary
probe "$primary" retry primary
probe "$primary" offline-edit primary

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
echo no | avdmanager create avd --force --name noctua_phaseb_secondary --package "$image"
"$ANDROID_HOME/emulator/emulator" -avd noctua_phaseb_secondary -port 5556 -no-window -no-audio -no-boot-anim -no-snapshot \
  -gpu swiftshader_indirect -feature -Vulkan -memory 1536 -cores 2 > build/phaseb-network/secondary-emulator.txt 2>&1 </dev/null &
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
probe "$secondary" recover secondary
probe "$secondary" edit-secondary secondary
probe "$primary" observe-secondary primary
probe "$primary" conflict-local primary
probe "$secondary" conflict-remote secondary
adb -s "$primary" shell svc wifi enable; adb -s "$primary" shell svc data enable
probe "$primary" observe-conflict primary
for side in primary secondary; do
  device="$primary"; [[ "$side" == secondary ]] && device="$secondary"
  adb -s "$device" exec-out run-as br.com.diariodeclasse tar -cf - files/phaseb-network > "build/phaseb-network/$side-captures.tar"
done
printf '%s\n' '10 Android HTTP phases passed on two independent emulators. PostgreSQL/PostgREST local; Auth JWT fixtures, not hosted Supabase.' > build/phaseb-network/summary.txt

# Expand only after preserving B screenshots and conflict evidence.
./scripts/ci/test-phase-c-network.sh "$primary" "$secondary"
