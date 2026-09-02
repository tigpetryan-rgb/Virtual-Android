#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import subprocess
from pathlib import Path

SYSTEM = {"libc.so", "libm.so", "libdl.so", "liblog.so", "libandroid.so", "libz.so"}
NEEDED_RX = re.compile(r"\(NEEDED\).*Shared library: \[(.+?)\]")
LOAD_RX = re.compile(r"^\s*LOAD\s+.*?\s+(0x[0-9a-fA-F]+)\s*$", re.M)


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("dir", type=Path)
    ap.add_argument("--manifest", type=Path)
    ap.add_argument("--min-load-align", type=int, default=16384)
    ap.add_argument("--expected-machine", default="AArch64")
    ns = ap.parse_args()
    d = ns.dir
    readelf = shutil.which("llvm-readelf") or shutil.which("readelf")
    if not readelf:
        print("NOT VERIFIED: readelf not installed")
        return 3
    main = d / "libqemu_system_aarch64.so"
    if not main.is_file():
        print(f"MISS {main}")
        return 2
    bad_names = [p.name for p in d.iterdir() if p.is_file() and p.suffix != ".so" and p.name != "qemu-runtime-manifest.json"]
    if bad_names:
        print("BAD jniLibs filenames: " + ", ".join(sorted(bad_names)))
        return 2

    missing = set()
    bad_align = []
    actual: dict[str, dict] = {}
    files = {p.name: p for p in d.glob("*.so")}
    for name, p in sorted(files.items()):
        header = subprocess.check_output([readelf, "-h", str(p)], text=True, stderr=subprocess.STDOUT)
        machine_line = next((line for line in header.splitlines() if "Machine:" in line), "")
        if ns.expected_machine not in machine_line:
            print(f"BAD ELF machine {name}: expected {ns.expected_machine!r}; got {machine_line.strip()!r}")
            return 2
        dyn = subprocess.check_output([readelf, "-d", str(p)], text=True, stderr=subprocess.STDOUT)
        deps = NEEDED_RX.findall(dyn)
        unresolved = [x for x in deps if x not in SYSTEM and x not in files]
        missing.update(unresolved)
        ph = subprocess.check_output([readelf, "-lW", str(p)], text=True, stderr=subprocess.STDOUT)
        aligns = [int(x, 16) for x in LOAD_RX.findall(ph)]
        align_text = ",".join(hex(x) for x in aligns) if aligns else "none"
        print(f"ELF {name}: needed={', '.join(deps) if deps else '(none)'} load_align={align_text}")
        actual[name] = {
            "bytes": p.stat().st_size,
            "sha256": sha256(p),
            "needed": deps,
            "load_alignments": aligns,
        }
        if not aligns or min(aligns) < ns.min_load_align:
            bad_align.append((name, aligns))
    if missing:
        print("UNRESOLVED: " + ", ".join(sorted(missing)))
        return 2
    if bad_align:
        for name, aligns in bad_align:
            shown = ",".join(hex(x) for x in aligns) if aligns else "none"
            print(f"BAD 16K ALIGNMENT {name}: {shown}; require >= {hex(ns.min_load_align)}")
        return 2

    if ns.manifest:
        if not ns.manifest.is_file():
            print(f"MISS manifest {ns.manifest}")
            return 2
        manifest = json.loads(ns.manifest.read_text(encoding="utf-8"))
        if manifest.get("format") != 1 or manifest.get("host_abi") != "arm64-v8a":
            print("BAD runtime manifest format/ABI")
            return 2
        entries = {entry["name"]: entry for entry in manifest.get("files", [])}
        if set(entries) != set(actual):
            print(f"BAD manifest file set: manifest={sorted(entries)} actual={sorted(actual)}")
            return 2
        for name, observed in actual.items():
            expected = entries[name]
            for key in ("bytes", "sha256", "needed", "load_alignments"):
                if expected.get(key) != observed[key]:
                    print(f"BAD manifest {name} {key}: expected={expected.get(key)!r} actual={observed[key]!r}")
                    return 2
        if manifest.get("minimum_load_alignment") != ns.min_load_align:
            print("BAD manifest minimum_load_alignment")
            return 2
        if manifest.get("missing"):
            print("BAD manifest records unresolved dependencies: " + ", ".join(manifest["missing"]))
            return 2
        if manifest.get("bad_load_alignment"):
            print("BAD manifest records incompatible PT_LOAD alignment")
            return 2
        print(f"Runtime manifest hashes/closure match: {ns.manifest}")

    print(f"QEMU runtime closure OK: {len(files)} packaged ELF files; 16K-compatible PT_LOAD alignment")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
