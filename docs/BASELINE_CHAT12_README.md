# Virtual Android Prototype v0.7

Goal: run an isolated ARM64 Android guest from a normal ARM64 Android APK without
root, bootloader unlock, Shizuku, or direct `/dev/kvm` access.

The universal backend is **QEMU/TCG in user space**. AVF/pKVM remains a separate
optional acceleration backend only where the platform actually grants access.

## P0 — capability layer

- ARM64 / CPU / RAM probe
- AVF feature + permission detection
- `/dev/kvm`, `/dev/tun`, `/dev/vhost-vsock` probes
- real AArch64 JIT/execmem smoke test
- host page-size reporting and 16 KiB native compatibility checks
- backend selection

## P1 — Linux proof guest

- QEMU host in dedicated Android `:vm` process
- AIDL control/callback interface
- deterministic ARM `virt` + TCG launch
- packaged QEMU runtime dependency/hash verification
- tiny no-libc ARM64 `/init` + deterministic 565-byte initramfs
- 120-second watchdog and auto-stop
- acceptance marker `VA_P1_GUEST_OK`
- `latest-p1.json` proof and ADB smoke runner

P1 is **not** declared PASS in this repository until a real stock ARM64 phone
returns `success=true` from `scripts/adb_smoke_p1.sh`.

## P2 — full AOSP framework proof

P2 reuses AOSP's upstream `device/generic/goldfish/fvpbase` QEMU target and adds
only a small `va_fvp` overlay for deterministic acceptance markers.

P2 consumes:

```text
kernel
combined-ramdisk.img
system-qemu.img
userdata.img
SHA256SUMS
```

v0.7 now reports deterministic stages:

```text
LINUX
ANDROID_INIT
SERVICEMANAGER
ZYGOTE
FRAMEWORK_READY
```

P2 is PASS only at `VA_P2_FRAMEWORK_OK`, emitted on guest
`sys.boot_completed=1`. P2 remains blocked until latest P1 is PASS.

## P3 — interactive guest display/input

P3 is blocked until latest P2 is PASS. It keeps QEMU in `:vm`, adds upstream-like
USB input plus an absolute `usb-tablet`, and exposes QEMU's VNC server only on
`127.0.0.1:5901`.

The main APK process contains a dependency-free RFB 3.8 client that:

- negotiates no-auth loopback RFB,
- requests 32-bit true-colour Raw framebuffer updates,
- renders them into an Android `SurfaceView`,
- maps phone touch to RFB absolute pointer events,
- maps basic Android keys to RFB key events.

P3 DISPLAY is recorded PASS only when the **same P3 run** reaches both:

1. `VA_P2_FRAMEWORK_OK`, and
2. a non-empty RFB framebuffer.

The result is `files/reports/latest-p3-display.json`.

End-to-end guest touch semantics are wired but are **not yet declared physical
PASS**; v0.7 host-tests the actual RFB pointer packet path.

## Main build/test commands

P1 host pipeline:

```bash
./scripts/build_all_p1.sh
```

P2 AOSP checkout/build/export (large Linux host):

```bash
./scripts/init_p2_aosp_checkout.sh /work/aosp-p2
AOSP_ROOT=/work/aosp-p2 ./scripts/build_p2_aosp.sh
./scripts/export_p2_bundle.sh /work/aosp-p2/out/target/product/fvpbase
```

Physical debug device:

```bash
./scripts/adb_seed_p2.sh ./out/p2-bundle
./scripts/adb_smoke_p1.sh my-phone
./scripts/adb_smoke_p2.sh my-phone
./scripts/adb_smoke_p3.sh my-phone
```

See `BUILD_STATUS.md`, `docs/P2_BOOT_DIAGNOSTICS.md`, and
`docs/P3_DISPLAY_INPUT.md`.

## Reproducible CI and release metadata

Task 12 adds a central `config/toolchain.lock.json`, deterministic `FILELIST.txt` /
`release/release-manifest.json` generation, stale acceptance-proof rejection, and
byte-reproducible source ZIP packaging. See `docs/REPRODUCIBLE_BUILDS.md`.

The checked-in release manifest intentionally keeps hardware/device acceptance
gates at `NOT_VERIFIED`; hosted CI or a copied proof file never counts as a
physical-device PASS.
