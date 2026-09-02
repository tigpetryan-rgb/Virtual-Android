#!/usr/bin/env bash
set -euo pipefail
PKG=${PKG:-com.example.virtualandroid}
ACTIVITY=${ACTIVITY:-com.example.virtualandroid.MainActivity}
LABEL=${1:-p2-device}
SERIAL_ARGS=()
if [[ -n "${ANDROID_SERIAL:-}" ]]; then SERIAL_ARGS=(-s "$ANDROID_SERIAL"); fi
OUTDIR=${OUTDIR:-p2-results}
mkdir -p "$OUTDIR"

# P2 is deliberately gated on a previous P1 PASS and a seeded P2 bundle.
adb "${SERIAL_ARGS[@]}" shell run-as "$PKG" test -s files/reports/latest-p1.json || {
  echo "No P1 proof. Run P1 first." >&2; exit 3;
}
adb "${SERIAL_ARGS[@]}" shell run-as "$PKG" test -s files/p2/SHA256SUMS || {
  echo "No P2 bundle. Run scripts/adb_seed_p2.sh first." >&2; exit 4;
}
adb "${SERIAL_ARGS[@]}" shell run-as "$PKG" rm -f files/reports/latest-p2.json
adb "${SERIAL_ARGS[@]}" logcat -c
adb "${SERIAL_ARGS[@]}" shell am force-stop "$PKG"
adb "${SERIAL_ARGS[@]}" shell am start -n "$PKG/$ACTIVITY" --ez auto_p2 true >/dev/null

# Poll only for the proof file; the app-side watchdog owns VM timeout semantics.
while ! adb "${SERIAL_ARGS[@]}" shell run-as "$PKG" test -s files/reports/latest-p2.json 2>/dev/null; do
  sleep 5
done
adb "${SERIAL_ARGS[@]}" exec-out run-as "$PKG" cat files/reports/latest-p2.json > "$OUTDIR/$LABEL.json"
adb "${SERIAL_ARGS[@]}" logcat -d -s VA-P2:I VA-P1:I '*:S' > "$OUTDIR/$LABEL.log"
python3 - "$OUTDIR/$LABEL.json" <<'PY'
import json,sys
p=sys.argv[1]
d=json.load(open(p))
print(json.dumps(d, indent=2))
if not d.get('success'):
    raise SystemExit(5)
PY
echo "P2 PASS artifacts: $OUTDIR/$LABEL.json $OUTDIR/$LABEL.log"
