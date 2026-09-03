# P1 Bring-up: stock Android APK -> QEMU/TCG -> ARM64 Linux PID 1

## Definition of done

P1 is complete only when a non-root, targetSdk 36 APK installed on a normal ARM64
phone produces the following line from the guest serial console:

```text
VA_P1_GUEST_OK: AArch64 Linux reached /init under QEMU TCG
```

No `/dev/kvm`, root, bootloader unlock, Shizuku, privileged permission, or shell
helper is part of this acceptance test.

## Host process topology

```text
com.example.virtualandroid       UI / capability probe
        |
        | Binder (AIDL)
        v
com.example.virtualandroid:vm    VM supervisor
        |
        +-- child QEMU PIE process (preferred P1 packaging path)
             |
             +-- TCG translated ARM64 guest CPU
```

The VM supervisor and UI have the same Android app UID but separate Linux
processes. A QEMU crash should therefore not kill the Activity process.

## P1 QEMU command

The app constructs the equivalent of:

```sh
qemu-system-aarch64 \
  -machine virt,gic-version=3 \
  -cpu max \
  -accel tcg,thread=multi \
  -smp 2 \
  -m 1024 \
  -kernel /data/user/0/<pkg>/files/guest/p1/Image \
  -initrd /data/user/0/<pkg>/files/guest/p1/initramfs.cpio.gz \
  -append 'console=ttyAMA0 earlycon=pl011,0x09000000 rdinit=/init panic=-1' \
  -display none \
  -serial stdio \
  -monitor none \
  -no-reboot \
  -no-shutdown
```

`-cpu max` is deliberate: the QEMU `virt` machine defaults to a 32-bit CPU, so
an AArch64 guest must select a 64-bit CPU model explicitly.

## Host executable packaging strategy

P1 packages the QEMU PIE under Android's native library tree as:

```text
lib/arm64-v8a/libqemu_system_aarch64.so
```

With legacy native packaging enabled, PackageManager extracts it to the app's
`nativeLibraryDir`. The supervisor first attempts direct execution. If that
fails at `execve`, it retries through `/system/bin/linker64`.

This second path is intentionally treated as a compatibility experiment, not an
API contract. OEM SELinux/linker behavior can differ. If a meaningful fraction
of devices reject both paths, the next backend packaging option is:

```text
:vm Android process
   -> System.loadLibrary("vaqemu")
   -> JNI qemu_main(argc, argv)
```

That avoids child-executable policy entirely, at the cost of carrying a small
QEMU embedding patch and allowing QEMU's `exit()` semantics to terminate the
`:vm` process.

## Guest boot artifact strategy

P1 does not use BusyBox or Android userspace. `/init` is a 1.2 KiB statically
linked AArch64 ELF with raw syscalls. This removes libc, dynamic linker and root
filesystem variables from the first test.

The initramfs contains only:

```text
/init
TRAILER!!!
```

The guest kernel must support PL011 console and gzip initramfs.

## Failure matrix

| Symptom | Likely layer | Next check |
|---|---|---|
| `Missing packaged QEMU` | APK packaging | inspect APK `lib/arm64-v8a` |
| `EACCES` on QEMU direct launch | host SELinux/exec policy | linker64 retry / logcat avc |
| linker reports missing `.so` | QEMU runtime deps | `readelf -d`, package dependency libs |
| QEMU says machine/cpu unknown | over-trimmed QEMU build | ARM virt/Kconfig features |
| no serial output | QEMU args/kernel | PL011 + `console=ttyAMA0` |
| kernel panic: no init | initramfs | gzip/newc + executable `/init` |
| boot marker appears | **P1 pass** | record wall time/RSS/CPU |

## Metrics to capture on first pass

- time from Start tap to first QEMU line
- time to Linux `Booting Linux` line
- time to `VA_P1_GUEST_OK`
- host process PSS/RSS
- peak host CPU utilization
- guest RAM allocation
- temperature / thermal throttling after 60 s
- whether direct exec or linker64 path was used

These numbers determine whether TCG can be a practical universal fallback or
only a compatibility mode while accelerated backends do the real work.

## v0.3 preflight and proof artifact

Before the guest command is launched, the `:vm` supervisor runs packaged QEMU
with `--version`. A failure here is classified as a host runtime/linker failure,
not a guest failure. Successful preflight records the QEMU version and whether
Android used the direct or explicit `linker64` path.

Each attempt writes `files/reports/p1-*.json` with monotonic timing points and
the final result. This is the evidence file to collect together with serial logs
on physical-device tests.

The build/import instructions are now in `docs/P1_HOST_BUILD.md`.

## Current-run proof hardening (task 01)

P1 proof format 3 is bound to one execution with a `runId`. The debug ADB runner generates that ID, passes it through the Activity/Binder boundary, and rejects any report whose ID or start timestamp does not match the current invocation. Manual UI runs receive a service-generated ID.

The report additionally records the current APK version/install timestamp, host Android build fingerprint, kernel/initramfs hashes, QEMU binary/runtime-manifest hashes, backend=`TCG`, and the exact terminal marker. `P1ReportStore.latestPassed()` rejects legacy reports, reports from another host build, and reports that predate the current APK update.

Guest boot assets are recopied from the current APK on every P1 attempt. This deliberately prevents a missing new APK asset from being masked by an old `filesDir/guest/p1/Image` left by an earlier install.

QEMU launcher failures now carry explicit categories for runtime integrity, dynamic linker/dependency problems, direct/linker64 execution denial, timeout, and malformed preflight output. Guest/QEMU serial text is also scanned for JIT/exec denial, kernel-load errors and kernel/initramfs panic signals so early exit and watchdog failures identify the most likely layer.
