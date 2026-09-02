# Guest bring-up plan

The first guest image should be custom-built for the emulated ARM64 `virt` machine.

## Stage A: Linux proof image

Use a tiny ARM64 kernel + initramfs with:
- PL011 console
- PCI host generic
- virtio-pci
- virtio-blk
- virtio-net
- devtmpfs

Target QEMU command shape during desktop development:

```bash
qemu-system-aarch64 \
  -machine virt \
  -cpu max \
  -accel tcg \
  -m 1024 \
  -smp 2 \
  -kernel Image \
  -initrd initramfs.cpio.gz \
  -append 'console=ttyAMA0' \
  -device virtio-net-pci,netdev=n0 \
  -netdev user,id=n0 \
  -nographic
```

Once this boots through the Android-host native backend, move to AOSP.

## Stage B: AOSP headless

Create a device/product definition whose vendor side matches the virtual devices exposed by the VMM. Avoid using a vendor phone ROM.

Initial HAL scope:
- graphics composer: stub/headless first
- audio: stub
- power: stub
- health: virtual implementation
- keymaster/keystore: software development implementation only for prototype
- sensors/camera/location: absent or stubbed

Then add virtio-gpu and interactive services incrementally.
