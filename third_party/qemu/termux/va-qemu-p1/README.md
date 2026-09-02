# Termux build-oracle recipe for P1

This is deliberately a **P1 bridge**, not the final distribution architecture.
It reuses Termux's Android/Bionic porting environment and its AArch64 setjmp
workaround, but trims QEMU to one system target and removes UI/network/tooling
features we do not need for the first boot marker.

Install it into a current `termux-packages` checkout:

```sh
./scripts/install_termux_qemu_recipe.sh /work/termux-packages
cd /work/termux-packages
./build-package.sh -a aarch64 va-qemu-p1
```

Once the package has been built/extracted into a Termux prefix, import QEMU and
its recursive `DT_NEEDED` closure into the APK:

```sh
./scripts/import_termux_qemu.sh /path/to/termux-prefix
```

The importer validates AArch64 + PIE, recursively copies non-system shared
libraries, optionally strips RPATH/RUNPATH with `patchelf`, and writes
`qemu-runtime-manifest.json` with hashes and dependency information.

If the pinned configure flags drift in a later QEMU release, keep P1 pinned to
10.2.1 until the boot marker passes. Version upgrades belong after P1.
