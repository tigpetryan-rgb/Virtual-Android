#!/usr/bin/env python3
"""Deterministically reconstruct the proven CHAT-12 source release.

This script encodes only repair edits that were independently proven by
GitHub Actions ZIP-entry CRC checks and the final required SHA-256.
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import io
from pathlib import Path
import zipfile

EXPECTED_SHA256 = "fdf40b01bc1dd3cc68b39fa118aab9b312683756126df63c25b71bce15d086a1"
EXPECTED_ENTRIES = 109


def read_b64(path: Path) -> str:
    return "".join(path.read_text(encoding="utf-8").split())


def reconstruct(repo: Path) -> tuple[str, bytes]:
    parts_dir = repo / "baseline" / "parts"
    repair_dir = repo / "baseline" / "repair"
    parts = {int(p.stem): read_b64(p) for p in sorted(parts_dir.glob("*.b64"))}
    repairs = {p.stem: read_b64(p) for p in sorted(repair_dir.glob("*.b64"))}

    if sorted(parts) != list(range(9)):
        raise SystemExit(f"Expected parts 000..008, found {sorted(parts)}")

    # Same-length repairs proven by nominal-half diff + ZIP CRC.
    for i in (1, 2, 3):
        key = f"{i:03d}1"
        repair = repairs[key]
        if len(repair) != 10_000:
            raise SystemExit(f"Repair {key} length is {len(repair)}, expected 10000")
        if len(parts[i]) < 20_000:
            raise SystemExit(f"Part {i:03d} unexpectedly short before half repair")
        parts[i] = parts[i][:10_000] + repair

    # Structural edits proven by per-entry CRC solving.
    if len(parts[4]) != 20_001 or parts[4][16_995] != "i":
        raise SystemExit("Part 004 precondition mismatch at proven deletion offset 16995")
    parts[4] = parts[4][:16_995] + parts[4][16_996:]

    if len(parts[6]) != 19_999:
        raise SystemExit(f"Part 006 precondition length mismatch: {len(parts[6])}")
    parts[6] = "3" + parts[6]

    if len(parts[7]) != 19_999:
        raise SystemExit(f"Part 007 precondition length mismatch: {len(parts[7])}")
    parts[7] = "l" + parts[7]

    # Final same-length repair proven by the P3PureSmoke.kt entry CRC.
    if parts[7][8_282] != "U":
        raise SystemExit("Part 007 precondition mismatch at proven replacement offset 8282")
    parts[7] = parts[7][:8_282] + "E" + parts[7][8_283:]

    stream = "".join(parts[i] for i in range(9))
    if len(stream) != 175_004:
        raise SystemExit(f"Repaired Base64 length {len(stream)} != 175004")

    raw = base64.b64decode(stream, validate=True)
    sha = hashlib.sha256(raw).hexdigest()
    if sha != EXPECTED_SHA256:
        raise SystemExit(f"Recovered ZIP SHA-256 {sha} != required {EXPECTED_SHA256}")

    with zipfile.ZipFile(io.BytesIO(raw)) as zf:
        names = zf.namelist()
        bad = zf.testzip()
        if len(names) != EXPECTED_ENTRIES:
            raise SystemExit(f"ZIP entry count {len(names)} != expected {EXPECTED_ENTRIES}")
        if bad is not None:
            raise SystemExit(f"ZIP CRC validation failed at {bad}")

    return stream, raw


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", default=".")
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    repo = Path(args.repo).resolve()
    _stream, raw = reconstruct(repo)
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(raw)
    print(f"CHAT12_SOURCE_SHA256={EXPECTED_SHA256}")
    print(f"CHAT12_SOURCE_BYTES={len(raw)}")
    print(f"CHAT12_SOURCE_ENTRIES={EXPECTED_ENTRIES}")


if __name__ == "__main__":
    main()
