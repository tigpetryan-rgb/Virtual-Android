#!/usr/bin/env python3
from __future__ import annotations

import hashlib
from pathlib import Path
from typing import Iterable

GENERATED_METADATA = {
    "FILELIST.txt",
    "release/release-manifest.json",
}
VOLATILE_FILES = {
    "guest/p2/LAST_PRODUCT_OUT.txt",
}
IGNORED_DIRS = {".git", ".gradle", ".work", "build", "__pycache__", "dist"}
IGNORED_SUFFIXES = {".pyc", ".pyo", ".swp", ".tmp"}


def is_ignored(path: Path, root: Path, *, include_generated: bool = False) -> bool:
    rel = path.relative_to(root).as_posix()
    parts = path.relative_to(root).parts
    if any(part in IGNORED_DIRS for part in parts[:-1]):
        return True
    if path.suffix in IGNORED_SUFFIXES or path.name == ".DS_Store":
        return True
    if rel in VOLATILE_FILES:
        return True
    if not include_generated and rel in GENERATED_METADATA:
        return True
    return False


def iter_files(root: Path, *, include_generated: bool = False) -> Iterable[Path]:
    for path in sorted(root.rglob("*"), key=lambda p: p.relative_to(root).as_posix()):
        if path.is_symlink():
            continue
        if path.is_file() and not is_ignored(path, root, include_generated=include_generated):
            yield path


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()
