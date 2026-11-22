#!/usr/bin/env bash
# Sourced by the established commercial runner, reusing only its two CI-owned AVDs.
[[ "${GITHUB_ACTIONS:-}" == true && "${NOCTUA_COMMERCIAL_FINAL:-}" == YES && "${NOCTUA_COMMERCIAL_DISPOSABLE:-}" == YES ]] || exit 1
# 9: distinct concurrent edits, then repeat across real Android process death.
probe "$primary" e-auto-final-offline-one primary
probe "$secondary" e-auto-final-peer-one secondary
probe "$primary" e-auto-final-converge-one primary
probe "$secondary" e-auto-final-peer-observe-one secondary
probe "$primary" e-auto-final-offline-restart primary
probe "$secondary" e-auto-final-peer-two secondary
probe "$primary" e-auto-final-reopen-offline primary
probe "$primary" e-auto-final-converge-two primary
probe "$secondary" e-auto-final-peer-observe-two secondary
# 10: explicit choice of four over nine, obsolete decision, third revision,
# lost resolution reply, process restart and confirmed receipt replay.
probe "$primary" e-auto-final-conflict-local primary
probe "$secondary" e-auto-final-conflict-remote secondary
probe "$primary" e-auto-final-resolve-local primary
probe "$secondary" e-auto-final-observe-local secondary
probe "$primary" e-auto-final-stale-local primary
probe "$secondary" e-auto-final-stale-remote secondary
probe "$primary" e-auto-final-decision-lost primary
probe "$primary" e-auto-final-decision-replay primary
probe "$secondary" e-auto-final-observe-remote secondary
# 11: pending writes survive actual logout, secure vault recreation, login,
# lost server reply and restart; no explicit activation or engine invocation.
probe "$primary" e-auto-final-logout-pending primary
probe "$primary" e-auto-final-login-after-logout primary
probe "$primary" e-auto-final-push-lost primary
probe "$primary" e-auto-final-push-replay primary
probe "$secondary" e-auto-final-observe-push secondary
# 12: same application A -> unentitled B -> A; SQL/HTTP negative probes also run.
probe "$primary" e-auto-final-isolation primary
# 13: encrypted old backup, local pending operation and newer remote enrollment;
# restore remains paused until explicit UI KEEP_REMOTE, with queue evidence retained.
probe "$primary" e-auto-final-backup primary
probe "$secondary" e-auto-final-after-backup secondary
probe "$primary" e-auto-final-restore primary
probe "$primary" e-auto-final-final primary
probe "$secondary" e-auto-final-final secondary
collect_final_evidence
for side in primary secondary; do
    device="$primary"; [[ "$side" != secondary ]] || device="$secondary"
    adb -s "$device" exec-out run-as br.com.diariodeclasse cat files/commercial-final-final.sha256 > "build/commercial-tests/android/final-$side.sha256"
done
cmp build/commercial-tests/android/final-primary.sha256 build/commercial-tests/android/final-secondary.sha256
python3 scripts/test-commercial-final-evidence.py
printf '%s\n' '29 final phases passed; 9-13, two fresh CI AVDs, actual local HTTP/Room/Keystore; hashes converge. Synthetic Auth, not hosted.' > build/commercial-tests/android/final-summary.txt
