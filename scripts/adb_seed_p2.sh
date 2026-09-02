#!/usr/bin/env bash
set -euo pipefail
PKG=${PKG:-com.example.virtualandroid}
BUNDLE=${1:-}
SERIAL_ARGS=()
if [[ -n "${ANDROID_SERIAL:-}" ]]; then SERIAL_ARGS=(-s "$ANDROID_SERIAL"); fi
if [[ -z "$BUNDLE" || ! -d "$BUNDLE" ]]; then
  echo "usage: $0 /path/to/p2-bundle" >&2
  exit 2
fi
for f in kernel combined-ramdisk.img system-qemu.img userdata.img SHA256SUMS; do
  [[ -s "$BUNDLE/$f" ]] || { echo "Missing $BUNDLE/$f" >&2; exit 3; }
done
adb "${SERIAL_ARGS[@]}" shell run-as "$PKG" mkdir -p files/p2
for f in kernel combined-ramdisk.img system-qemu.img userdata.img SHA256SUMS; do
  echo "Seeding $f..."
  adb "${SERIAL_ARGS[@]}" push "$BUNDLE/$f" "/data/local/tmp/va-$f" >/dev/null
  adb "${SERIAL_ARGS[@]}" shell "cat /data/local/tmp/va-$f | run-as $PKG sh -c 'cat > files/p2/$f'"
  adb "${SERIAL_ARGS[@]}" shell rm -f "/data/local/tmp/va-$f"
done
adb "${SERIAL_ARGS[@]}" shell run-as "$PKG" ls -lh files/p2
echo "P2 artifacts seeded into $PKG private storage."
