#!/usr/bin/env bash
set -euo pipefail
PKG="${PKG:-com.example.virtualandroid}"
ACTIVITY="${ACTIVITY:-com.example.virtualandroid/.MainActivity}"
ADB="${ADB:-adb}"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-150}"
OUT_PREFIX="${1:-p1-smoke}"
REPORT="${OUT_PREFIX}.json"
LOG="${OUT_PREFIX}.log"
DEVICE="${OUT_PREFIX}.device.txt"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VALIDATOR="$ROOT/scripts/validate_p1_report.py"
ADB_ARGS=()
[[ -n "${ANDROID_SERIAL:-}" ]] && ADB_ARGS=(-s "$ANDROID_SERIAL")
adb_cmd() { "$ADB" "${ADB_ARGS[@]}" "$@"; }

command -v "$ADB" >/dev/null || { echo "adb not found: $ADB" >&2; exit 2; }
command -v python3 >/dev/null || { echo "python3 not found" >&2; exit 2; }
adb_cmd get-state >/dev/null

ABILIST="$(adb_cmd shell getprop ro.product.cpu.abilist | tr -d '\r')"
[[ "$ABILIST" == *arm64-v8a* ]] || { echo "P1 requires ARM64 device; abilist=$ABILIST" >&2; exit 2; }
KERNEL_QEMU="$(adb_cmd shell getprop ro.kernel.qemu | tr -d '\r')"
BOOT_QEMU="$(adb_cmd shell getprop ro.boot.qemu | tr -d '\r')"
if [[ "$KERNEL_QEMU" == "1" || "$BOOT_QEMU" == "1" ]]; then
  echo "Refusing P1 hardware PASS on an emulator/QEMU Android target." >&2
  exit 2
fi

if ! adb_cmd shell run-as "$PKG" true >/dev/null 2>&1; then
  echo "run-as failed for $PKG. Install a debug build first." >&2
  exit 2
fi

RUN_ID="${P1_RUN_ID:-p1-$(date -u +%Y%m%dT%H%M%SZ)-$$-${RANDOM:-0}}"
START_EPOCH_MS="$(python3 - <<'PY'
import time
print(time.time_ns() // 1_000_000)
PY
)"
FINGERPRINT="$(adb_cmd shell getprop ro.build.fingerprint | tr -d '\r')"
SERIAL="$(adb_cmd get-serialno | tr -d '\r')"
{
  echo "runId=$RUN_ID"
  echo "serial=$SERIAL"
  echo "abilist=$ABILIST"
  echo "fingerprint=$FINGERPRINT"
  echo "manufacturer=$(adb_cmd shell getprop ro.product.manufacturer | tr -d '\r')"
  echo "model=$(adb_cmd shell getprop ro.product.model | tr -d '\r')"
  echo "device=$(adb_cmd shell getprop ro.product.device | tr -d '\r')"
  echo "sdk=$(adb_cmd shell getprop ro.build.version.sdk | tr -d '\r')"
  echo "startEpochMs=$START_EPOCH_MS"
} > "$DEVICE"

adb_cmd shell run-as "$PKG" rm -f files/reports/latest-p1.json
if adb_cmd shell run-as "$PKG" test -e files/reports/latest-p1.json >/dev/null 2>&1; then
  echo "Could not clear stale latest-p1.json; aborting acceptance run." >&2
  exit 2
fi
adb_cmd logcat -c || true
adb_cmd shell am force-stop "$PKG"
adb_cmd shell am start -n "$ACTIVITY" --ez auto_p1 true --es p1_run_id "$RUN_ID" >/dev/null

echo "P1 started: runId=$RUN_ID; waiting for current-run proof (timeout ${TIMEOUT_SECONDS}s)..."
deadline=$((SECONDS + TIMEOUT_SECONDS))
while (( SECONDS < deadline )); do
  if adb_cmd shell run-as "$PKG" test -s files/reports/latest-p1.json >/dev/null 2>&1; then
    adb_cmd exec-out run-as "$PKG" cat files/reports/latest-p1.json > "$REPORT"
    adb_cmd logcat -d -s 'VA-P1:I' '*:S' > "$LOG" || true
    if python3 "$VALIDATOR" "$REPORT" \
      --expected-run-id "$RUN_ID" \
      --min-start-epoch-ms "$START_EPOCH_MS" \
      --expected-fingerprint "$FINGERPRINT" \
      --require-success; then
      echo "PASS: fresh physical-device P1 proof validated"
      echo "Report: $REPORT"
      echo "Log:    $LOG"
      echo "Device: $DEVICE"
      exit 0
    fi
    echo "FAIL: report exists but did not satisfy current-run P1 PASS contract" >&2
    python3 -m json.tool "$REPORT" >&2 || true
    echo "Log: $LOG" >&2
    exit 1
  fi
  sleep 2
done

adb_cmd logcat -d -s 'VA-P1:I' '*:S' > "$LOG" || true
echo "TIMEOUT: no current-run latest-p1.json after ${TIMEOUT_SECONDS}s; runId=$RUN_ID" >&2
echo "Log: $LOG" >&2
echo "Device: $DEVICE" >&2
exit 1
