# P2 architecture — upstream AOSP fvpbase on APK-owned QEMU/TCG

P2 proves a real Android framework can boot inside the same unprivileged
APK-owned QEMU boundary as P1.

## Upstream baseline

Rather than maintaining a speculative custom board, P2 inherits AOSP's
`device/generic/goldfish/fvpbase/fvp.mk`. That target already supports QEMU's ARM
`virt` machine and supplies the board/kernel/fstab/init/VINTF pieces required by
Android.

The local `va_fvp` product overlay intentionally stays tiny: product identity and
two serial acceptance markers. This lets us track upstream virtual-device fixes.

## Runtime artifact contract

```text
files/p2/
  kernel
  combined-ramdisk.img
  system-qemu.img      # dynamic system/vendor image generated for QEMU
  userdata.img
  SHA256SUMS
```

All hashes are checked before QEMU starts. The system image is attached read-only;
userdata is persistent and writable.

## QEMU topology

- ARM `virt,mte=on`
- `-cpu max`
- TCG multi-threaded acceleration
- virtio-blk system + userdata
- virtio-net PCI with QEMU/libslirp user-mode NAT
- virtio GPU retained for the full AOSP framework product, but host presentation
  is disabled (`-display none`) during P2
- PL011 serial console
- no root, KVM, TUN/TAP, or host filesystem mount

P2 retains upstream `androidboot.hardware=qemu` and its upstream boot-device
identifier. Changing this would select different init/fstab behavior and is not a
cosmetic rename.

## Acceptance

The local product adds an init rc that writes deterministic markers to `/dev/kmsg`:

1. `VA_P2_ANDROID_INIT_OK` during `post-fs-data`
2. `VA_P2_FRAMEWORK_OK` when `sys.boot_completed=1`

The second marker causes the APK runner to record a PASS report and stop QEMU.
