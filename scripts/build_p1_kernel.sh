#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readarray -t LOCK < <(python3 - "$ROOT/config/toolchain.lock.json" <<'PY'
import json, sys
x=json.load(open(sys.argv[1]))['toolchains']['p1_linux']
print(x['version']); print(x['sha256']); print(x['url'])
PY
)
PINNED_VERSION="${LOCK[0]}"
PINNED_SHA256="${LOCK[1]}"
PINNED_URL="${LOCK[2]}"
VERSION="${LINUX_VERSION:-$PINNED_VERSION}"
JOBS="${JOBS:-$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo 4)}"
WORK="${P1_KERNEL_WORK:-$ROOT/.work/p1-kernel}"
TARBALL="$WORK/linux-$VERSION.tar.xz"
SRC="$WORK/linux-$VERSION"
URL="$PINNED_URL"
if [[ "$VERSION" != "$PINNED_VERSION" ]]; then
  URL="https://cdn.kernel.org/pub/linux/kernel/v6.x/linux-$VERSION.tar.xz"
fi
OUT="$ROOT/app/src/main/assets/p1/Image"
mkdir -p "$WORK" "$(dirname "$OUT")"

for t in curl tar make clang ld.lld llvm-ar llvm-nm llvm-objcopy llvm-objdump llvm-readelf llvm-strip; do
  command -v "$t" >/dev/null || { echo "missing build tool: $t" >&2; exit 2; }
done

if [[ ! -s "$TARBALL" ]]; then
  echo "Downloading Linux $VERSION"
  curl -fL --retry 3 "$URL" -o "$TARBALL"
fi
if [[ "$VERSION" == "$PINNED_VERSION" ]]; then
  echo "$PINNED_SHA256  $TARBALL" | sha256sum -c -
elif [[ "${ALLOW_UNPINNED_KERNEL:-0}" != "1" ]]; then
  echo "Refusing unpinned Linux $VERSION. Set ALLOW_UNPINNED_KERNEL=1 to override." >&2
  exit 2
fi
if [[ ! -d "$SRC" ]]; then
  tar -C "$WORK" -xf "$TARBALL"
fi

make -C "$SRC" mrproper
make -C "$SRC" ARCH=arm64 LLVM=1 defconfig
"$SRC/scripts/config" --file "$SRC/.config" \
  -e BLK_DEV_INITRD \
  -e RD_GZIP \
  -e SERIAL_AMBA_PL011 \
  -e SERIAL_AMBA_PL011_CONSOLE \
  -e PRINTK \
  -e TTY \
  -e DEVTMPFS \
  -d RANDOMIZE_BASE
make -C "$SRC" ARCH=arm64 LLVM=1 olddefconfig
make -C "$SRC" ARCH=arm64 LLVM=1 -j"$JOBS" Image
cp "$SRC/arch/arm64/boot/Image" "$OUT"
echo "P1 kernel ready: $OUT ($(wc -c < "$OUT") bytes)"
