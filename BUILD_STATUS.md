# Build status — v0.7 + Task 12 CI/reproducibility hardening

## VERIFIED

- ✅ Deterministic P1 initramfs rebuild matches the checked-in artifact byte for byte.
- ✅ Project architecture/source invariants pass locally.
- ✅ Shell syntax, Python AST syntax, Android/guest XML parsing and GitHub Actions YAML parsing pass locally.
- ✅ Central `config/toolchain.lock.json` is consistent with the project-owned Android, Linux, AOSP, QEMU and Termux build inputs.
- ✅ Pure Kotlin/JVM P2/P3 topology smoke passes locally.
- ✅ Fake-server RFB 3.8 integration smoke passes locally.
- ✅ Release tooling unit tests pass, including byte-identical repeated ZIP packaging and stale PASS-proof rejection.
- ✅ `FILELIST.txt` and `release/release-manifest.json` regenerate deterministically from the current source snapshot.
- ✅ `BUILD_STATUS.md` is machine-checked to remain strictly split into VERIFIED / NOT VERIFIED.

## NOT VERIFIED

- ⚠️ Android SDK/NDK/Gradle APK compilation is not verified in this local workspace unless an actual `:app:assembleDebug` run is recorded separately.
- ⚠️ The real Android/Bionic QEMU runtime is not present in this supplied baseline, so QEMU `DT_NEEDED`/SHA-256/16 KiB closure is NOT VERIFIED here; CI runs the verifier automatically when the runtime exists.
- ⚠️ The pinned Linux kernel source was not downloaded/rebuilt in this local workspace unless recorded separately; the build helper enforces Linux 6.12.107 and its SHA-256.
- ⚠️ AOSP Android 16 was not synced/built in this local workspace; the checkout helper defaults to the pinned `android-16.0.0_r2` tag.
- ⚠️ Docker/Termux QEMU build is NOT VERIFIED locally; the builder now checks out the exact locked `termux-packages` commit before building QEMU 10.2.1.
- ⚠️ P1 physical-device PASS is NOT VERIFIED without a fresh same-run `VA_P1_GUEST_OK` proof and `adb_smoke_p1.sh` reporting `success=true`.
- ⚠️ P2 physical-device PASS is NOT VERIFIED without current-run framework readiness / `VA_P2_FRAMEWORK_OK` and `adb_smoke_p2.sh` reporting `success=true`.
- ⚠️ P3 DISPLAY physical PASS is NOT VERIFIED without current-run framework readiness plus a current-run non-empty RFB framebuffer.
- ❌ P3 INPUT physical PASS remains NOT VERIFIED; deterministic host input -> guest acknowledgement is still required in the same run.
- ⚠️ Optional device-farm smoke is NOT VERIFIED until the manual self-hosted workflow is actually executed against a provisioned physical-device APK.
