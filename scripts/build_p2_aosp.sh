#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
AOSP_ROOT=${AOSP_ROOT:-${1:-}}
VARIANT=${P2_VARIANT:-eng}
JOBS=${JOBS:-$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo 8)}
if [[ -z "$AOSP_ROOT" || ! -f "$AOSP_ROOT/build/envsetup.sh" ]]; then
  echo "Set AOSP_ROOT or pass /path/to/aosp" >&2
  exit 2
fi
if [[ "$VARIANT" != "eng" && "$VARIANT" != "userdebug" ]]; then
  echo "P2_VARIANT must be eng or userdebug" >&2
  exit 2
fi
"$ROOT/scripts/install_p2_aosp_overlay.sh" "$AOSP_ROOT"
cd "$AOSP_ROOT"
# shellcheck disable=SC1091
source build/envsetup.sh
export FVP_MULTILIB_BUILD=false
lunch "va_fvp-${VARIANT}"
m -j"$JOBS"
OUT="$ANDROID_PRODUCT_OUT"
for f in kernel combined-ramdisk.img system-qemu.img userdata.img; do
  [[ -s "$OUT/$f" ]] || { echo "Missing P2 output: $OUT/$f" >&2; exit 3; }
done
echo "P2 AOSP build complete: $OUT"
printf '%s\n' "$OUT" > "$ROOT/guest/p2/LAST_PRODUCT_OUT.txt"
