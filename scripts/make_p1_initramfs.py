#!/usr/bin/env python3
"""Create a minimal gzip-compressed newc initramfs containing only /init."""
from __future__ import annotations
import argparse
import gzip
import os
import stat
import struct
from pathlib import Path


def pad4(b: bytearray) -> None:
    while len(b) % 4:
        b.append(0)


def field(v: int) -> bytes:
    return f"{v:08x}".encode("ascii")


def add_newc(out: bytearray, name: str, data: bytes, mode: int, ino: int) -> None:
    name_b = name.encode() + b"\0"
    header = b"070701" + b"".join([
        field(ino), field(mode), field(0), field(0), field(1), field(0),
        field(len(data)), field(0), field(0), field(0), field(0),
        field(len(name_b)), field(0),
    ])
    assert len(header) == 110
    out += header
    out += name_b
    pad4(out)
    out += data
    pad4(out)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("init", type=Path)
    ap.add_argument("output", type=Path)
    args = ap.parse_args()

    init_data = args.init.read_bytes()
    archive = bytearray()
    add_newc(archive, "init", init_data, stat.S_IFREG | 0o755, 1)
    add_newc(archive, "TRAILER!!!", b"", stat.S_IFREG, 2)

    args.output.parent.mkdir(parents=True, exist_ok=True)
    with gzip.GzipFile(filename="", mode="wb", fileobj=args.output.open("wb"), mtime=0) as gz:
        gz.write(archive)
    print(f"wrote {args.output} ({args.output.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
