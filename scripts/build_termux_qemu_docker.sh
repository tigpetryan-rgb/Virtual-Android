#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readarray -t LOCK < <(python3 - "$ROOT/config/toolchain.lock.json" <<'PY'
import json, sys
x=json.load(open(sys.argv[1]))['toolchains']['termux_packages']
print(x['repository']); print(x['commit'])
PY
)
TERMUX_REPO="${LOCK[0]}"
TERMUX_COMMIT="${LOCK[1]}"
TERMUX_PACKAGES="${1:-${TERMUX_PACKAGES:-$ROOT/.work/termux-packages}}"

if [[ ! -d "$TERMUX_PACKAGES/.git" ]]; then
  mkdir -p "$TERMUX_PACKAGES"
  git -C "$TERMUX_PACKAGES" init
  git -C "$TERMUX_PACKAGES" remote add origin "$TERMUX_REPO"
else
  git -C "$TERMUX_PACKAGES" remote set-url origin "$TERMUX_REPO"
fi
if ! git -C "$TERMUX_PACKAGES" cat-file -e "$TERMUX_COMMIT^{commit}" 2>/dev/null; then
  git -C "$TERMUX_PACKAGES" fetch --depth=1 origin "$TERMUX_COMMIT"
fi
git -C "$TERMUX_PACKAGES" checkout --detach --force "$TERMUX_COMMIT"
ACTUAL_COMMIT="$(git -C "$TERMUX_PACKAGES" rev-parse HEAD)"
[[ "$ACTUAL_COMMIT" == "$TERMUX_COMMIT" ]] || { echo "Termux commit mismatch: $ACTUAL_COMMIT" >&2; exit 2; }

echo "Using pinned termux-packages commit $ACTUAL_COMMIT"
"$ROOT/scripts/install_termux_qemu_recipe.sh" "$TERMUX_PACKAGES"
(
  cd "$TERMUX_PACKAGES"
  ./scripts/run-docker.sh ./build-package.sh -f -I -a aarch64 va-qemu-p1
)

echo
echo "QEMU package build completed from pinned termux-packages $TERMUX_COMMIT."
echo "Extract the generated .deb and run scripts/import_termux_qemu.sh on its Termux prefix."
