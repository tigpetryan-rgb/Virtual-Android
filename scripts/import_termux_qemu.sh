#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PREFIX="${1:-${TERMUX_PREFIX:-}}"
if [[ -z "$PREFIX" ]]; then
  echo "usage: $0 /path/to/termux-prefix [qemu-system-aarch64]" >&2
  exit 2
fi
QEMU="${2:-$PREFIX/bin/qemu-system-aarch64}"
OUT="$ROOT/app/src/main/jniLibs/arm64-v8a"
ASSET_MANIFEST="$ROOT/app/src/main/assets/qemu/qemu-runtime-manifest.json"
mkdir -p "$(dirname "$ASSET_MANIFEST")"
python3 "$ROOT/scripts/import_qemu_runtime.py" \
  --prefix "$PREFIX" \
  --qemu "$QEMU" \
  --out "$OUT" \
  --manifest "$ASSET_MANIFEST"
mkdir -p "$ROOT/third_party/qemu"
cp "$ASSET_MANIFEST" "$ROOT/third_party/qemu/qemu-runtime-manifest.json"
"$ROOT/scripts/check_p1_assets.sh"
