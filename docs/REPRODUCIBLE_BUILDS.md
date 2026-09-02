# Reproducible build and release contract

Task 12 centralizes the versions controlled by this project in
`config/toolchain.lock.json`. The lock is machine-readable and CI checks that the
Android Gradle files, P1 Linux builder, P2 AOSP checkout helper, QEMU recipe and
Termux builder have not drifted from it.

## Pinned inputs

The current lock fixes:

- GitHub CI base image: `ubuntu-24.04`;
- Temurin Java 17, Gradle 8.10.2, AGP 8.8.2 and Kotlin 2.1.10;
- Android API/target 36, Build Tools 36.0.0, NDK 28.2.13676358 and CMake 3.22.1;
- P1 Linux 6.12.107 plus its SHA-256;
- P2 AOSP manifest tag `android-16.0.0_r2`;
- QEMU 10.2.1 plus its source tarball SHA-256;
- the exact `termux/termux-packages` commit used as the Android/Bionic build oracle.

The Ubuntu runner label identifies the runner image family rather than an
immutable VM image digest. OS packages installed with `apt` are therefore
runner-snapshot inputs, not project-pinned source artifacts. The project-owned
compiler/platform versions above remain explicit.

## Runtime/native artifact hashes

`scripts/import_qemu_runtime.py` recursively imports the QEMU `DT_NEEDED`
closure, records size/SHA-256/dependencies/PT_LOAD alignments in
`qemu-runtime-manifest.json`, and now records source paths relative to the
Termux prefix so the manifest does not embed machine-specific absolute paths.

`scripts/verify_packaged_qemu.py --manifest ...` checks that the packaged ELF
set exactly matches the manifest, including every recorded SHA-256, byte size,
`DT_NEEDED` list and load alignment. The Android runtime independently verifies
file size and SHA-256 before execution.

## Deterministic source metadata

Run:

```sh
python3 scripts/generate_release_metadata.py
```

This deterministically regenerates:

- `FILELIST.txt` — sorted source inventory with SHA-256 for every release source
  file except the two self-referential generated metadata files;
- `release/release-manifest.json` — toolchain lock, full source hash inventory,
  QEMU runtime hash information when present, and explicit gate states.

The checked-in manifest deliberately defaults all execution/device gates to
`NOT_VERIFIED`. A source snapshot must never become a hardware PASS claim merely
because a proof file happened to be left in a workspace.

CI uses `--check` and fails when generated metadata is stale.

## Stale acceptance-proof guard

`scripts/validate_release_tree.py` rejects release trees containing
acceptance-proof-shaped JSON that claims `success=true`, `PASS`, `PASSED` or
`VERIFIED`. This prevents a previous P1/P2/P3 device proof from being silently
bundled into a new source/release package and misrepresented as current-run
acceptance.

`BUILD_STATUS.md` is also required to contain exactly two second-level status
sections: `VERIFIED` and `NOT VERIFIED`.

## Deterministic packaging

Create a source release with:

```sh
python3 scripts/package_release.py \
  --output dist/virtual-android-prototype-v0.7-ci-reproducible.zip
```

The packager:

1. runs the stale-proof/status guard;
2. regenerates release metadata;
3. sorts archive paths;
4. normalizes permissions;
5. fixes ZIP timestamps from `SOURCE_DATE_EPOCH` or the locked default;
6. excludes host/build caches such as `.git`, `.gradle`, `.work`, `build`,
   `__pycache__` and `dist`.

Given the same release tree and `SOURCE_DATE_EPOCH`, repeated archives are
byte-for-byte identical. `tests/test_release_tools.py` verifies this property.

## CI gates

`source-checks.yml` provides separate jobs for source/reproducibility checks,
pure Kotlin/JVM topology tests, fake-RFB integration, and QEMU dependency
closure verification when an imported runtime is present. `android-compile.yml`
compiles the APK with the pinned Android toolchain and verifies native 16 KiB
PT_LOAD alignment. `release-package.yml` repeats the deterministic package and
compares the resulting bytes. `device-smoke.yml` is manual and self-hosted only;
it cannot turn hosted CI into a physical-device PASS.
