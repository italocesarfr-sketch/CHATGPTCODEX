#!/usr/bin/env bash
# Existing runner/harness and six automatic lifecycle phases; only fresh CI-owned AVDs.
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${CI:-}" == true && "${NOCTUA_COMMERCIAL_DISPOSABLE:-}" == YES ]] || exit 1
primary="$(adb get-serialno)"
[[ "$primary" == emulator-* && "$(adb -s "$primary" shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || exit 1
[[ "$(adb -s "$primary" emu avd name | head -1 | tr -d '\r')" == noctua_commercial_auto_* ]] || exit 1
secondary=emulator-5556
[[ "$(adb devices | awk '$1=="emulator-5556" {print $1}')" == '' ]] || exit 1
name="noctua_commercial_auto_second_${GITHUB_RUN_ID}_${GITHUB_RUN_ATTEMPT}"
mkdir -p build/commercial-tests/android
spawned=false
cleanup(){ if [[ "$spawned" == true ]]; then adb -s "$secondary" emu kill >/dev/null 2>&1 || true; fi; }
trap cleanup EXIT
probe(){
    local device="$1" phase="$2" side="$3"
    adb -s "$device" shell am force-stop br.com.diariodeclasse
    timeout --signal=INT --kill-after=10s 3m adb -s "$device" shell am instrument -w -r \
      -e timeout_msec 120000 -e phaseBNetworkPhase "$phase" -e phaseBDevice "$side" \
      -e class br.com.diariodeclasse.CommercialAutomaticNetworkProbeTest \
      br.com.diariodeclasse.test/br.com.diariodeclasse.AuthTestRunner > "build/commercial-tests/android/$phase.txt"
    python3 - "$phase" <<'PY'
from pathlib import Path
import sys
t=Path('build/commercial-tests/android/'+sys.argv[1]+'.txt').read_text()
assert 'OK (1 test)' in t and all(x not in t for x in ('FAILURES','INSTRUMENTATION_FAILED','Process crashed')),t
print(sys.argv[1]+': passed')
PY
}
probe "$primary" e-auto-commercial-init-lost primary
probe "$primary" e-auto-commercial-seed primary
image="$(python3 - <<'PY'
from pathlib import Path
import os
p=Path(os.environ['ANDROID_HOME'])/'system-images/android-30'
for tag in sorted(p.iterdir()):
    if (tag/'x86_64/package.xml').is_file():print('system-images;android-30;'+tag.name+';x86_64');break
else:raise SystemExit('API 30 image unavailable')
PY
)"
echo no | avdmanager create avd --name "$name" --package "$image"
"$ANDROID_HOME/emulator/emulator" -avd "$name" -port 5556 -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect -feature -Vulkan -memory 1536 -cores 2 > build/commercial-tests/android/second-boot.txt 2>&1 </dev/null &
spawned=true
timeout 180 adb -s "$secondary" wait-for-device
python3 - "$secondary" <<'PY'
import subprocess,sys,time
deadline=time.monotonic()+180
while time.monotonic()<deadline:
    r=subprocess.run(['adb','-s',sys.argv[1],'shell','getprop','sys.boot_completed'],capture_output=True,text=True,timeout=5)
    p=subprocess.run(['adb','-s',sys.argv[1],'shell','pm','path','android'],capture_output=True,text=True,timeout=5)
    if r.stdout.strip()=='1' and p.returncode==0 and 'package:' in p.stdout:break
    time.sleep(1)
else:raise SystemExit('New secondary AVD failed to boot')
PY
adb -s "$secondary" shell input keyevent 82
for option in window_animation_scale transition_animation_scale animator_duration_scale; do adb -s "$secondary" shell settings put global "$option" 0; done
adb -s "$secondary" install app/build/outputs/apk/debug/app-debug.apk
adb -s "$secondary" install -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
probe "$secondary" e-auto-commercial-recover secondary
probe "$primary" e-auto-commercial-observe-offline primary
probe "$primary" e-auto-commercial-reconnect primary
probe "$secondary" e-auto-commercial-observe-final secondary
adb -s "$primary" exec-out run-as br.com.diariodeclasse cat files/commercial-final.sha256 > build/commercial-tests/android/primary.sha256
adb -s "$secondary" exec-out run-as br.com.diariodeclasse cat files/commercial-final.sha256 > build/commercial-tests/android/secondary.sha256
cmp build/commercial-tests/android/primary.sha256 build/commercial-tests/android/secondary.sha256
printf '%s\n' '6 phases passed; two independent CI AVDs, actual local HTTP/Room/startup; identical final canonical hashes. Synthetic Auth, not hosted.' > build/commercial-tests/android/summary.txt
