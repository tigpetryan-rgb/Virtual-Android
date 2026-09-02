package com.example.virtualandroid.vm

import android.content.Context
import java.io.File

/**
 * Installs immutable P1 guest boot artifacts from APK assets into app-private storage.
 *
 * The files are data, not executable host code. The QEMU host executable/library is
 * packaged separately under the APK native-library directory.
 */
object GuestAssetInstaller {
    data class P1Artifacts(
        val kernel: File,
        val initramfs: File,
    )

    fun installP1(context: Context): Result<P1Artifacts> = runCatching {
        val outDir = File(context.filesDir, "guest/p1").apply { mkdirs() }
        val kernel = File(outDir, "Image")
        val initramfs = File(outDir, "initramfs.cpio.gz")

        copyAssetIfPresent(context, "p1/Image", kernel)
        copyAssetIfPresent(context, "p1/initramfs.cpio.gz", initramfs)

        require(kernel.isFile && kernel.length() > 0L) {
            "Missing P1 kernel asset: app/src/main/assets/p1/Image"
        }
        require(initramfs.isFile && initramfs.length() > 0L) {
            "Missing P1 initramfs asset: app/src/main/assets/p1/initramfs.cpio.gz"
        }
        P1Artifacts(kernel, initramfs)
    }

    private fun copyAssetIfPresent(context: Context, asset: String, target: File) {
        if (target.isFile && target.length() > 0L) return
        try {
            context.assets.open(asset).use { input ->
                target.outputStream().use(input::copyTo)
            }
        } catch (_: java.io.FileNotFoundException) {
            // The scaffold intentionally ships without a Linux kernel binary.
        }
    }
}
