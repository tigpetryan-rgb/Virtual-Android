# Build status — v0.7 + G1 reproducibility + G2/CHAT-01 acceptance hardening

## VERIFIED

- ✅ Deterministic P1 initramfs packaging from the checked-in `guest/p1/out/init` binary reproduces the checked-in `initramfs.cpio.gz` byte for byte; host compiler output is not treated as reproducible unless that compiler is separately pinned and verified.
- ✅ Project architecture/source invariants pass locally/CI when recorded by the canonical workflows.
- ✅ Shell syntax, Python AST syntax, Android/guest XML parsing and GitHub Actions YAML parsing are part of canonical source checks.
- ✅ Central `config/toolchain.lock.json` governs project-owned Android, Linux, AOSP, QEMU and Termux build inputs.
- ✅ Pure Kotlin/JVM P2/P3 topology smoke and fake-server RFB 3.8 integration smoke are canonical regressions.
- ✅ Release tooling tests enforce byte-identical repeated source packaging and stale PASS-proof rejection.
- ✅ `FILELIST.txt` and `release/release-manifest.json` are deterministic generated metadata and must match the current source snapshot.
- ✅ CHAT-01 P1 acceptance contract is hardened to proof format 3 with current-run `runId`, APK/update timestamp, host build fingerprint, backend=`TCG`, exact terminal marker, guest artifact hashes, QEMU/runtime hashes, launch paths and classified diagnostics.
- ✅ `adb_smoke_p1.sh` rejects obvious emulator targets, requires stale-proof deletion, passes a unique run ID into the app, and validates only a fresh same-run proof.
- ✅ Synthetic P1 validator tests cover valid current-run proof, wrong run ID, stale timestamp, previous-APK proof, fake marker rejection and schema-valid failure reports. These tests are not hardware PASS.
- ✅ P1 guest assets are refreshed from the current APK on every attempt, so stale app-private artifacts cannot satisfy a new build.
- ✅ `BUILD_STATUS.md` is machine-checked to remain strictly split into VERIFIED / NOT VERIFIED.

## NOT VERIFIED

- ⚠️ G2 physical ARM64 device PASS is NOT VERIFIED until a real stock ARM64 Android device produces a fresh same-run exact `VA_P1_GUEST_OK: AArch64 Linux reached /init under QEMU TCG` proof and `adb_smoke_p1.sh` validates `success=true`.
- ⚠️ The canonical source snapshot still requires a provisioned `app/src/main/assets/p1/Image` and Android/Bionic QEMU runtime before the physical P1 run can succeed; source integration alone is not device acceptance.
- ⚠️ Android SDK/NDK/Gradle APK compilation is only VERIFIED when a current canonical CI/device-preparation run records `:app:assembleDebug` success.
- ⚠️ Packaged QEMU/Bionic execution, dependency closure and JIT/exec behavior are NOT VERIFIED on physical Android until the runtime is imported and exercised there.
- ⚠️ The pinned Linux kernel source is only VERIFIED as a built P1 `Image` when the pinned kernel workflow/build records the artifact and hash.
- ⚠️ AOSP Android 16 guest framework acceptance remains G3 and is not part of G2 PASS.
- ⚠️ P2 physical-device PASS is NOT VERIFIED without current-run framework readiness / `VA_P2_FRAMEWORK_OK` and `adb_smoke_p2.sh` reporting `success=true`.
- ⚠️ P3 DISPLAY physical PASS is NOT VERIFIED without current-run framework readiness plus a current-run non-empty RFB framebuffer.
- ❌ P3 INPUT physical PASS remains NOT VERIFIED; deterministic host input -> guest acknowledgement is still required in the same run.
- ⚠️ Optional device-farm smoke is NOT VERIFIED until the manual self-hosted workflow is actually executed against a provisioned physical-device APK.
