#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
status=0
for f in \
  "$ROOT/app/src/main/assets/p1/Image" \
  "$ROOT/app/src/main/assets/p1/initramfs.cpio.gz"; do
  if [[ -s "$f" ]]; then
    echo "OK   $f ($(wc -c < "$f") bytes)"
  else
    echo "MISS $f"
    status=1
  fi
done

QDIR="$ROOT/app/src/main/jniLibs/arm64-v8a"
QEMU="$QDIR/libqemu_system_aarch64.so"
if [[ -s "$QEMU" ]]; then
  echo "OK   $QEMU ($(wc -c < "$QEMU") bytes)"
  MANIFEST="$ROOT/app/src/main/assets/qemu/qemu-runtime-manifest.json"
  if [[ -s "$MANIFEST" ]]; then
    python3 "$ROOT/scripts/verify_packaged_qemu.py" "$QDIR" --manifest "$MANIFEST" || status=1
  else
    echo "MISS $MANIFEST"
    status=1
  fi
else
  echo "MISS $QEMU"
  status=1
fi
exit "$status"
