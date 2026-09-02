# QEMU Android/Bionic host boundary — P1/P2/P3

The prototype packages QEMU and its non-system Bionic dependency closure under:

```text
app/src/main/jniLibs/arm64-v8a/
```

The executable is deliberately installed as `libqemu_system_aarch64.so`. It is
an AArch64 PIE executable, not JNI; the native-library packaging path gives it
an Android-owned executable-code location.

## Baseline

The prototype pins QEMU 10.2.1 and uses Termux as the Android/Bionic build
oracle. The local minimal recipe retains only AArch64 system emulation + TCG,
libslirp networking, pixman and the VNC display backend needed by P3.

P3 requires VNC. Do not reintroduce a bare `--disable-vnc`: app preflight calls
`qemu -display help` and refuses interactive launch when VNC is absent.

The P3 server binds only to loopback. TLS/SASL/JPEG are intentionally disabled
in the minimal recipe because no remote VNC exposure is part of the design.

## Runtime import

```sh
./scripts/import_termux_qemu.sh /path/to/termux-prefix
./scripts/check_p1_assets.sh
```

The importer recursively copies `DT_NEEDED` libraries, omits Android system
libraries, removes RPATH/RUNPATH, rewrites versioned SONAMEs when necessary,
checks 16 KiB `PT_LOAD` compatibility and emits SHA-256 runtime evidence.

## Future tightening

RFB Raw is a P3 proof/debug transport, not the final rendering architecture.
Once a physical phone passes P3, replace it with a lower-copy app-private
transport while keeping the guest's virtio-gpu contract stable.

For distribution, QEMU GPLv2 obligations and every bundled dependency's license,
source offer/availability and SBOM need explicit handling.
