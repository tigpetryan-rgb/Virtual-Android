#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TERMUX_PACKAGES="${1:-${TERMUX_PACKAGES:-}}"
if [[ -z "$TERMUX_PACKAGES" || ! -x "$TERMUX_PACKAGES/build-package.sh" ]]; then
  echo "usage: $0 /path/to/termux-packages" >&2
  exit 2
fi
SRC="$ROOT/third_party/qemu/termux/va-qemu-p1"
DST="$TERMUX_PACKAGES/packages/va-qemu-p1"
rm -rf "$DST"
mkdir -p "$DST"
cp -a "$SRC"/. "$DST"/
echo "Installed recipe: $DST"
echo "Build with: cd '$TERMUX_PACKAGES' && ./build-package.sh -a aarch64 va-qemu-p1"
