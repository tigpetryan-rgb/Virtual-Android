package com.example.virtualandroid.vm

/**
 * Interactive P3 topology.
 *
 * QEMU's VNC listener is loopback-only. The UI process connects to the same
 * host network namespace and renders RFB frames locally; nothing is exposed to
 * the LAN. USB tablet gives absolute pointer coordinates suitable for touch.
 */
object P3QemuArgs {
    const val VNC_DISPLAY = 1
    const val VNC_PORT = 5900 + VNC_DISPLAY

    fun interactive(
        a: P2GuestArtifacts,
        memoryMiB: Int,
        vcpus: Int,
        adbHostPort: Int = 5555,
    ): List<String> {
        val base = P2QemuArgs.framework(a, memoryMiB, vcpus, adbHostPort).toMutableList()

        // P2 ends in "-display none". Remove that headless presentation pair.
        val displayIndex = base.indexOf("-display")
        if (displayIndex >= 0 && displayIndex + 1 < base.size) {
            base.removeAt(displayIndex + 1)
            base.removeAt(displayIndex)
        }

        // Mirror AOSP fvpbase's full-QEMU input topology, using tablet instead
        // of relative mouse so an Android touchscreen maps cleanly to RFB.
        base += listOf(
            "-usb",
            "-device", "qemu-xhci",
            "-device", "usb-kbd",
            "-device", "usb-tablet",
            "-vnc", "127.0.0.1:$VNC_DISPLAY",
        )
        return base
    }
}
