#!/usr/bin/env bash
set -euo pipefail
PKG="${PKG:-com.example.virtualandroid}"
OUT="${1:-p1-device-proof.json}"
ADB="${ADB:-adb}"

command -v "$ADB" >/dev/null || { echo "adb not found" >&2; exit 2; }
"$ADB" get-state >/dev/null

TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT
if ! "$ADB" shell run-as "$PKG" cat files/reports/latest-p1.json >"$TMP" 2>/dev/null; then
  echo "Could not read latest P1 report via run-as." >&2
  echo "Install a debug build, run P1 once, then retry." >&2
  exit 2
fi
python3 -m json.tool "$TMP" >"$OUT"
echo "P1 proof saved: $OUT"
