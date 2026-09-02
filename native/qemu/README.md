# QEMU/TCG integration boundary

Do not wire the UI directly to QEMU internals. Preserve a narrow `VmEngine` interface.

Two packaging strategies are possible:

1. **In-process native library**
   - lowest IPC overhead
   - more invasive QEMU refactoring
   - crash takes down the VM host process
   - licensing boundary is tighter

2. **Packaged native executable in a dedicated app process**
   - better crash isolation
   - easier to keep upstream QEMU close to stock
   - Android 10+ forbids executing binaries copied into writable app-home paths, so executable code must be packaged/loaded using Android-compatible read-only executable locations rather than downloaded/copied code.

For the first real engine bring-up, prefer a dedicated `android:process=":vm"` service and a native backend controlled over Binder/Unix sockets.

Required QEMU feature subset:
- target: aarch64-softmmu only
- TCG
- `virt` machine
- virtio-blk
- virtio-net + user networking
- serial/chardev
- later: virtio-gpu, virtio-input

Disable everything else initially to reduce binary size and attack surface.
