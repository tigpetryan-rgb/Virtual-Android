#!/usr/bin/env python3
from __future__ import annotations

import copy
import importlib.util
import sys
import time
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("validate_p1_report", ROOT / "scripts/validate_p1_report.py")
validator = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
sys.modules[SPEC.name] = validator
SPEC.loader.exec_module(validator)

SHA = "a" * 64
MARKER = validator.TERMINAL_MARKER


def good_report() -> dict:
    now = int(time.time() * 1000)
    return {
        "format": 3,
        "runId": "p1-test-run-1234",
        "backend": "TCG",
        "terminalMarker": MARKER,
        "terminalMarkerSeen": MARKER,
        "markerSeen": True,
        "success": True,
        "startedAtUtc": "2026-09-01T20:00:00.000Z",
        "finishedAtUtc": "2026-09-01T20:00:01.000Z",
        "startedAtEpochMs": now,
        "finishedAtEpochMs": now + 1000,
        "elapsedRealtimeStartNs": 1,
        "lastBootStage": "TERMINAL_MARKER",
        "app": {
            "packageName": "com.example.virtualandroid",
            "versionName": "0.7.0",
            "versionCode": 7,
            "firstInstallTime": now - 5000,
            "lastUpdateTime": now - 1000,
            "debuggable": True,
        },
        "host": {
            "manufacturer": "Example",
            "brand": "Example",
            "model": "Physical Phone",
            "device": "phone",
            "product": "phone",
            "buildId": "BUILD",
            "buildFingerprint": "example/phone/build:16/id:user/release-keys",
            "buildIncremental": "1",
            "sdk": 36,
            "supportedAbis": "arm64-v8a,armeabi-v7a",
        },
        "guest": {
            "memoryMiB": 1024,
            "vcpus": 2,
            "kernelSha256": SHA,
            "kernelBytes": 100,
            "initramfsSha256": SHA,
            "initramfsBytes": 100,
        },
        "qemu": {
            "version": "QEMU emulator version 10.2.1",
            "preflightLaunchPath": "DIRECT",
            "guestLaunchPath": "DIRECT",
            "exitCode": 143,
            "qemuSha256": SHA,
            "runtimeManifestSha256": SHA,
            "runtimeFileCount": 8,
            "runtimeBytes": 123456,
        },
        "timingMs": {"preflight": 10, "firstOutput": 20, "bootingLinux": 30, "bootMarker": 40, "total": 50},
        "failureCategory": None,
        "failure": None,
    }


class P1ProofValidatorTests(unittest.TestCase):
    def test_valid_current_run_success(self) -> None:
        report = good_report()
        validator.validate(
            report,
            expected_run_id=report["runId"],
            min_start_epoch_ms=report["startedAtEpochMs"],
            expected_fingerprint=report["host"]["buildFingerprint"],
            require_success=True,
        )

    def test_rejects_stale_wrong_run_id(self) -> None:
        with self.assertRaisesRegex(validator.ProofError, "stale/wrong runId"):
            validator.validate(good_report(), expected_run_id="p1-other-run-5678", require_success=True)

    def test_rejects_stale_timestamp(self) -> None:
        report = good_report()
        with self.assertRaisesRegex(validator.ProofError, "stale report timestamp"):
            validator.validate(report, min_start_epoch_ms=report["startedAtEpochMs"] + 60_000, require_success=True)

    def test_rejects_success_without_exact_terminal_marker(self) -> None:
        report = good_report()
        report["terminalMarkerSeen"] = "VA_P1_GUEST_OK"
        with self.assertRaisesRegex(validator.ProofError, "exact terminal marker"):
            validator.validate(report, require_success=True)

    def test_rejects_proof_from_previous_apk_install(self) -> None:
        report = good_report()
        report["app"]["lastUpdateTime"] = report["startedAtEpochMs"] + 1
        with self.assertRaisesRegex(validator.ProofError, "predates current APK"):
            validator.validate(report, require_success=True)

    def test_failure_report_can_be_schema_valid_without_pass_fields(self) -> None:
        report = good_report()
        report["success"] = False
        report["markerSeen"] = False
        report["terminalMarkerSeen"] = None
        report["lastBootStage"] = "PREPARING"
        report["failureCategory"] = "GUEST_ASSET"
        report["failure"] = "kernel missing"
        report["guest"]["kernelSha256"] = None
        report["qemu"]["qemuSha256"] = None
        validator.validate(report, require_success=False)


if __name__ == "__main__":
    unittest.main()
