# P1 guest: ARM64 Linux -> raw `/init`

P1 deliberately avoids Android userspace. Its only job is to prove that a stock
Android APK can drive QEMU/TCG far enough to execute guest AArch64 PID 1.

## initramfs

`init.S` uses raw AArch64 Linux syscalls, writes the proof marker to stdout, and
then sleeps forever. It has no libc or dynamic-linker dependency.

Rebuild it with:

```sh
export ANDROID_NDK_HOME=/path/to/android-ndk
./scripts/build_p1_initramfs.sh
```

Expected marker:

```text
VA_P1_GUEST_OK: AArch64 Linux reached /init under QEMU TCG
```

## kernel

Use the repository build script:

```sh
./scripts/build_p1_kernel.sh
```

The default baseline is Linux 6.12.107 LTS with ARM64 `defconfig` plus explicit
initramfs and PL011 console requirements. The resulting raw image is copied to:

```text
app/src/main/assets/p1/Image
```

P1 boots it directly with QEMU `-kernel`; no UEFI/bootloader is involved.
