#!/usr/bin/env python3
from __future__ import annotations

import argparse
import re
import shutil
import subprocess
from pathlib import Path

LOAD_RX = re.compile(r"^\s*LOAD\s+.*?\s+(0x[0-9a-fA-F]+)\s*$", re.M)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("paths", nargs="+", type=Path)
    ap.add_argument("--min-load-align", type=int, default=16384)
    ns = ap.parse_args()
    readelf = shutil.which("llvm-readelf") or shutil.which("readelf")
    if not readelf:
        print("NOT VERIFIED: readelf/llvm-readelf is not installed")
        return 3
    files: list[Path] = []
    for item in ns.paths:
        if item.is_dir():
            files.extend(sorted(item.rglob("*.so")))
        elif item.is_file():
            files.append(item)
    if not files:
        print("NOT VERIFIED: no ELF files matched")
        return 3
    bad = []
    for path in files:
        text = subprocess.check_output([readelf, "-lW", str(path)], text=True, stderr=subprocess.STDOUT)
        aligns = [int(value, 16) for value in LOAD_RX.findall(text)]
        shown = ",".join(hex(value) for value in aligns) if aligns else "none"
        print(f"ELF {path}: PT_LOAD align={shown}")
        if not aligns or min(aligns) < ns.min_load_align:
            bad.append((path, aligns))
    if bad:
        for path, aligns in bad:
            print(f"FAIL {path}: requires every PT_LOAD alignment >= {hex(ns.min_load_align)}; got {aligns}")
        return 2
    print(f"16 KiB ELF verifier: OK ({len(files)} files)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
