#!/usr/bin/env bash
set -euo pipefail
PKG=${PKG:-com.example.virtualandroid}
ACTIVITY=${ACTIVITY:-com.example.virtualandroid.MainActivity}
LABEL=${1:-p3-device}
SERIAL_ARGS=()
if [[ -n "${ANDROID_SERIAL:-}" ]]; then SERIAL_ARGS=(-s "$ANDROID_SERIAL"); fi
OUTDIR=${OUTDIR:-p3-results}
mkdir -p "$OUTDIR"

# P3 is gated on a previous P2 framework PASS and the same seeded guest bundle.
adb "${SERIAL_ARGS[@]}" shell run-as "$PKG" test -s files/reports/latest-p2.json || {
  echo "No P2 proof. Run scripts/adb_smoke_p2.sh first." >&2; exit 3;
}
adb "${SERIAL_ARGS[@]}" exec-out run-as "$PKG" cat files/reports/latest-p2.json > "$OUTDIR/$LABEL-pre-p2.json"
python3 - "$OUTDIR/$LABEL-pre-p2.json" <<'PY'
import json,sys
p=sys.argv[1]
d=json.load(open(p))
if not d.get('success'):
    raise SystemExit('Latest P2 proof is not PASS')
PY
adb "${SERIAL_ARGS[@]}" shell run-as "$PKG" test -s files/p2/SHA256SUMS || {
  echo "No P2 bundle. Run scripts/adb_seed_p2.sh first." >&2; exit 4;
}

adb "${SERIAL_ARGS[@]}" shell run-as "$PKG" rm -f files/reports/latest-p3-display.json
adb "${SERIAL_ARGS[@]}" logcat -c
adb "${SERIAL_ARGS[@]}" shell am force-stop "$PKG"
adb "${SERIAL_ARGS[@]}" shell am start -n "$PKG/$ACTIVITY" --ez auto_p3 true >/dev/null

# App-side P3 watchdog owns VM lifetime. This waits for the stricter proof:
# current-run framework marker + current-run RFB framebuffer.
while ! adb "${SERIAL_ARGS[@]}" shell run-as "$PKG" test -s files/reports/latest-p3-display.json 2>/dev/null; do
  sleep 5
done
adb "${SERIAL_ARGS[@]}" exec-out run-as "$PKG" cat files/reports/latest-p3-display.json > "$OUTDIR/$LABEL.json"
adb "${SERIAL_ARGS[@]}" logcat -d -s VA-P3:I VA-P2:I VA-P1:I '*:S' > "$OUTDIR/$LABEL.log"
python3 - "$OUTDIR/$LABEL.json" <<'PY'
import json,sys
p=sys.argv[1]
d=json.load(open(p))
print(json.dumps(d, indent=2))
if not d.get('success') or not d.get('frameworkReadyInSameRun') or not d.get('frameReceivedInSameRun'):
    raise SystemExit(5)
if d.get('width', 0) <= 0 or d.get('height', 0) <= 0:
    raise SystemExit(6)
PY
echo "P3 DISPLAY PASS artifacts: $OUTDIR/$LABEL.json $OUTDIR/$LABEL.log"
echo "Touch transport is wired but still requires manual guest interaction validation in v0.7."
