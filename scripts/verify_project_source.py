#!/usr/bin/env python3
from pathlib import Path
import re
import sys

root = Path(__file__).resolve().parents[1]
errors: list[str] = []

def require(cond: bool, msg: str) -> None:
    if not cond:
        errors.append(msg)

manifest = (root / "app/src/main/AndroidManifest.xml").read_text()
gradle = (root / "app/build.gradle.kts").read_text()
qargs = (root / "app/src/main/java/com/example/virtualandroid/vm/QemuArgs.kt").read_text()
service = (root / "app/src/main/java/com/example/virtualandroid/vm/VmService.kt").read_text()
launcher = (root / "app/src/main/java/com/example/virtualandroid/vm/QemuProcessLauncher.kt").read_text()
cmake = (root / "app/src/main/cpp/CMakeLists.txt").read_text()
recipe = (root / "third_party/qemu/termux/va-qemu-p1/build.sh").read_text()
p2args = (root / "app/src/main/java/com/example/virtualandroid/vm/P2QemuArgs.kt").read_text()
p2overlay = (root / "guest/p2/aosp-overlay/device/virtualandroid/fvp/va_fvp.mk").read_text()
p2rc = (root / "guest/p2/aosp-overlay/device/virtualandroid/fvp/init.virtualandroid.rc").read_text()
p3args = (root / "app/src/main/java/com/example/virtualandroid/vm/P3QemuArgs.kt").read_text()
rfb = (root / "app/src/main/java/com/example/virtualandroid/display/RfbProtocol.kt").read_text()
aidl = (root / "app/src/main/aidl/com/example/virtualandroid/vm/IVmService.aidl").read_text()

status = (root / "BUILD_STATUS.md").read_text()
settings = (root / "settings.gradle.kts").read_text()
release_doc = (root / "docs/REPRODUCIBLE_BUILDS.md").read_text()

require('android:process=":vm"' in manifest, "VmService must remain isolated in :vm")
require('abiFilters += listOf("arm64-v8a")' in gradle, "P1 APK must currently be arm64-only")
require('useLegacyPackaging = true' in gradle, "QEMU executable requires extracted nativeLibraryDir")
require('keepDebugSymbols += "**/*.so"' in gradle, "QEMU runtime hashes require AGP native stripping to be disabled")
require('"-accel", "tcg,thread=multi"' in qargs, "P1 must explicitly use TCG")
require('"-display", "none"' in qargs, "P1 must remain headless")
require('VA_P1_GUEST_OK' in service, "P1 acceptance marker handling missing")
require('P1_TIMEOUT_SECONDS' in service, "P1 watchdog missing")
require('QemuRuntimeVerifier.verify(context)' in launcher, "QEMU runtime hash verification missing")
require('max-page-size=16384' in cmake, "JNI probe must be 16K-page compatible")
require('max-page-size=16384' in recipe, "QEMU build must be 16K-page compatible")
require('--target-list=aarch64-softmmu' in recipe, "QEMU recipe must build only AArch64 system emulation")
require('--enable-tcg' in recipe and '--disable-kvm' in recipe, "P1 QEMU recipe must be universal TCG-only")
require('--enable-slirp' in recipe and 'libslirp' in recipe, "QEMU runtime must carry unprivileged user-mode networking for P2")
require('--enable-vnc' in recipe and not re.search(r'^\s*--disable-vnc\s+\\$', recipe, re.M), "P3 QEMU recipe must include the VNC display backend")

require('"androidboot.hardware=qemu"' in p2args, "P2 must retain upstream AOSP qemu hardware identity")
require('"virt,mte=on"' in p2args, "P2 must track AOSP fvpbase QEMU machine contract")
require('system-qemu.img' in (root / "scripts/export_p2_bundle.sh").read_text(), "P2 exporter must carry system-qemu.img")
require('device/generic/goldfish/fvpbase/fvp.mk' in p2overlay, "P2 product must inherit upstream fvpbase")
require('VA_P2_FRAMEWORK_OK' in p2rc, "P2 framework acceptance marker missing")
require('VA_P2_SERVICEMANAGER_OK' in p2rc and 'VA_P2_ZYGOTE_OK' in p2rc, "P2 intermediate milestone markers missing")
require('startP2Guest' in aidl, "P2 Binder API missing")
require('startP3Guest' in aidl, "P3 Binder API missing")
require('"-vnc", "127.0.0.1:$VNC_DISPLAY"' in p3args, "P3 VNC must stay loopback-only")
require('"usb-tablet"' in p3args and '"usb-kbd"' in p3args, "P3 absolute pointer/keyboard devices missing")
require('RFB 003.008' in rfb and 'writeS32Locked(out, 0) // Raw only' in rfb, "P3 minimal RFB 3.8 Raw client missing")
require('requireVnc = true' in service, "P3 must hard-check QEMU VNC capability before launch")

require('include(":host-tests")' in settings, "Reproducible pure Kotlin/JVM host-test module missing")
require(status.count("## VERIFIED") == 1 and status.count("## NOT VERIFIED") == 1, "BUILD_STATUS must keep strict VERIFIED / NOT VERIFIED split")
require('toolchain.lock.json' in release_doc and 'package_release.py' in release_doc, "Reproducible build documentation missing")
require((root / "release/release-manifest.json").is_file(), "Machine-readable release manifest missing")
require((root / "config/toolchain.lock.json").is_file(), "Central toolchain lock missing")

if errors:
    for e in errors:
        print(f"FAIL {e}")
    sys.exit(2)
print("Project source invariants: OK")
