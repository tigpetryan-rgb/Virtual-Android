package com.example.virtualandroid.vm

import android.content.Context
import android.os.Build
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Launch boundary for the P1 QEMU host binary.
 *
 * Expected package layout after the Android/QEMU import:
 *   <nativeLibraryDir>/libqemu_system_aarch64.so
 *   <nativeLibraryDir>/<QEMU runtime dependency libs...>
 */
class QemuProcessLauncher(private val context: Context) {
    @Volatile
    private var process: Process? = null

    enum class LaunchPath { DIRECT, LINKER64 }

    data class LaunchResult(
        val exitCode: Int,
        val launchPath: LaunchPath,
    )

    data class PreflightResult(
        val qemuPath: String,
        val launchPath: LaunchPath,
        val versionLine: String,
        val runtimeManifestSha256: String,
        val runtimeFileCount: Int,
        val runtimeBytes: Long,
        val vncAvailable: Boolean,
    )

    fun preflight(onLog: (String) -> Unit): Result<PreflightResult> = preflight(false, onLog)

    fun preflight(
        requireVnc: Boolean,
        onLog: (String) -> Unit,
    ): Result<PreflightResult> = runCatching {
        check(process == null) { "VM process already exists" }
        val verification = QemuRuntimeVerifier.verify(context).getOrThrow()
        onLog("QEMU runtime verified: files=${verification.fileCount} bytes=${verification.totalBytes} manifestSha256=${verification.manifestSha256}")
        val qemu = qemuFile()
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        onLog("host=${Build.MANUFACTURER} ${Build.MODEL} sdk=${Build.VERSION.SDK_INT}")
        onLog("nativeLibraryDir=${nativeDir.absolutePath}")
        onLog("qemu=${qemu.absolutePath} bytes=${qemu.length()} executable=${qemu.canExecute()}")

        val result = execute(
            qemu = qemu,
            args = listOf("--version"),
            timeoutSeconds = 10,
            onLog = { onLog("qemu-preflight> $it") },
        )
        require(result.exitCode == 0) { "QEMU --version exited ${result.exitCode}" }
        val first = result.lines.firstOrNull { it.isNotBlank() }
            ?: error("QEMU --version produced no output")
        require(first.contains("QEMU", ignoreCase = true)) {
            "Unexpected QEMU --version output: $first"
        }

        val displayHelp = execute(
            qemu = qemu,
            args = listOf("-display", "help"),
            timeoutSeconds = 10,
            onLog = { onLog("qemu-display> $it") },
        )
        val vncAvailable = displayHelp.lines.any { line ->
            line.trim().split(Regex("\\s+")).any { it.equals("vnc", ignoreCase = true) } ||
                line.contains("vnc", ignoreCase = true)
        }
        if (requireVnc) require(vncAvailable) {
            "Packaged QEMU lacks VNC display support; P3 requires CONFIG_VNC"
        }

        PreflightResult(
            qemu.absolutePath,
            result.launchPath,
            first,
            verification.manifestSha256,
            verification.fileCount,
            verification.totalBytes,
            vncAvailable,
        )
    }

    fun run(
        args: List<String>,
        timeoutSeconds: Long? = null,
        onLog: (String) -> Unit,
    ): Result<LaunchResult> = runCatching {
        check(process == null) { "VM process already exists" }
        val qemu = qemuFile()
        val result = execute(
            qemu = qemu,
            args = args,
            timeoutSeconds = timeoutSeconds,
            onLog = onLog,
        )
        LaunchResult(result.exitCode, result.launchPath)
    }

    fun stop(onLog: (String) -> Unit): Result<Unit> = runCatching {
        val p = process ?: return@runCatching
        onLog("Stopping QEMU process")
        p.destroy()
        if (!p.waitFor(2, TimeUnit.SECONDS)) {
            onLog("QEMU did not exit after SIGTERM; forcing stop")
            p.destroyForcibly()
            p.waitFor(2, TimeUnit.SECONDS)
        }
    }

    private data class ExecResult(
        val exitCode: Int,
        val launchPath: LaunchPath,
        val lines: List<String>,
    )

    private fun execute(
        qemu: File,
        args: List<String>,
        timeoutSeconds: Long?,
        onLog: (String) -> Unit,
    ): ExecResult {
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val env: (ProcessBuilder) -> Unit = { pb ->
            pb.directory(context.filesDir).redirectErrorStream(true)
            // All imported non-system DT_NEEDED libraries live beside QEMU.
            pb.environment()["LD_LIBRARY_PATH"] = nativeDir.absolutePath
            pb.environment()["TMPDIR"] = context.cacheDir.absolutePath
            pb.environment()["HOME"] = context.filesDir.absolutePath
            pb.environment()["XDG_CACHE_HOME"] = context.cacheDir.absolutePath
            pb.environment()["G_MESSAGES_DEBUG"] = ""
        }

        val direct = listOf(qemu.absolutePath) + args
        val (p, path) = try {
            onLog("launch[direct]=${direct.joinToString(" ") { shellQuoteForLog(it) }}")
            ProcessBuilder(direct).also(env).start() to LaunchPath.DIRECT
        } catch (directError: IOException) {
            val linker64 = File("/system/bin/linker64")
            if (!linker64.isFile) throw directError
            val trampoline = listOf(linker64.absolutePath, qemu.absolutePath) + args
            onLog("direct exec failed: ${directError.message}")
            onLog("launch[linker64]=${trampoline.joinToString(" ") { shellQuoteForLog(it) }}")
            ProcessBuilder(trampoline).also(env).start() to LaunchPath.LINKER64
        }

        process = p
        val lines = mutableListOf<String>()
        val reader = Thread {
            p.inputStream.bufferedReader().useLines { seq ->
                seq.forEach {
                    synchronized(lines) { lines += it }
                    onLog(it)
                }
            }
        }.apply {
            name = "va-qemu-output"
            isDaemon = true
            start()
        }

        try {
            if (timeoutSeconds == null) {
                p.waitFor()
            } else if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                p.destroyForcibly()
                p.waitFor(2, TimeUnit.SECONDS)
                throw IOException("QEMU timed out after ${timeoutSeconds}s")
            }
            reader.join(2_000)
            return ExecResult(
                exitCode = p.exitValue(),
                launchPath = path,
                lines = synchronized(lines) { lines.toList() },
            )
        } finally {
            process = null
        }
    }

    private fun qemuFile(): File {
        val qemu = File(context.applicationInfo.nativeLibraryDir, "libqemu_system_aarch64.so")
        require(qemu.isFile && qemu.length() > 0L) {
            "Missing packaged QEMU host binary: ${qemu.absolutePath}. See third_party/qemu/README.md"
        }
        return qemu
    }

    private fun shellQuoteForLog(s: String): String =
        if (s.all { it.isLetterOrDigit() || it in "-_=./,:" }) s
        else "'${s.replace("'", "'\\''")}'"
}
