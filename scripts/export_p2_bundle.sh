#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
PRODUCT_OUT=${1:-${ANDROID_PRODUCT_OUT:-}}
DEST=${2:-"$ROOT/out/p2-bundle"}
if [[ -z "$PRODUCT_OUT" ]]; then
  echo "usage: $0 /path/to/out/target/product/fvpbase [dest]" >&2
  exit 2
fi
mkdir -p "$DEST"
rm -f "$DEST"/*
for f in kernel combined-ramdisk.img system-qemu.img userdata.img; do
  [[ -s "$PRODUCT_OUT/$f" ]] || { echo "Missing $PRODUCT_OUT/$f" >&2; exit 3; }
  cp "$PRODUCT_OUT/$f" "$DEST/$f"
done
(
  cd "$DEST"
  sha256sum kernel combined-ramdisk.img system-qemu.img userdata.img > SHA256SUMS
)
echo "P2 bundle exported to $DEST"
