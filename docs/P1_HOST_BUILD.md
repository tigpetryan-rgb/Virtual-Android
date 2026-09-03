# P1 host build pipeline

The P1 acceptance test needs exactly three guest/host artifacts:

1. `app/src/main/assets/p1/initramfs.cpio.gz` — already generated from our raw-syscall AArch64 `/init`.
2. `app/src/main/assets/p1/Image` — generic ARM64 Linux kernel for QEMU `virt`.
3. `app/src/main/jniLibs/arm64-v8a/libqemu_system_aarch64.so` plus its Android/Bionic shared-library closure.

## A. Build the guest kernel

The default script is pinned to Linux **6.12.107 LTS**. It uses standard
`arm64 defconfig`, explicitly enables initramfs + PL011 console, disables KASLR
for reproducible bring-up, and copies the raw kernel `Image` into APK assets.

On a Linux build machine install the usual kernel build dependencies plus LLVM,
then run:

```sh
./scripts/build_p1_kernel.sh
```

Override with `LINUX_VERSION=x.y.z` only after P1 already passes on the pinned
baseline.

The script intentionally downloads source from kernel.org instead of shipping a
third-party prebuilt kernel whose configuration/provenance we do not control.

## B. Build QEMU through the Termux/Bionic oracle

Clone a current `termux-packages` checkout and install our narrow recipe:

```sh
./scripts/install_termux_qemu_recipe.sh /work/termux-packages
cd /work/termux-packages
./build-package.sh -a aarch64 va-qemu-p1
```

The recipe pins QEMU 10.2.1 and reuses Termux's maintained AArch64 Android
`setjmp` workaround. Unlike the full Termux desktop QEMU recipe, this prototype
requests only `aarch64-softmmu`, TCG, FDT, pixman, GLib and the minimum Android
compatibility libraries needed for headless P1.

This is a **build oracle**, not a runtime dependency on the Termux app. The final
APK must contain everything it needs and must run when Termux is not installed.

## C. Import only the runtime dependency closure

Point the importer at the built/extracted Termux prefix:

```sh
sudo apt-get install patchelf   # or equivalent on the build host
./scripts/import_termux_qemu.sh /path/to/termux-prefix
```

The importer:

- validates that QEMU is AArch64 and PIE/ET_DYN;
- recursively reads every `DT_NEEDED` edge;
- leaves Android system libraries out of the APK;
- copies the remaining Bionic libraries into `jniLibs/arm64-v8a`;
- rewrites versioned/non-jniLibs-safe SONAMEs with `patchelf` when necessary;
- removes build-machine RPATH/RUNPATH;
- writes `third_party/qemu/qemu-runtime-manifest.json` containing file hashes and dependency edges;
- verifies every packaged ELF has `PT_LOAD` alignment >= 16 KiB for Android 15+ 16 KiB-page hosts;
- fails if the closure is incomplete or a bundled native binary is only 4 KiB aligned.

P1 may still be relatively large. Size optimization happens only after the
boot-marker proof is stable.

## D. APK-side preflight

When the user presses **Start P1 guest**, `VmService` first executes:

```text
qemu --version
```

inside the isolated `:vm` process. If that fails, we never attempt to boot the
guest. This distinguishes Android linker/SELinux/runtime-dependency failures from
kernel/guest failures.

The supervisor logs whether the executable launched directly or through the
`/system/bin/linker64` fallback.

## E. Evidence file

Every P1 attempt writes an app-private report like:

```text
files/reports/p1-YYYYMMDD-HHMMSS.json
```

It contains device model/SDK, requested RAM/vCPUs, QEMU version, launch path,
exit code, failure reason, and monotonic milliseconds to:

- QEMU preflight completion;
- first guest/QEMU output;
- `Booting Linux`;
- `VA_P1_GUEST_OK`.

The serial marker plus this report is the P1 proof artifact.

See `docs/HOST_16K_PAGES.md` for the host-page-size invariant.

## F. Acceptance-proof integrity

The QEMU runtime manifest is now part of the physical-device proof chain. APK-side verification requires an ARM64 manifest, the expected QEMU main binary, unique runtime filenames, file size/hash matches, inclusion of the main binary, and `minimum_load_alignment >= 16384`. The successful P1 proof records both the runtime-manifest SHA-256 and the installed QEMU binary SHA-256.

The ADB acceptance runner uses `scripts/validate_p1_report.py` to reject stale/wrong run IDs, reports predating the current APK install/update, build-fingerprint mismatches, non-TCG backends, marker-only fakes, and successful reports without guest/QEMU hashes. Synthetic validator tests are source-level assurance only and never count as device PASS.
