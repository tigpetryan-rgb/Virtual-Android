package com.example.virtualandroid.vm

import android.content.Context
import android.os.Build
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Launch boundary for the packaged QEMU host binary. */
class QemuProcessLauncher(private val context: Context) {
    @Volatile
    private var process: Process? = null

    enum class LaunchPath { DIRECT, LINKER64 }

    class LaunchFailure(
        val category: String,
        message: String,
        cause: Throwable? = null,
    ) : IOException("[$category] $message", cause)

    data class LaunchResult(
        val exitCode: Int,
        val launchPath: LaunchPath,
    )

    data class PreflightResult(
        val qemuPath: String,
        val launchPath: LaunchPath,
        val versionLine: String,
        val runtimeManifestSha256: String,
        val qemuSha256: String,
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
        val verification = QemuRuntimeVerifier.verify(context).getOrElse {
            throw LaunchFailure("RUNTIME_VERIFY", it.message ?: it.javaClass.simpleName, it)
        }
        onLog(
            "QEMU runtime verified: files=${verification.fileCount} bytes=${verification.totalBytes} " +
                "manifestSha256=${verification.manifestSha256} qemuSha256=${verification.qemuSha256}",
        )
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
        if (result.exitCode != 0) {
            val tail = result.lines.takeLast(8).joinToString(" | ")
            val category = if (tail.contains("CANNOT LINK EXECUTABLE", ignoreCase = true) ||
                tail.contains("not found", ignoreCase = true)
            ) {
                "RUNTIME_DEPENDENCY_OR_LINKER"
            } else {
                "PREFLIGHT_EXIT"
            }
            throw LaunchFailure(category, "QEMU --version exited ${result.exitCode}; output=$tail")
        }
        val first = result.lines.firstOrNull { it.isNotBlank() }
            ?: throw LaunchFailure("PREFLIGHT_NO_OUTPUT", "QEMU --version produced no output")
        if (!first.contains("QEMU", ignoreCase = true)) {
            throw LaunchFailure("PREFLIGHT_BAD_OUTPUT", "Unexpected QEMU --version output: $first")
        }

        val displayHelp = execute(
            qemu = qemu,
            args = listOf("-display", "help"),
            timeoutSeconds = 10,
            onLog = { onLog("qemu-display> $it") },
        )
        val vncAvailable = displayHelp.lines.any { line -> line.contains("vnc", ignoreCase = true) }
        if (requireVnc && !vncAvailable) {
            throw LaunchFailure("VNC_UNAVAILABLE", "Packaged QEMU lacks VNC display support; P3 requires CONFIG_VNC")
        }

        PreflightResult(
            qemuPath = qemu.absolutePath,
            launchPath = result.launchPath,
            versionLine = first,
            runtimeManifestSha256 = verification.manifestSha256,
            qemuSha256 = verification.qemuSha256,
            runtimeFileCount = verification.fileCount,
            runtimeBytes = verification.totalBytes,
            vncAvailable = vncAvailable,
        )
    }

    fun run(
        args: List<String>,
        timeoutSeconds: Long? = null,
        onLog: (String) -> Unit,
    ): Result<LaunchResult> = runCatching {
        check(process == null) { "VM process already exists" }
        val qemu = qemuFile()
        val result = execute(qemu, args, timeoutSeconds, onLog)
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
            if (!linker64.isFile) {
                throw LaunchFailure(
                    "EXEC_START_FAILED",
                    "direct QEMU exec failed (${directError.message}); /system/bin/linker64 is unavailable",
                    directError,
                )
            }
            val trampoline = listOf(linker64.absolutePath, qemu.absolutePath) + args
            onLog("direct exec failed: ${directError.message}")
            onLog("launch[linker64]=${trampoline.joinToString(" ") { shellQuoteForLog(it) }}")
            try {
                ProcessBuilder(trampoline).also(env).start() to LaunchPath.LINKER64
            } catch (linkerError: IOException) {
                throw LaunchFailure(
                    "EXEC_OR_JIT_DENIED",
                    "direct QEMU exec failed (${directError.message}); linker64 fallback failed (${linkerError.message})",
                    linkerError,
                )
            }
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
                reader.join(2_000)
                val tail = synchronized(lines) { lines.takeLast(8).joinToString(" | ") }
                throw LaunchFailure("TIMEOUT", "QEMU timed out after ${timeoutSeconds}s; output=$tail")
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
        if (!qemu.isFile || qemu.length() <= 0L) {
            throw LaunchFailure(
                "RUNTIME_MISSING",
                "Missing packaged QEMU host binary: ${qemu.absolutePath}. See third_party/qemu/README.md",
            )
        }
        return qemu
    }

    private fun shellQuoteForLog(s: String): String =
        if (s.all { it.isLetterOrDigit() || it in "-_=./,:" }) s
        else "'${s.replace("'", "'\\''")}'"
}
