# Virtual Android P2 AOSP overlay

P2 does **not** invent a new virtual board anymore. It layers a tiny product on
AOSP's existing `device/generic/goldfish/fvpbase` target, which upstream AOSP
already supports on QEMU's ARM `virt` machine.

The overlay registers `va_fvp-{eng,userdebug}` and adds only deterministic
serial markers:

- `VA_P2_ANDROID_INIT_OK` after `/data` preparation begins
- `VA_P2_FRAMEWORK_OK` when `sys.boot_completed=1`

The actual hardware/fstab/init/VINTF contract remains upstream `fvpbase`.
