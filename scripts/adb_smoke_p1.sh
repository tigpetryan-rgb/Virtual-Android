#!/usr/bin/env bash
set -euo pipefail
PKG="${PKG:-com.example.virtualandroid}"
ACTIVITY="${ACTIVITY:-com.example.virtualandroid/.MainActivity}"
ADB="${ADB:-adb}"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-150}"
OUT_PREFIX="${1:-p1-smoke}"
REPORT="${OUT_PREFIX}.json"
LOG="${OUT_PREFIX}.log"

command -v "$ADB" >/dev/null || { echo "adb not found" >&2; exit 2; }
"$ADB" get-state >/dev/null

# Requires the normal debuggable variant so run-as can retrieve app-private proof.
if ! "$ADB" shell run-as "$PKG" true >/dev/null 2>&1; then
  echo "run-as failed for $PKG. Install a debug build first." >&2
  exit 2
fi

"$ADB" shell run-as "$PKG" rm -f files/reports/latest-p1.json >/dev/null 2>&1 || true
"$ADB" logcat -c || true
"$ADB" shell am force-stop "$PKG"
"$ADB" shell am start -n "$ACTIVITY" --ez auto_p1 true >/dev/null

echo "P1 started; waiting for app proof (timeout ${TIMEOUT_SECONDS}s)..."
deadline=$((SECONDS + TIMEOUT_SECONDS))
while (( SECONDS < deadline )); do
  if "$ADB" shell run-as "$PKG" test -s files/reports/latest-p1.json >/dev/null 2>&1; then
    "$ADB" shell run-as "$PKG" cat files/reports/latest-p1.json | tr -d '\r' > "$REPORT"
    "$ADB" logcat -d -s 'VA-P1:I' '*:S' > "$LOG" || true
    python3 -m json.tool "$REPORT" >/dev/null
    if python3 - "$REPORT" <<'PY'
import json,sys
j=json.load(open(sys.argv[1]))
raise SystemExit(0 if j.get('success') is True else 1)
PY
    then
      echo "PASS: $REPORT"
      echo "Log:  $LOG"
      exit 0
    fi
    echo "FAIL: report was produced but success=false" >&2
    python3 -m json.tool "$REPORT" >&2 || true
    echo "Log: $LOG" >&2
    exit 1
  fi
  sleep 2
done

"$ADB" logcat -d -s 'VA-P1:I' '*:S' > "$LOG" || true
echo "TIMEOUT: no latest-p1.json after ${TIMEOUT_SECONDS}s" >&2
echo "Log: $LOG" >&2
exit 1
