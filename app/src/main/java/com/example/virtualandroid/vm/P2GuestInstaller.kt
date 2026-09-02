package com.example.virtualandroid.vm

import android.content.Context
import java.io.File
import java.security.MessageDigest

/** Verifies the externally seeded AOSP P2 bundle in app-private storage. */
object P2GuestInstaller {
    private val required = listOf(
        "kernel",
        "combined-ramdisk.img",
        "system-qemu.img",
        "userdata.img",
    )

    fun verify(context: Context): Result<P2GuestArtifacts> = runCatching {
        val dir = File(context.filesDir, "p2")
        require(dir.isDirectory) { "Missing P2 directory: ${dir.absolutePath}" }
        val sums = File(dir, "SHA256SUMS")
        require(sums.isFile && sums.length() > 0L) { "Missing P2 SHA256SUMS" }
        val expected = parseSums(sums)
        require(expected.keys.containsAll(required)) { "P2 SHA256SUMS does not cover all required artifacts" }

        var bytes = 0L
        val files = required.associateWith { name ->
            val f = File(dir, name)
            require(f.isFile && f.length() > 0L) { "Missing P2 artifact: $name" }
            val actual = sha256(f)
            require(actual.equals(expected.getValue(name), ignoreCase = true)) {
                "P2 SHA-256 mismatch for $name: expected=${expected.getValue(name)} actual=$actual"
            }
            bytes += f.length()
            f
        }

        P2GuestArtifacts(
            kernel = files.getValue("kernel"),
            combinedRamdisk = files.getValue("combined-ramdisk.img"),
            systemQemu = files.getValue("system-qemu.img"),
            userdata = files.getValue("userdata.img"),
            bundleManifestSha256 = sha256(sums),
            bundleBytes = bytes,
        )
    }

    private fun parseSums(file: File): Map<String, String> = file.readLines()
        .filter { it.isNotBlank() }
        .associate { line ->
            val parts = line.trim().split(Regex("\\s+"), limit = 2)
            require(parts.size == 2 && parts[0].matches(Regex("[0-9a-fA-F]{64}"))) {
                "Malformed SHA256SUMS line: $line"
            }
            val name = parts[1].removePrefix("*").trim()
            require('/' !in name && '\\' !in name) { "Unsafe P2 artifact name: $name" }
            name to parts[0].lowercase()
        }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n <= 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
