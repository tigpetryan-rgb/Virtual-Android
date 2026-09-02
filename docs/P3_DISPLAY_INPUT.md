# P3 — interactive display/input bridge

## Goal

P3 keeps QEMU/TCG in the isolated Android `:vm` process while presenting the
AOSP guest in the normal app process. No root, bootloader unlock, host Surface
privileges, `/dev/kvm`, or `/dev/tun` are required.

## Display topology

```text
AOSP SurfaceFlinger
       |
       v
virtio-gpu-pci
       |
       v
QEMU display core (:vm)
       |
       v
RFB/VNC 3.8, 127.0.0.1:5901 only
       |
       v
GuestDisplayView (main APK process)
       |
       v
Android SurfaceView
```

The first implementation requests only RFB Raw encoding and a fixed
32-bit little-endian true-colour pixel format. That is intentionally simple and
bandwidth-heavy: it is an acceptance/debug bridge, not the final renderer.

## Input topology

P3 tracks AOSP `fvpbase/run_qemu`'s USB input model but uses `usb-tablet` for an
absolute pointer, which maps a phone touchscreen more naturally than a relative
mouse.

```text
MotionEvent -> RFB PointerEvent -> QEMU -> usb-tablet -> Linux evdev -> Android InputReader
KeyEvent    -> RFB KeyEvent     -> QEMU -> usb-kbd    -> Linux evdev -> Android InputReader
```

## Security boundary

The VNC listener binds only to `127.0.0.1:5901`. It is never intentionally
exposed to Wi-Fi/mobile interfaces. The P3 preflight also refuses to launch if
the packaged QEMU does not advertise VNC support.

This no-auth loopback transport is acceptable only for the prototype. A later
hardening step should replace TCP VNC with an app-private Unix-domain display
channel or a purpose-built shared-memory transport.

## P3 acceptance

A framebuffer alone is not enough because a boot logo can produce a frame.
`latest-p3-display.json` is written only after both events occur in the current
P3 run:

1. serial reaches `VA_P2_FRAMEWORK_OK` (`sys.boot_completed=1`), and
2. the APK receives a non-empty RFB framebuffer.

Run on a physical device after P2 PASS:

```bash
./scripts/adb_smoke_p3.sh my-phone
```

v0.7 proves the display transport automatically. Pointer/key packet correctness
is protocol-tested on the host, but end-to-end guest touch semantics still need
a physical-device/manual interaction acceptance before P3 INPUT is declared
PASS.

## Performance direction after acceptance

Raw RFB deliberately copies pixels several times and should not be the final
product path. Once P3 works on real devices, profile in this order:

1. RFB Tight/ZRLE only if it meaningfully reduces CPU+bandwidth cost.
2. QEMU display listener / shared-memory bridge inside the app UID.
3. `AHardwareBuffer`/native buffer import where Android's public APIs permit it.
4. Optional accelerated guest rendering backend on platform configurations that
   expose a usable virtualization/GPU path.
