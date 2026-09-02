package com.example.virtualandroid.vm

/**
 * Android 16 P2 command line derived from AOSP fvpbase/run_qemu.
 *
 * We keep AOSP's hardware identity and disk/network topology so the APK runner
 * consumes the same build artifacts that upstream AOSP already boots on QEMU virt.
 */
object P2QemuArgs {
    fun framework(
        a: P2GuestArtifacts,
        memoryMiB: Int,
        vcpus: Int,
        adbHostPort: Int = 5555,
    ): List<String> = listOf(
        "-kernel", a.kernel.absolutePath,
        "-initrd", a.combinedRamdisk.absolutePath,
        "-machine", "virt,mte=on",
        "-cpu", "max",
        "-accel", "tcg,thread=multi",
        "-drive", "driver=raw,file=${a.systemQemu.absolutePath},if=none,id=system,readonly=on",
        "-device", "virtio-blk-device,drive=system",
        "-drive", "driver=raw,file=${a.userdata.absolutePath},if=none,id=userdata",
        "-device", "virtio-blk-device,drive=userdata",
        "-append", listOf(
            "console=ttyAMA0",
            "earlyprintk=ttyAMA0",
            "androidboot.hardware=qemu",
            "androidboot.boot_devices=a003e00.virtio_mmio",
            "loglevel=9",
        ).joinToString(" "),
        "-m", memoryMiB.coerceIn(1536, 4096).toString(),
        "-smp", vcpus.coerceIn(1, 8).toString(),
        "-no-reboot",
        "-nic", "user,model=virtio-net-pci-non-transitional,hostfwd=tcp:127.0.0.1:$adbHostPort-172.20.51.1:5555,host=172.20.51.254,net=172.20.51.0/24,dhcpstart=172.20.51.1",
        // Full fvp starts graphics/framework services. Keep the virtual GPU even
        // though the host presentation window is intentionally disabled in P2.
        "-device", "virtio-gpu-pci",
        "-display", "none",
        "-serial", "stdio",
        "-monitor", "none",
    )
}
