# P1 physical-device acceptance test

P1 is complete only when a **real stock ARM64 Android device** executes guest PID 1 under QEMU/TCG and the same acceptance run produces the exact terminal line:

```text
VA_P1_GUEST_OK: AArch64 Linux reached /init under QEMU TCG
```

The hardware PASS rule is intentionally strict: a fresh current-run marker plus `adb_smoke_p1.sh` validating `success=true`. Source checks, fake reports, host tests, emulators, or an old `latest-p1.json` are not hardware PASS.

## Before installing

Run:

```sh
./scripts/check_p1_assets.sh
```

All three categories must be `OK`: Linux `Image`, initramfs, and packaged QEMU runtime closure. Build/install a **debug APK** from Android Studio or Gradle. P1 does not require root, bootloader unlock, Termux, `/dev/kvm`, `/dev/tun`, Shizuku, or adb root.

The current APK must actually contain `p1/Image` and `p1/initramfs.cpio.gz`. P1 refreshes the app-private copies from the current APK on every attempt; a stale kernel left in `filesDir` from an older install cannot satisfy the gate.

## Preferred acceptance command

With one physical ARM64 phone attached:

```sh
./scripts/adb_smoke_p1.sh poco-p1
```

Use `ANDROID_SERIAL=<serial>` when more than one adb target exists. The runner:

1. confirms the target advertises `arm64-v8a` and rejects obvious emulator/QEMU Android targets;
2. requires `run-as`, so the installed build must be debuggable;
3. creates a unique P1 run ID and records device/build metadata;
4. **must** delete `files/reports/latest-p1.json` and aborts if stale-proof removal fails;
5. launches `MainActivity` with `auto_p1=true` and the same run ID;
6. waits for a new report;
7. validates format, run ID, start time, current APK install/update time, device build fingerprint, backend=`TCG`, hashes, exact terminal marker, and `success=true`.

Successful output creates:

```text
poco-p1.json
poco-p1.log
poco-p1.device.txt
```

The JSON report is app-generated proof; the `.device.txt` file is runner-side device context. Keep both with the log.

## Manual UI run

1. Open **Virtual Android Prototype**.
2. Confirm the capability probe reports executable generated AArch64 code (`execmem`) successfully.
3. Press **Start P1 guest**.
4. The app watchdog is 120 seconds.
5. The service generates a run ID, verifies current APK guest artifacts and the packaged QEMU runtime, then starts QEMU explicitly with TCG.

On an exact terminal marker the app records the marker, stops QEMU, and writes `files/reports/p1-*.json` plus `latest-p1.json`.

To collect a manual report:

```sh
./scripts/adb_collect_p1.sh poco-p1.json
```

This schema-validates the report but **does not by itself prove a current-run hardware PASS**. To additionally demand a successful report during collection:

```sh
REQUIRE_P1_SUCCESS=1 ./scripts/adb_collect_p1.sh poco-p1.json
```

For hardware acceptance use `adb_smoke_p1.sh`, because it binds the report to the runner's run ID and start timestamp.

## Proof fields

P1 proof format 3 records at least:

- `runId`, UTC start/finish times and epoch timestamps;
- `backend=TCG` and the exact terminal-marker contract;
- app package/version code/version name/install-update timestamps/debuggable state;
- device manufacturer/model/device/product, SDK, supported ABIs and Android build fingerprint;
- guest RAM/vCPU request plus kernel and initramfs SHA-256/byte counts;
- QEMU version, runtime-manifest SHA-256, QEMU binary SHA-256, runtime file/byte counts, preflight and guest launch paths, exit code;
- monotonic timing to preflight, first output, `Booting Linux`, and terminal marker;
- failure category, failure detail, last boot stage and diagnostic signals.

P2 only accepts a P1 proof from the currently installed app version and current host build. A report predating the APK's `lastUpdateTime` is rejected.

## Failure classification

The proof and `VA-P1` log distinguish these layers:

- `GUEST_ASSET`: current APK is missing/has an unusable kernel or initramfs.
- `RUNTIME_VERIFY` / `RUNTIME_MISSING`: QEMU manifest, hash, file closure, or packaged main binary problem.
- `RUNTIME_DEPENDENCY_OR_LINKER`: Android dynamic-linker/runtime dependency failure during `qemu --version`.
- `EXEC_START_FAILED` / `EXEC_OR_JIT_DENIED`: child execution policy or TCG/JIT executable-memory denial.
- `PREFLIGHT_*`: QEMU executable launched but its preflight output/exit was invalid.
- `QEMU_EARLY_EXIT_NO_OUTPUT`: QEMU died before useful output.
- `KERNEL_LOAD_ERROR` / `KERNEL_OR_MACHINE_EARLY_EXIT`: QEMU ran but the kernel/machine command did not reach Linux boot.
- `KERNEL_OR_INITRAMFS_PANIC` / `INITRAMFS_OR_INIT_*`: Linux started but `/init`/initramfs did not reach the terminal marker.
- `*_TIMEOUT`: watchdog expired; classification reflects how far boot progressed.

For live diagnostics:

```sh
adb logcat -s VA-P1:I '*:S'
```

Never advance project P1 to PASS unless the physical-device smoke runner itself validates the fresh proof.
