package com.example.virtualandroid.vm

import java.io.File

object QemuArgs {
    /**
     * P1 intentionally has no block disks, graphics, or network.
     * Success criterion: Linux reaches /init and prints VA_P1_GUEST_OK on PL011.
     */
    fun p1(kernel: File, initramfs: File, memoryMiB: Int, vcpus: Int): List<String> = listOf(
        "-machine", "virt,gic-version=3",
        "-cpu", "max",
        "-accel", "tcg,thread=multi",
        "-smp", vcpus.coerceIn(1, 4).toString(),
        "-m", memoryMiB.coerceIn(256, 2048).toString(),
        "-kernel", kernel.absolutePath,
        "-initrd", initramfs.absolutePath,
        "-append", "console=ttyAMA0 earlycon=pl011,0x09000000 rdinit=/init panic=-1",
        "-display", "none",
        "-serial", "stdio",
        "-monitor", "none",
        "-no-reboot",
        "-no-shutdown",
    )
}
