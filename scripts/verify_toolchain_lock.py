#!/usr/bin/env python3
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

root = Path(__file__).resolve().parents[1]
lock = json.loads((root / "config/toolchain.lock.json").read_text(encoding="utf-8"))["toolchains"]
errors: list[str] = []


def require(condition: bool, message: str) -> None:
    if not condition:
        errors.append(message)


root_gradle = (root / "build.gradle.kts").read_text(encoding="utf-8")
app_gradle = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
kernel = (root / "scripts/build_p1_kernel.sh").read_text(encoding="utf-8")
aosp = (root / "scripts/init_p2_aosp_checkout.sh").read_text(encoding="utf-8")
qemu_recipe = (root / "third_party/qemu/termux/va-qemu-p1/build.sh").read_text(encoding="utf-8")
termux = (root / "scripts/build_termux_qemu_docker.sh").read_text(encoding="utf-8")

require(f'version "{lock["android_gradle_plugin"]["version"]}"' in root_gradle, "AGP version drifted from toolchain.lock.json")
require(f'version "{lock["kotlin"]["version"]}"' in root_gradle, "Kotlin version drifted from toolchain.lock.json")
require(f'compileSdk = {lock["android"]["compile_sdk"]}' in app_gradle, "compileSdk drifted from toolchain lock")
require(f'ndkVersion = "{lock["android"]["ndk"]}"' in app_gradle, "NDK version drifted from toolchain lock")
require(f'version = "{lock["android"]["cmake"]}"' in app_gradle, "CMake version drifted from toolchain lock")
require("toolchain.lock.json" in kernel, "P1 kernel builder must read the central toolchain lock")
require("toolchain.lock.json" in aosp, "AOSP checkout helper must read the central toolchain lock")
require("toolchain.lock.json" in termux, "Termux builder must read the central toolchain lock")
require(f'TERMUX_PKG_VERSION={lock["qemu"]["version"]}' in qemu_recipe, "QEMU version drifted from toolchain lock")
require(f'TERMUX_PKG_SHA256={lock["qemu"]["sha256"]}' in qemu_recipe, "QEMU SHA-256 drifted from toolchain lock")

source_workflow = (root / ".github/workflows/source-checks.yml").read_text(encoding="utf-8")
android_workflow = (root / ".github/workflows/android-compile.yml").read_text(encoding="utf-8")
for workflow_name, workflow in (("source-checks.yml", source_workflow), ("android-compile.yml", android_workflow)):
    require(lock["ci_linux"]["github_runner"] in workflow, f"{workflow_name} runner drifted from toolchain lock")
    require(f"java-version: '{lock['java']['major']}'" in workflow, f"{workflow_name} Java version drifted from toolchain lock")
    require(f"gradle-version: '{lock['gradle']['version']}'" in workflow, f"{workflow_name} Gradle version drifted from toolchain lock")
require(f"PyYAML=={lock['python_ci_yaml_parser']['version']}" in source_workflow, "Pinned PyYAML CI parser drifted from toolchain lock")
require(f'"platforms;android-{lock["android"]["compile_sdk"]}"' in android_workflow, "Android CI platform drifted from toolchain lock")
require(f'"build-tools;{lock["android"]["build_tools"]}"' in android_workflow, "Android Build Tools drifted from toolchain lock")
require(f'"ndk;{lock["android"]["ndk"]}"' in android_workflow, "Android CI NDK drifted from toolchain lock")
require(f'"cmake;{lock["android"]["cmake"]}"' in android_workflow, "Android CI CMake drifted from toolchain lock")

if errors:
    for error in errors:
        print(f"FAIL {error}", file=sys.stderr)
    raise SystemExit(2)
print("Toolchain lock consistency: OK")
