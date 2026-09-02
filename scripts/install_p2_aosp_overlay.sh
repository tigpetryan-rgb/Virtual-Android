#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
AOSP_ROOT=${1:-}
if [[ -z "$AOSP_ROOT" || ! -f "$AOSP_ROOT/build/envsetup.sh" ]]; then
  echo "usage: $0 /path/to/aosp" >&2
  exit 2
fi
SRC="$ROOT/guest/p2/aosp-overlay/device/virtualandroid"
DST="$AOSP_ROOT/device/virtualandroid"
rm -rf "$DST"
mkdir -p "$(dirname "$DST")"
cp -a "$SRC" "$DST"
echo "Installed P2 overlay -> $DST"
