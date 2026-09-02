# P2 acceptance contract

P2 has two serial milestones:

1. `VA_P2_ANDROID_INIT_OK` — Android init progressed into post-fs-data.
2. `VA_P2_FRAMEWORK_OK` — guest property `sys.boot_completed=1` fired.

Only milestone 2 is a P2 PASS. The APK writes `files/reports/latest-p2.json` with
QEMU runtime identity, guest bundle SHA-256 values, timing, and failure reason.

P2 uses AOSP's QEMU hardware identity (`androidboot.hardware=qemu`) and upstream
`fvpbase` fstab/VINTF contract. Do not change it to `virtualandroid` unless we
intentionally fork the device tree later.
