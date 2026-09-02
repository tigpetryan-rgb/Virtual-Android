#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readarray -t LOCK < <(python3 - "$ROOT/config/toolchain.lock.json" <<'PY'
import json, sys
x=json.load(open(sys.argv[1]))['toolchains']['p2_aosp']
print(x['manifest_url']); print(x['manifest_tag'])
PY
)
MANIFEST_URL="${LOCK[0]}"
PINNED_AOSP_TAG="${LOCK[1]}"
DEST=${1:-aosp-p2}
AOSP_TAG=${AOSP_TAG:-$PINNED_AOSP_TAG}
JOBS=${JOBS:-8}
if [[ "$AOSP_TAG" != "$PINNED_AOSP_TAG" && "${ALLOW_UNPINNED_AOSP:-0}" != "1" ]]; then
  echo "Refusing unpinned AOSP tag $AOSP_TAG. Set ALLOW_UNPINNED_AOSP=1 to override." >&2
  exit 2
fi
command -v repo >/dev/null || { echo "repo tool is required" >&2; exit 2; }
mkdir -p "$DEST"
cd "$DEST"
repo init -u "$MANIFEST_URL" -b "$AOSP_TAG"
repo sync -c -j"$JOBS" --fail-fast
echo "AOSP P2 checkout ready: $(pwd) ($AOSP_TAG)"
