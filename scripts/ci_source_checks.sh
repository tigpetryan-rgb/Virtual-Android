#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

printf '== shell syntax ==\n'
while IFS= read -r -d '' f; do
  bash -n "$f"
done < <(find "$ROOT/scripts" "$ROOT/third_party/qemu/termux" -type f \( -name '*.sh' -o -name 'build.sh' \) -print0 | sort -z)

printf '== python syntax ==\n'
python3 - "$ROOT" <<'PY2'
import ast, pathlib, sys
root=pathlib.Path(sys.argv[1])
files=sorted(list((root/'scripts').glob('*.py')) + list((root/'tests').glob('*.py')))
for path in files:
    ast.parse(path.read_text(encoding='utf-8'), filename=str(path))
print(f'Python AST syntax: OK ({len(files)} files)')
PY2

printf '== XML parse ==\n'
python3 - "$ROOT" <<'PY2'
import pathlib, sys, xml.etree.ElementTree as ET
root=pathlib.Path(sys.argv[1])
files=sorted(root.rglob('*.xml'))
for path in files:
    ET.parse(path)
print(f'XML parse: OK ({len(files)} files)')
PY2

printf '== YAML parse ==\n'
python3 - "$ROOT" <<'PY2'
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
PY2

printf '== deterministic initramfs packaging ==\n'
PACK_A="$(mktemp)"
PACK_B="$(mktemp)"
trap 'rm -f "$PACK_A" "$PACK_B"' EXIT
python3 "$ROOT/scripts/make_p1_initramfs.py" "$ROOT/guest/p1/out/init" "$PACK_A"
python3 "$ROOT/scripts/make_p1_initramfs.py" "$ROOT/guest/p1/out/init" "$PACK_B"
cmp "$PACK_A" "$PACK_B"
cmp "$PACK_A" "$ROOT/app/src/main/assets/p1/initramfs.cpio.gz"

printf '== invariants / toolchain / release guards ==\n'
"$ROOT/scripts/verify_project_source.py"
"$ROOT/scripts/verify_toolchain_lock.py"
"$ROOT/scripts/validate_release_tree.py"
python3 -m unittest -v "$ROOT/tests/test_release_tools.py"
python3 -m unittest -v "$ROOT/tests/test_p1_report_validator.py"
"$ROOT/scripts/generate_release_metadata.py" --check
