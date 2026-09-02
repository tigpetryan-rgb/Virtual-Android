#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

from release_common import iter_files

KNOWN_PROOF_NAMES = {
    "latest-p1.json",
    "latest-p2.json",
    "latest-p3-display.json",
    "latest-p3-input.json",
    "p1-device-proof.json",
}
PROOF_NAME_RX = re.compile(r"(?:^|[-_.])(p[123]|device)[-_.].*proof|proof.*(?:p[123]|device)", re.I)
PASS_WORDS = {"PASS", "PASSED", "VERIFIED"}
PROOF_KEYS = {
    "markerSeen", "marker_seen", "lastStage", "last_stage", "framebuffer",
    "frameworkReady", "framework_ready", "guestMarker", "guest_marker",
}


def is_proof_candidate(path: Path, data: object) -> bool:
    name = path.name.lower()
    if name in KNOWN_PROOF_NAMES or PROOF_NAME_RX.search(name):
        return True
    if "reports" in {part.lower() for part in path.parts} and path.suffix.lower() == ".json":
        return True
    if isinstance(data, dict):
        keys = set(data)
        if keys & PROOF_KEYS and ("success" in keys or "state" in keys or "status" in keys):
            return True
    return False


def pass_claim(data: object) -> bool:
    if not isinstance(data, dict):
        return False
    if data.get("success") is True:
        return True
    for key in ("state", "status", "result"):
        value = data.get(key)
        if isinstance(value, str) and value.upper() in PASS_WORDS:
            return True
    return False


def validate(root: Path) -> list[str]:
    errors: list[str] = []
    for path in root.rglob("*"):
        if path.is_symlink():
            errors.append(f"symlink is not allowed in deterministic release tree: {path.relative_to(root)}")
    for path in iter_files(root, include_generated=True):
        if path.suffix.lower() != ".json":
            continue
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError):
            continue
        if is_proof_candidate(path.relative_to(root), data) and pass_claim(data):
            errors.append(
                "stale/device acceptance PASS proof must not be bundled: "
                + path.relative_to(root).as_posix()
            )
    status = root / "BUILD_STATUS.md"
    if status.is_file():
        headings = [line.strip() for line in status.read_text(encoding="utf-8").splitlines() if line.startswith("## ")]
        if headings != ["## VERIFIED", "## NOT VERIFIED"]:
            errors.append("BUILD_STATUS.md must contain exactly '## VERIFIED' then '## NOT VERIFIED' status sections")
    return errors


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    ns = ap.parse_args()
    root = ns.root.resolve()
    errors = validate(root)
    if errors:
        for error in errors:
            print(f"FAIL {error}", file=sys.stderr)
        return 2
    print("Release tree guard: OK (no bundled PASS acceptance proof; BUILD_STATUS split is strict)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
