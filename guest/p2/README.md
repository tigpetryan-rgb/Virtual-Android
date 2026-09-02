# P2 — AOSP framework guest on QEMU virt

P2 is gated on a physical P1 PASS. The important architecture change in v0.6
is that P2 now **reuses AOSP's upstream `device/generic/goldfish/fvpbase` QEMU
contract** instead of inventing `device/virtualandroid/virt_arm64` from scratch.

AOSP already provides the ARM64 board config, GKI/virtual-device modules,
`fstab.qemu`, `init.qemu.rc`, VINTF declarations, dynamic-partition QEMU image,
and a QEMU `virt` runner. Our overlay only adds deterministic acceptance markers.

## Acceptance chain

```text
QEMU/TCG
 -> Linux/GKI
 -> Android first-stage init
 -> mount system/vendor from system-qemu.img
 -> post-fs-data
 -> zygote / system_server / framework
 -> sys.boot_completed=1
 -> VA_P2_FRAMEWORK_OK
```

Markers emitted by `init.virtualandroid.rc`:

- `VA_P2_ANDROID_INIT_OK`
- `VA_P2_FRAMEWORK_OK`

Only the second marker is P2 PASS.

## Build output consumed by the APK

- `kernel`
- `combined-ramdisk.img`
- `system-qemu.img`
- `userdata.img`
- `SHA256SUMS`

The large guest images are not bundled in the APK. For debug bring-up use
`scripts/export_p2_bundle.sh` followed by `scripts/adb_seed_p2.sh`.

See `docs/P2_AOSP_BUILD.md` and `docs/P2_ACCEPTANCE.md`.
