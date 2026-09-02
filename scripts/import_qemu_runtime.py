#!/usr/bin/env python3
"""Import a Termux-built qemu-system-aarch64 runtime into Android jniLibs.

This intentionally copies the ELF DT_NEEDED closure from a Termux prefix so P1
can prove Android/Bionic host execution before we spend time on a fully custom
minimal QEMU build.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

SYSTEM_LIBS = {
    "libc.so", "libm.so", "libdl.so", "liblog.so", "libandroid.so",
    "libz.so",  # Android provides zlib; prefer system copy for this P1 bridge.
}

NEEDED_RE = re.compile(r"\(NEEDED\).*Shared library: \[(.+?)\]")
MACHINE_RE = re.compile(r"^\s*Machine:\s*(.+?)\s*$", re.M)
TYPE_RE = re.compile(r"^\s*Type:\s*(.+?)\s*$", re.M)
LOAD_RE = re.compile(r"^\s*LOAD\s+.*?\s+(0x[0-9a-fA-F]+)\s*$", re.M)


def run(*args: str) -> str:
    return subprocess.check_output(args, text=True, stderr=subprocess.STDOUT)


def find_tool(explicit: str | None, candidates: list[str]) -> str:
    if explicit:
        p = shutil.which(explicit) or (explicit if os.path.isfile(explicit) else None)
        if p:
            return str(p)
        raise SystemExit(f"tool not found: {explicit}")
    for c in candidates:
        p = shutil.which(c)
        if p:
            return p
    raise SystemExit(f"none of these tools found: {', '.join(candidates)}")


def elf_header(readelf: str, path: Path) -> str:
    return run(readelf, "-h", str(path))


def needed(readelf: str, path: Path) -> list[str]:
    text = run(readelf, "-d", str(path))
    return NEEDED_RE.findall(text)


def load_alignments(readelf: str, path: Path) -> list[int]:
    text = run(readelf, "-lW", str(path))
    return [int(x, 16) for x in LOAD_RE.findall(text)]


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def locate_library(prefix: Path, soname: str) -> Path | None:
    candidates = [
        prefix / "lib" / soname,
        prefix / "lib64" / soname,
    ]
    for c in candidates:
        if c.is_file():
            return c
    # Last-resort search for versioned/relocated Termux libraries.
    matches = list(prefix.glob(f"**/{soname}"))
    return matches[0] if matches else None


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--prefix", required=True, type=Path, help="Termux prefix used for the QEMU build")
    ap.add_argument("--qemu", type=Path, help="qemu-system-aarch64 path; defaults to PREFIX/bin/qemu-system-aarch64")
    ap.add_argument("--out", required=True, type=Path, help="Android app jniLibs/arm64-v8a directory")
    ap.add_argument("--readelf", help="readelf/llvm-readelf executable")
    ap.add_argument("--patchelf", help="patchelf executable (required if a DT_NEEDED name is not Android jniLibs-safe)")
    ap.add_argument("--manifest", type=Path, help="manifest output path; defaults beside --out")
    ap.add_argument("--min-load-align", type=int, default=16384, help="minimum ELF PT_LOAD alignment; default 16384 for universal Android 15+ support")
    ns = ap.parse_args()

    prefix = ns.prefix.resolve()
    qemu = (ns.qemu or (prefix / "bin" / "qemu-system-aarch64")).resolve()
    out = ns.out.resolve()
    if not qemu.is_file():
        raise SystemExit(f"QEMU binary not found: {qemu}")

    readelf = find_tool(ns.readelf, ["llvm-readelf", "readelf"])
    patchelf = None
    if ns.patchelf:
        patchelf = find_tool(ns.patchelf, [ns.patchelf])
    else:
        patchelf = shutil.which("patchelf")

    hdr = elf_header(readelf, qemu)
    machine = MACHINE_RE.search(hdr)
    typ = TYPE_RE.search(hdr)
    if not machine or "AArch64" not in machine.group(1):
        raise SystemExit(f"QEMU host binary is not AArch64:\n{hdr}")
    if not typ or "DYN" not in typ.group(1):
        raise SystemExit("Expected Android PIE/ET_DYN QEMU executable")

    out.mkdir(parents=True, exist_ok=True)
    for old in out.glob("libva_qemu_*.so"):
        old.unlink()
    main_dst = out / "libqemu_system_aarch64.so"
    shutil.copy2(qemu, main_dst)

    def packaged_name(soname: str) -> str:
        # Android Gradle jniLibs reliably packages lib*.so. Versioned Linux
        # SONAMEs such as libfoo.so.0 are rewritten to an APK-safe filename.
        if re.fullmatch(r"lib[^/]+\.so", soname):
            return soname
        safe = re.sub(r"[^A-Za-z0-9_]+", "_", soname).strip("_")
        return f"libva_qemu_{safe}.so"

    queue: list[tuple[str, Path]] = [(main_dst.name, main_dst)]
    source_for: dict[str, Path] = {main_dst.name: qemu}
    copied: dict[str, Path] = {main_dst.name: main_dst}
    original_to_packaged: dict[str, str] = {}
    graph: dict[str, list[str]] = {}
    missing: set[str] = set()

    while queue:
        current_name, current = queue.pop(0)
        deps = needed(readelf, current)
        graph[current_name] = deps
        for soname in deps:
            if soname in SYSTEM_LIBS:
                continue
            if soname in original_to_packaged:
                continue
            src = locate_library(prefix, soname)
            if not src:
                missing.add(soname)
                continue
            dst_name = packaged_name(soname)
            dst = out / dst_name
            shutil.copy2(src, dst)
            original_to_packaged[soname] = dst_name
            copied[dst_name] = dst
            source_for[dst_name] = src
            queue.append((dst_name, dst))

    needs_rewrite = any(src != dst for src, dst in original_to_packaged.items())
    if needs_rewrite and not patchelf:
        renamed = [f"{src}->{dst}" for src, dst in sorted(original_to_packaged.items()) if src != dst]
        raise SystemExit("patchelf is required for jniLibs-safe SONAME rewrites: " + ", ".join(renamed))

    if patchelf:
        for path in copied.values():
            try:
                subprocess.run([patchelf, "--remove-rpath", str(path)], check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
                current_needed = needed(readelf, path)
                for soname in current_needed:
                    replacement = original_to_packaged.get(soname)
                    if replacement and replacement != soname:
                        subprocess.run([patchelf, "--replace-needed", soname, replacement, str(path)], check=True)
                if path.name != main_dst.name:
                    subprocess.run([patchelf, "--set-soname", path.name, str(path)], check=True)
            except subprocess.CalledProcessError as e:
                stderr = getattr(e, "stderr", b"") or b""
                raise SystemExit(f"patchelf failed for {path.name}: {stderr.decode(errors='replace')}")

    # Re-read dependency graph after any rewrite so the manifest reflects the APK.
    graph = {name: needed(readelf, path) for name, path in copied.items()}
    alignments = {name: load_alignments(readelf, path) for name, path in copied.items()}
    bad_alignment = {
        name: values for name, values in alignments.items()
        if not values or min(values) < ns.min_load_align
    }

    manifest = {
        "format": 1,
        "source_prefix": "termux-prefix",
        "qemu_source": qemu.relative_to(prefix).as_posix() if qemu.is_relative_to(prefix) else qemu.name,
        "host_abi": "arm64-v8a",
        "main": main_dst.name,
        "files": [
            {
                "name": name,
                "bytes": path.stat().st_size,
                "sha256": sha256(path),
                "source": source_for[name].relative_to(prefix).as_posix() if source_for[name].is_relative_to(prefix) else source_for[name].name,
                "needed": graph.get(name, needed(readelf, path)),
                "load_alignments": alignments.get(name, []),
            }
            for name, path in sorted(copied.items())
        ],
        "system_libraries_not_copied": sorted(SYSTEM_LIBS),
        "soname_rewrites": dict(sorted((k, v) for k, v in original_to_packaged.items() if k != v)),
        "minimum_load_alignment": ns.min_load_align,
        "bad_load_alignment": {k: v for k, v in sorted(bad_alignment.items())},
        "missing": sorted(missing),
    }
    manifest_path = (ns.manifest or (out / "qemu-runtime-manifest.json")).resolve()
    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n")

    total = sum(p.stat().st_size for p in copied.values())
    print(f"Imported {len(copied)} ELF files, {total / (1024*1024):.1f} MiB")
    print(f"Main: {main_dst}")
    print(f"Manifest: {manifest_path}")
    if missing:
        print("ERROR unresolved DT_NEEDED: " + ", ".join(sorted(missing)), file=sys.stderr)
        return 2
    if bad_alignment:
        for name, values in sorted(bad_alignment.items()):
            shown = ",".join(hex(v) for v in values) if values else "(no LOAD segments)"
            print(f"ERROR {name}: PT_LOAD alignments {shown}; require >= {hex(ns.min_load_align)}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
