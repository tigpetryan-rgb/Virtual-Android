# Changelog

## 0.4.0

- Replaced the executable-memory capability check with an actual AArch64 JIT
  smoke test (`BTI c; mov w0,#42; ret`) after RW -> RX transition.
- Added host runtime page-size reporting.
- Added explicit 16 KiB ELF alignment for `libvaprobes.so` and the P1 QEMU
  build recipe.
- Added 16 KiB `PT_LOAD` validation for every imported QEMU runtime ELF.
- Added a 120-second P1 watchdog.
- P1 now auto-stops QEMU after `VA_P1_GUEST_OK` so success evidence is always
  persisted without a manual stop.
- Added `reports/latest-p1.json` plus a UI action to copy the latest proof.
- Mirrored P1 VM logs to Android logcat tag `VA-P1`.
- Added `adb_collect_p1.sh` and one-command debug acceptance runner
  `adb_smoke_p1.sh`.
- Added physical-device and 16 KiB host compatibility documentation.

## 0.3.0

- Added reproducible P1 guest-kernel build script.
- Added Termux/Bionic QEMU build-oracle recipe and dependency-closure importer.
- Added QEMU host preflight and JSON timing report.

## v0.5.0
- Added SHA-256 verification of every imported QEMU runtime ELF before execution.
- Runtime manifest is now packaged as an APK asset and its digest is recorded in P1 proof JSON.
- Hardened VM start/stop lifecycle with generation-based cancellation to avoid stale PREPARING/STARTING races.
- Pinned Linux 6.12.107 source tarball SHA-256 in the kernel build pipeline.
- Added one-command Termux Docker QEMU build helper.
- Added GitHub Actions source checks and reproducible P1 kernel artifact workflow.
- Added non-UI P2 headless AOSP QEMU argument skeleton and guest architecture contract.
- Pinned Android NDK r28c/CMake toolchain and added Android compile CI.
- Added `build_all_p1.sh` to orchestrate initramfs -> kernel -> Termux QEMU -> runtime import -> APK.
- Added build matrix documentation separating skeleton compile, host artifacts, and physical-device acceptance.
- Disabled AGP native stripping for packaged `.so` files so imported QEMU SHA-256 values survive APK packaging unchanged.
- Enabled libslirp in the Android/Bionic QEMU runtime so the same host binary can support P2 unprivileged user-mode NAT without `/dev/tun`.

## v0.6.0
- Replaced the speculative custom P2 board contract with AOSP's upstream `device/generic/goldfish/fvpbase` QEMU target.
- Added `va_fvp-{eng,userdebug}` product overlay with deterministic Android-init and framework-ready serial markers.
- Aligned APK P2 QEMU arguments to upstream `run_qemu`: `virt,mte=on`, `androidboot.hardware=qemu`, `system-qemu.img`, userdata, virtio network/GPU and localhost ADB forwarding.
- Added reproducible AOSP checkout/build/export scripts for P2.
- Added SHA-256 verified external P2 guest bundle and debug ADB seeding into app-private storage.
- Added P2 Binder/UI launch path, hard P1-PASS gate, 30-minute guest watchdog, auto-stop on `sys.boot_completed=1`, and `latest-p2.json` evidence.
- Added one-command `adb_smoke_p2.sh` acceptance capture.
- Added host-side P2 QEMU-argument compile smoke and deliberate bundle-corruption test.

## v0.7.0
- Added deterministic P2 boot-stage diagnostics for Linux, Android init,
  servicemanager, zygote and framework-ready milestones.
- Upgraded P2 proof JSON to format 2 with `lastStage` and per-stage timings.
- Added interactive P3 QEMU topology with upstream-like xHCI/USB keyboard and
  absolute `usb-tablet` input.
- Added loopback-only QEMU VNC presentation at `127.0.0.1:5901`.
- Added dependency-free RFB 3.8 Raw framebuffer client and Android SurfaceView
  renderer with touch/basic keyboard forwarding.
- Added P3 hard gate on previous P2 PASS and a runtime QEMU VNC capability
  preflight.
- Fixed the minimal Android/Bionic QEMU recipe, which previously disabled VNC;
  it now builds the VNC backend explicitly while leaving TLS/SASL/JPEG extras
  disabled for the prototype.
- Added P3 display proof requiring both current-run framework-ready and current-
  run framebuffer evidence.
- Added `adb_smoke_p3.sh`, P3 display/input documentation, and P2 boot diagnostic
  documentation.
- Added host fake-VNC integration coverage for RFB negotiation, raw pixel decode,
  and pointer event transmission.

## Task 12 — CI/reproducibility hardening

- Added a machine-readable central toolchain lock for CI Linux family, Java, Gradle, AGP, Kotlin, Android SDK/NDK/CMake, Linux, AOSP, QEMU and the exact Termux packages commit.
- Reworked Termux/QEMU build setup to check out the pinned commit instead of building an arbitrary future `master`.
- Added deterministic source inventory and release manifest generation plus byte-reproducible ZIP packaging.
- Added stale physical-acceptance PASS proof rejection and strict VERIFIED / NOT VERIFIED status validation.
- Added dedicated CI stages for source syntax/reproducibility, pure Kotlin/JVM, fake RFB, QEMU closure, APK compile and native 16 KiB ELF verification, plus an explicit optional self-hosted device smoke workflow.
- Hardened QEMU runtime manifest verification so the recorded file set, sizes, SHA-256 values, `DT_NEEDED` closure and PT_LOAD alignments must match the packaged runtime.
