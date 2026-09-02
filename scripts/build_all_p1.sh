#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TERMUX_PACKAGES="${TERMUX_PACKAGES:-${1:-$ROOT/.work/termux-packages}}"
WORK="${P1_ALL_WORK:-$ROOT/.work/p1-all}"
mkdir -p "$WORK"

need() { command -v "$1" >/dev/null || { echo "missing tool: $1" >&2; exit 2; }; }
for t in python3 dpkg-deb; do need "$t"; done

echo "== [1/6] deterministic initramfs =="
"$ROOT/scripts/build_p1_initramfs.sh"

echo "== [2/6] pinned ARM64 Linux kernel =="
"$ROOT/scripts/build_p1_kernel.sh"

echo "== [3/6] Android/Bionic QEMU via Termux Docker =="
"$ROOT/scripts/build_termux_qemu_docker.sh" "$TERMUX_PACKAGES"

echo "== [4/6] extract and import QEMU runtime closure =="
DEB="$(find "$TERMUX_PACKAGES/output" -maxdepth 1 -type f \( -name 'va-qemu-p1_*.deb' -o -name 'va-qemu-p1*.deb' \) -printf '%T@ %p\n' 2>/dev/null | sort -nr | head -1 | cut -d' ' -f2-)"
if [[ -z "$DEB" || ! -f "$DEB" ]]; then
  echo "Could not find va-qemu-p1 .deb under $TERMUX_PACKAGES/output" >&2
  exit 2
fi
EXTRACT="$WORK/qemu-deb-root"
rm -rf "$EXTRACT"
mkdir -p "$EXTRACT"
dpkg-deb -x "$DEB" "$EXTRACT"
PREFIX="$EXTRACT/data/data/com.termux/files/usr"
if [[ ! -x "$PREFIX/bin/qemu-system-aarch64" && ! -f "$PREFIX/bin/qemu-system-aarch64" ]]; then
  echo "Extracted package does not contain $PREFIX/bin/qemu-system-aarch64" >&2
  find "$EXTRACT" -maxdepth 7 -type f -name 'qemu-system-aarch64' -print >&2 || true
  exit 2
fi
"$ROOT/scripts/import_termux_qemu.sh" "$PREFIX"

echo "== [5/6] P1 artifact closure =="
"$ROOT/scripts/check_p1_assets.sh"
"$ROOT/scripts/verify_project_source.py"

echo "== [6/6] Android debug APK =="
if [[ -x "$ROOT/gradlew" ]]; then
  GRADLE=("$ROOT/gradlew")
elif command -v gradle >/dev/null; then
  GRADLE=(gradle)
else
  echo "Gradle is missing. Install Gradle 8.10.2 (required by AGP 8.8.x) or add a Gradle wrapper." >&2
  exit 2
fi
(
  cd "$ROOT"
  "${GRADLE[@]}" --no-daemon :app:assembleDebug
)
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
test -s "$APK"
echo "P1 debug APK ready: $APK ($(wc -c < "$APK") bytes)"
