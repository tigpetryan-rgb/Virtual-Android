package com.example.virtualandroid.vm

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

data class QemuRuntimeVerification(
    val manifestSha256: String,
    val fileCount: Int,
    val totalBytes: Long,
    val minimumLoadAlignment: Int,
)

/** Verifies the imported QEMU runtime before any child process is executed. */
object QemuRuntimeVerifier {
    private const val MANIFEST_ASSET = "qemu/qemu-runtime-manifest.json"

    fun verify(context: Context): Result<QemuRuntimeVerification> = runCatching {
        val manifestBytes = try {
            context.assets.open(MANIFEST_ASSET).use { it.readBytes() }
        } catch (e: Exception) {
            error("Missing $MANIFEST_ASSET. Import QEMU with scripts/import_termux_qemu.sh before P1: ${e.message}")
        }
        val manifest = JSONObject(manifestBytes.toString(Charsets.UTF_8))
        require(manifest.optInt("format", -1) == 1) { "Unsupported QEMU runtime manifest format" }
        require(manifest.optString("host_abi") == "arm64-v8a") { "QEMU runtime manifest is not arm64-v8a" }
        val main = manifest.getString("main")
        require(main == "libqemu_system_aarch64.so") { "Unexpected QEMU main binary: $main" }

        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val files = manifest.getJSONArray("files")
        var total = 0L
        for (i in 0 until files.length()) {
            val entry = files.getJSONObject(i)
            val name = entry.getString("name")
            require(name.startsWith("lib") && name.endsWith(".so") && !name.contains('/')) {
                "Unsafe packaged runtime filename: $name"
            }
            val file = File(nativeDir, name)
            require(file.isFile) { "QEMU runtime file missing after install: ${file.absolutePath}" }
            val expectedBytes = entry.getLong("bytes")
            require(file.length() == expectedBytes) {
                "QEMU runtime size mismatch for $name: installed=${file.length()} manifest=$expectedBytes"
            }
            val expectedSha = entry.getString("sha256")
            val actualSha = sha256(file)
            require(actualSha.equals(expectedSha, ignoreCase = true)) {
                "QEMU runtime SHA-256 mismatch for $name"
            }
            total += file.length()
        }
        require(files.length() > 0) { "QEMU runtime manifest contains no files" }
        require(File(nativeDir, main).isFile) { "QEMU main binary missing: $main" }

        QemuRuntimeVerification(
            manifestSha256 = sha256(manifestBytes),
            fileCount = files.length(),
            totalBytes = total,
            minimumLoadAlignment = manifest.optInt("minimum_load_alignment", 0),
        )
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

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
