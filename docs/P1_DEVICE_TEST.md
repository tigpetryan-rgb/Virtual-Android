# P1 physical-device acceptance test

P1 is complete only when a stock ARM64 Android device executes guest PID 1 under
QEMU/TCG and the app records `VA_P1_GUEST_OK`.

## Before installing

Run:

```sh
./scripts/check_p1_assets.sh
```

All three categories must be `OK`: Linux `Image`, initramfs, and packaged QEMU
runtime closure.

Build/install a **debug APK** from Android Studio or Gradle. P1 intentionally does
not require root, bootloader unlock, Termux, `/dev/kvm`, `/dev/tun`, or adb root.

## On the device

1. Open **Virtual Android Prototype**.
2. Confirm `execmem` says generated AArch64 code executed with result `42`.
3. Press **Start P1 guest**.
4. The acceptance watchdog is 120 seconds.
5. Success is the serial line:

```text
VA_P1_GUEST_OK: AArch64 Linux reached /init under QEMU TCG
```

On success the service marks P1 PASS, automatically stops QEMU, and persists the
report. This avoids requiring a manual Stop just to obtain evidence.

## Return the proof

The easiest no-adb path is **Copy latest P1 proof JSON** in the app and paste the
JSON into the development chat.

For a debug build with adb available, the entire P1 acceptance flow can be launched and collected with one command:

```sh
./scripts/adb_smoke_p1.sh poco-p1
```

The debug activity extra `auto_p1=true` is honored only when the installed APK is marked debuggable. The script clears the previous latest report, launches the app, waits for the new proof, validates `success=true`, and saves both `poco-p1.json` and the `VA-P1` logcat output.

To collect a report after a manual UI run instead:

```sh
./scripts/adb_collect_p1.sh poco-p1.json
```

This uses `run-as` (not root) to read only this app's latest private proof file.
The report contains host identity, QEMU version/launch path, exit status, guest
RAM/vCPU request and monotonic boot timings.

For live host/serial diagnostics you can also run:

```sh
adb logcat -s VA-P1:I '*:S'
```

## Failure classification

- `execmem` fails: TCG/JIT is not viable under this app/device policy.
- QEMU preflight fails: host linker, packaged dependency closure, or execution
  policy problem; guest kernel has not run yet.
- `Booting Linux` absent: QEMU machine/kernel loading problem.
- `Booting Linux` present but marker absent: kernel console/initramfs/init problem.
- watchdog timeout: save the proof plus `VA-P1` logcat; do not start AOSP P2 yet.
