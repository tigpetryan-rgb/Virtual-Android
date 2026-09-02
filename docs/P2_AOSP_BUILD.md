# P2 AOSP build path

## Baseline

P2 is based on upstream AOSP `device/generic/goldfish/fvpbase`, not a new board
implementation. AOSP already supplies a QEMU ARM `virt` launch path, QEMU fstab,
init rules, VINTF, dynamic-partition image creation, and virtual-device kernel
prebuilts.

The project overlay only registers `va_fvp-{eng,userdebug}` and emits deterministic
serial markers at Android-init and framework completion.

## Build

A stable initial source baseline is `android-16.0.0_r2`.

```bash
repo init -u https://android.googlesource.com/platform/manifest -b android-16.0.0_r2
repo sync -c -j8
AOSP_ROOT=$PWD /path/to/project/scripts/build_p2_aosp.sh
/path/to/project/scripts/export_p2_bundle.sh "$ANDROID_PRODUCT_OUT"
```

`build_p2_aosp.sh` sets `FVP_MULTILIB_BUILD=false` and builds `va_fvp-eng` by
default. Set `P2_VARIANT=userdebug` for the stricter variant.

## Required output contract

- `kernel`
- `combined-ramdisk.img`
- `system-qemu.img`
- `userdata.img`
- generated `SHA256SUMS` in the exported bundle

The APK does not place multi-gigabyte AOSP images in its APK. Debug bring-up uses
`adb_seed_p2.sh` to stream them into app-private storage. Production delivery must
use a signed/downloadable guest bundle with resumable storage and versioning.
