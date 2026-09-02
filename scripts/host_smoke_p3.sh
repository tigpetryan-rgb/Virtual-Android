#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
command -v kotlinc >/dev/null || { echo "kotlinc is required" >&2; exit 2; }
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

kotlinc \
  "$ROOT/app/src/main/java/com/example/virtualandroid/vm/P2BootStage.kt" \
  "$ROOT/app/src/main/java/com/example/virtualandroid/vm/P2GuestArtifacts.kt" \
  "$ROOT/app/src/main/java/com/example/virtualandroid/vm/P2QemuArgs.kt" \
  "$ROOT/app/src/main/java/com/example/virtualandroid/vm/P3QemuArgs.kt" \
  "$ROOT/tests/P3PureSmoke.kt" \
  -include-runtime -d "$TMP/p3-pure.jar"
java -jar "$TMP/p3-pure.jar"

kotlinc \
  "$ROOT/app/src/main/java/com/example/virtualandroid/display/RfbProtocol.kt" \
  "$ROOT/tests/RfbProtocolSmoke.kt" \
  -include-runtime -d "$TMP/rfb.jar"
java -jar "$TMP/rfb.jar"
