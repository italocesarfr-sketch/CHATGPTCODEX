#!/usr/bin/env bash
# Execute independent proofs even when one fails; the aggregate remains failed.
set -uo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${CI:-}" == true ]] || exit 1
[[ "$(adb get-serialno)" == emulator-* && "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || exit 1
status=0
./scripts/ci/test-functional-network.sh || status=1
# The primary was created by this workflow. These are solely our completed synthetic network fixtures.
adb shell pm clear br.com.diariodeclasse >/dev/null || exit 1
./scripts/ci/test-functional-android.sh || status=1
exit "$status"
