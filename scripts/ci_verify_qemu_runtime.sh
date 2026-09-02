#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
QDIR="$ROOT/app/src/main/jniLibs/arm64-v8a"
MANIFEST="$ROOT/app/src/main/assets/qemu/qemu-runtime-manifest.json"
if [[ ! -s "$QDIR/libqemu_system_aarch64.so" || ! -s "$MANIFEST" ]]; then
  echo "NOT VERIFIED: imported QEMU runtime is not present in this source snapshot"
  exit 0
fi
python3 "$ROOT/scripts/verify_packaged_qemu.py" "$QDIR" --manifest "$MANIFEST"
