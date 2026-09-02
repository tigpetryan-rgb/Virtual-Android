#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import zipfile
from datetime import datetime, timezone
from pathlib import Path

from generate_release_metadata import generate
from release_common import iter_files
from validate_release_tree import validate


def zip_datetime(epoch: int) -> tuple[int, int, int, int, int, int]:
    dt = datetime.fromtimestamp(epoch, tz=timezone.utc)
    year = max(1980, dt.year)
    return (year, dt.month, dt.day, dt.hour, dt.minute, dt.second - (dt.second % 2))


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    ap.add_argument("--output", type=Path, required=True)
    ns = ap.parse_args()
    root = ns.root.resolve()
    errors = validate(root)
    if errors:
        for error in errors:
            print(f"FAIL {error}")
        return 2

    filelist, manifest = generate(root)
    (root / "FILELIST.txt").write_bytes(filelist)
    (root / "release").mkdir(parents=True, exist_ok=True)
    (root / "release/release-manifest.json").write_bytes(manifest)
    lock = json.loads((root / "config/toolchain.lock.json").read_text(encoding="utf-8"))
    epoch = int(os.environ.get("SOURCE_DATE_EPOCH", lock["source_date_epoch"]))
    stamp = zip_datetime(epoch)

    output = ns.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    tmp = output.with_suffix(output.suffix + ".tmp")
    if tmp.exists():
        tmp.unlink()
    prefix = root.name + "/"
    with zipfile.ZipFile(tmp, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
        for path in iter_files(root, include_generated=True):
            rel = path.relative_to(root).as_posix()
            info = zipfile.ZipInfo(prefix + rel, date_time=stamp)
            info.compress_type = zipfile.ZIP_DEFLATED
            info.create_system = 3
            mode = 0o755 if (path.stat().st_mode & 0o111) else 0o644
            info.external_attr = mode << 16
            zf.writestr(info, path.read_bytes(), compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
    tmp.replace(output)
    print(f"Deterministic release ZIP: {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
