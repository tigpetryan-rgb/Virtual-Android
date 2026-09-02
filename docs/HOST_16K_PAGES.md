# 16 KiB host page-size requirement

The universal APK must work on both traditional 4 KiB-page ARM64 Android hosts
and newer 16 KiB-page devices. This is a host-native-code requirement and is
independent of the guest Android page size.

P1 therefore treats **16 KiB ELF compatibility as a packaging invariant**:

- `libvaprobes.so` is linked with 16 KiB max/common page size explicitly;
- the QEMU build-oracle recipe applies the same linker flags to the host QEMU PIE;
- every imported non-system QEMU dependency is checked after import;
- every ELF `PT_LOAD` alignment must be at least `0x4000` by default;
- the capability report records the physical host's runtime page size using
  `sysconf(_SC_PAGESIZE)`.

The importer can technically be invoked with a lower `--min-load-align` for a
throwaway local experiment, but release/prototype artifacts intended to support
our universal goal must use the default `16384` check.

This catches a subtle failure mode where P1 succeeds on a 4 KiB test phone but
the APK cannot even load its native runtime on a 16 KiB device.
