#!/usr/bin/env python3
"""Validate Virtual Android P1 proof JSON, including same-run anti-stale constraints."""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

FORMAT = 3
BACKEND = "TCG"
TERMINAL_MARKER = "VA_P1_GUEST_OK: AArch64 Linux reached /init under QEMU TCG"
SHA256_RE = re.compile(r"^[0-9a-fA-F]{64}$")


class ProofError(ValueError):
    pass


def need(condition: bool, message: str) -> None:
    if not condition:
        raise ProofError(message)


def is_sha256(value: object) -> bool:
    return isinstance(value, str) and SHA256_RE.fullmatch(value) is not None


def validate(
    report: dict,
    *,
    expected_run_id: str | None = None,
    min_start_epoch_ms: int | None = None,
    expected_fingerprint: str | None = None,
    require_success: bool = False,
) -> None:
    need(report.get("format") == FORMAT, f"format must be {FORMAT}")
    run_id = report.get("runId")
    need(isinstance(run_id, str) and len(run_id) >= 8, "missing/invalid runId")
    if expected_run_id is not None:
        need(run_id == expected_run_id, f"stale/wrong runId: report={run_id!r} expected={expected_run_id!r}")

    need(report.get("backend") == BACKEND, "backend must be TCG")
    need(report.get("terminalMarker") == TERMINAL_MARKER, "terminalMarker contract mismatch")
    need(isinstance(report.get("success"), bool), "success must be boolean")
    need(isinstance(report.get("markerSeen"), bool), "markerSeen must be boolean")

    started = report.get("startedAtEpochMs")
    finished = report.get("finishedAtEpochMs")
    need(isinstance(started, int) and started > 0, "startedAtEpochMs missing/invalid")
    need(isinstance(finished, int) and finished >= started, "finishedAtEpochMs missing/before start")
    need(isinstance(report.get("startedAtUtc"), str) and report["startedAtUtc"].endswith("Z"), "startedAtUtc missing")
    need(isinstance(report.get("finishedAtUtc"), str) and report["finishedAtUtc"].endswith("Z"), "finishedAtUtc missing")
    if min_start_epoch_ms is not None:
        need(started >= min_start_epoch_ms - 5000, f"stale report timestamp: started={started} minimum={min_start_epoch_ms}")

    app = report.get("app")
    need(isinstance(app, dict), "app object missing")
    need(app.get("packageName") == "com.example.virtualandroid", "unexpected app packageName")
    need(isinstance(app.get("versionCode"), int) and app["versionCode"] > 0, "app versionCode missing")
    need(isinstance(app.get("lastUpdateTime"), int) and app["lastUpdateTime"] > 0, "app lastUpdateTime missing")
    need(started >= app["lastUpdateTime"], "proof predates current APK install/update")

    host = report.get("host")
    need(isinstance(host, dict), "host object missing")
    for key in ("manufacturer", "model", "device", "buildFingerprint"):
        need(isinstance(host.get(key), str) and host[key].strip(), f"host.{key} missing")
    need("arm64-v8a" in str(host.get("supportedAbis", "")), "host does not report arm64-v8a")
    if expected_fingerprint is not None:
        need(host.get("buildFingerprint") == expected_fingerprint, "device build fingerprint mismatch")

    guest = report.get("guest")
    need(isinstance(guest, dict), "guest object missing")
    qemu = report.get("qemu")
    need(isinstance(qemu, dict), "qemu object missing")
    timing = report.get("timingMs")
    need(isinstance(timing, dict), "timingMs object missing")

    if require_success:
        need(report.get("success") is True, "success is not true")
        need(report.get("markerSeen") is True, "markerSeen is not true")
        need(report.get("terminalMarkerSeen") == TERMINAL_MARKER, "exact terminal marker was not recorded")
        need(report.get("failure") is None, "successful proof contains failure")
        need(report.get("failureCategory") is None, "successful proof contains failureCategory")
        need(report.get("lastBootStage") == "TERMINAL_MARKER", "successful proof did not reach TERMINAL_MARKER stage")
        for key in ("kernelSha256", "initramfsSha256"):
            need(is_sha256(guest.get(key)), f"guest.{key} missing/invalid")
        for key in ("kernelBytes", "initramfsBytes"):
            need(isinstance(guest.get(key), int) and guest[key] > 0, f"guest.{key} missing/invalid")
        need(isinstance(qemu.get("version"), str) and "qemu" in qemu["version"].lower(), "QEMU version missing")
        need(qemu.get("guestLaunchPath") in {"DIRECT", "LINKER64"}, "guest QEMU launch path missing")
        need(is_sha256(qemu.get("qemuSha256")), "qemu.qemuSha256 missing/invalid")
        need(is_sha256(qemu.get("runtimeManifestSha256")), "qemu.runtimeManifestSha256 missing/invalid")
        need(isinstance(qemu.get("runtimeFileCount"), int) and qemu["runtimeFileCount"] > 0, "runtimeFileCount missing")
        need(isinstance(qemu.get("runtimeBytes"), int) and qemu["runtimeBytes"] > 0, "runtimeBytes missing")
        need(isinstance(timing.get("bootMarker"), int) and timing["bootMarker"] >= 0, "bootMarker timing missing")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("report", type=Path)
    ap.add_argument("--expected-run-id")
    ap.add_argument("--min-start-epoch-ms", type=int)
    ap.add_argument("--expected-fingerprint")
    ap.add_argument("--require-success", action="store_true")
    ns = ap.parse_args()
    try:
        data = json.loads(ns.report.read_text())
        need(isinstance(data, dict), "top-level JSON must be an object")
        validate(
            data,
            expected_run_id=ns.expected_run_id,
            min_start_epoch_ms=ns.min_start_epoch_ms,
            expected_fingerprint=ns.expected_fingerprint,
            require_success=ns.require_success,
        )
    except (OSError, json.JSONDecodeError, ProofError) as exc:
        print(f"P1 PROOF INVALID: {exc}", file=sys.stderr)
        return 2
    print(f"P1 proof valid: runId={data['runId']} success={str(data['success']).lower()} backend={data['backend']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
