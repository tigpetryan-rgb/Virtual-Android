#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

printf '== shell syntax ==\n'
while IFS= read -r -d '' f; do
  bash -n "$f"
done < <(find "$ROOT/scripts" "$ROOT/third_party/qemu/termux" -type f \( -name '*.sh' -o -name 'build.sh' \) -print0 | sort -z)

printf '== python syntax ==\n'
python3 - "$ROOT" <<'PY'
import ast, pathlib, sys
root=pathlib.Path(sys.argv[1])
files=sorted(list((root/'scripts').glob('*.py')) + list((root/'tests').glob('*.py')))
for path in files:
    ast.parse(path.read_text(encoding='utf-8'), filename=str(path))
print(f'Python AST syntax: OK ({len(files)} files)')
PY

printf '== XML parse ==\n'
python3 - "$ROOT" <<'PY'
import pathlib, sys, xml.etree.ElementTree as ET
root=pathlib.Path(sys.argv[1])
files=sorted(root.rglob('*.xml'))
for path in files:
    ET.parse(path)
print(f'XML parse: OK ({len(files)} files)')
PY

printf '== YAML parse ==\n'
python3 - "$ROOT" <<'PY'
import pathlib, sys
try:
    import yaml
except ImportError as exc:
    raise SystemExit('PyYAML is required for CI workflow syntax validation; install the pinned version from config/toolchain.lock.json') from exc
root=pathlib.Path(sys.argv[1])
files=sorted((root/'.github/workflows').glob('*.yml'))
for path in files:
    yaml.safe_load(path.read_text(encoding='utf-8'))
print(f'YAML parse: OK ({len(files)} workflow files)')
PY

printf '== deterministic initramfs ==\n'
BEFORE="$(mktemp)"
trap 'rm -f "$BEFORE"' EXIT
cp "$ROOT/app/src/main/assets/p1/initramfs.cpio.gz" "$BEFORE"
"$ROOT/scripts/build_p1_initramfs.sh"
cmp "$BEFORE" "$ROOT/app/src/main/assets/p1/initramfs.cpio.gz"

printf '== invariants / toolchain / release guards ==\n'
"$ROOT/scripts/verify_project_source.py"
"$ROOT/scripts/verify_toolchain_lock.py"
"$ROOT/scripts/validate_release_tree.py"
python3 -m unittest -v "$ROOT/tests/test_release_tools.py"
"$ROOT/scripts/generate_release_metadata.py" --check
