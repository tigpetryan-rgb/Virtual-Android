package com.example.virtualandroid.vm

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * Installs immutable P1 guest boot artifacts from APK assets into app-private storage.
 *
 * The files are data, not executable host code. They are refreshed from the current APK
 * on every P1 attempt so a removed/replaced asset can never be satisfied by stale data
 * left in filesDir by a previous install/update.
 */
object GuestAssetInstaller {
    data class P1Artifacts(
        val kernel: File,
        val initramfs: File,
        val kernelSha256: String,
        val initramfsSha256: String,
    )

    fun installP1(context: Context): Result<P1Artifacts> = runCatching {
        val outDir = File(context.filesDir, "guest/p1").apply { mkdirs() }
        val kernel = File(outDir, "Image")
        val initramfs = File(outDir, "initramfs.cpio.gz")

        installRequiredAsset(context, "p1/Image", kernel)
        installRequiredAsset(context, "p1/initramfs.cpio.gz", initramfs)

        P1Artifacts(
            kernel = kernel,
            initramfs = initramfs,
            kernelSha256 = sha256(kernel),
            initramfsSha256 = sha256(initramfs),
        )
    }

    private fun installRequiredAsset(context: Context, asset: String, target: File) {
        val tmp = File(target.parentFile, ".${target.name}.current-apk.tmp")
        tmp.delete()
        try {
            context.assets.open(asset).use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (e: java.io.FileNotFoundException) {
            tmp.delete()
            error("Missing P1 asset in current APK: app/src/main/assets/$asset")
        }
        require(tmp.isFile && tmp.length() > 0L) { "Empty P1 asset in current APK: $asset" }
        if (target.exists() && !target.delete()) error("Could not replace stale P1 asset: ${target.absolutePath}")
        require(tmp.renameTo(target)) { "Could not install current P1 asset: ${target.absolutePath}" }
    }

    private fun sha256(file: File): String = file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(1024 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n <= 0) break
            digest.update(buffer, 0, n)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
}
