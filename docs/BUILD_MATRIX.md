# Build matrix

The prototype now separates artifacts so failures are attributable.

| Artifact | Host needed | Output | Acceptance |
|---|---|---|---|
| P1 initramfs | ordinary Linux + clang/lld | `app/src/main/assets/p1/initramfs.cpio.gz` | deterministic 565-byte archive with AArch64 static `/init` |
| P1 kernel | Linux + LLVM kernel tools | `app/src/main/assets/p1/Image` | pinned Linux 6.12.107 ARM64 Image |
| QEMU host | Docker + Termux packages build environment | `va-qemu-p1*.deb` | AArch64 Android/Bionic PIE, TCG-only, 16K PT_LOAD |
| QEMU APK runtime | Python + readelf + patchelf | `jniLibs/arm64-v8a/*.so` + asset manifest | recursive DT_NEEDED closure + SHA-256 + 16K alignment |
| APK skeleton | Android SDK 36 + NDK r28c + CMake 3.22.1 + Gradle 8.10.2 | `app-debug.apk` | compiles even before guest artifacts exist |
| P1 full APK | all above | `app-debug.apk` | `check_p1_assets.sh` passes before assembly |
| Physical P1 | stock ARM64 Android | proof JSON | `VA_P1_GUEST_OK`, `success=true` |

`./scripts/build_all_p1.sh` orchestrates the full host pipeline when all build prerequisites and network access are available.
