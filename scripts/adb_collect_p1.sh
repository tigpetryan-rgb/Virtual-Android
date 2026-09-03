#!/usr/bin/env bash
set -euo pipefail
PKG="${PKG:-com.example.virtualandroid}"
OUT="${1:-p1-device-proof.json}"
ADB="${ADB:-adb}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VALIDATOR="$ROOT/scripts/validate_p1_report.py"
ADB_ARGS=()
[[ -n "${ANDROID_SERIAL:-}" ]] && ADB_ARGS=(-s "$ANDROID_SERIAL")
adb_cmd() { "$ADB" "${ADB_ARGS[@]}" "$@"; }

command -v "$ADB" >/dev/null || { echo "adb not found: $ADB" >&2; exit 2; }
adb_cmd get-state >/dev/null

TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT
if ! adb_cmd exec-out run-as "$PKG" cat files/reports/latest-p1.json >"$TMP" 2>/dev/null; then
  echo "Could not read latest P1 report via run-as." >&2
  echo "Install a debug build, run P1 once, then retry." >&2
  exit 2
fi
python3 -m json.tool "$TMP" >"$OUT"
python3 "$VALIDATOR" "$OUT" ${REQUIRE_P1_SUCCESS:+--require-success}
echo "P1 proof saved and schema-validated: $OUT"
echo "Note: collection alone does not establish current-run hardware PASS; use adb_smoke_p1.sh for that gate."
