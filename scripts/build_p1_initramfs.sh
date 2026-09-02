#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/guest/p1/out"
mkdir -p "$OUT"

# Prefer an Android NDK clang if ANDROID_NDK_HOME is set. Because init.S uses
# raw Linux syscalls and is linked -nostdlib -static, no Android runtime is used.
if [[ -n "${ANDROID_NDK_HOME:-}" ]]; then
  HOST_TAG="${HOST_TAG:-linux-x86_64}"
  CLANG="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$HOST_TAG/bin/clang"
else
  CLANG="${CLANG:-clang}"
fi

"$CLANG" \
  --target=aarch64-linux-android26 \
  -nostdlib -static -fuse-ld=lld \
  -Wl,-e,_start -Wl,--build-id=none \
  "$ROOT/guest/p1/init.S" \
  -o "$OUT/init"

python3 "$ROOT/scripts/make_p1_initramfs.py" \
  "$OUT/init" \
  "$ROOT/app/src/main/assets/p1/initramfs.cpio.gz"

file "$OUT/init" || true
