package com.example.virtualandroid.vm

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Current-run-scoped proof artifact written after every P1 attempt. */
class P1RunReport(
    val runId: String,
    private val requestedMemoryMiB: Int,
    private val requestedVcpus: Int,
) {
    private val startedEpochMs = System.currentTimeMillis()
    private val startedNs = SystemClock.elapsedRealtimeNanos()
    private var preflightDoneNs: Long? = null
    private var firstOutputNs: Long? = null
    private var bootingLinuxNs: Long? = null
    private var markerNs: Long? = null
    private var preflightLaunchPath: String? = null
    private var guestLaunchPath: String? = null
    private var qemuVersion: String? = null
    private var qemuSha256: String? = null
    private var runtimeManifestSha256: String? = null
    private var runtimeFileCount: Int? = null
    private var runtimeBytes: Long? = null
    private var kernelSha256: String? = null
    private var kernelBytes: Long? = null
    private var initramfsSha256: String? = null
    private var initramfsBytes: Long? = null
    private var exitCode: Int? = null
    private val diagnosticSignals = linkedSetOf<String>()
    private var failureCategory: String? = null
    private var failure: String? = null

    fun onGuestArtifacts(artifacts: GuestAssetInstaller.P1Artifacts) {
        kernelSha256 = artifacts.kernelSha256
        kernelBytes = artifacts.kernel.length()
        initramfsSha256 = artifacts.initramfsSha256
        initramfsBytes = artifacts.initramfs.length()
    }

    fun onPreflight(result: QemuProcessLauncher.PreflightResult) {
        preflightDoneNs = SystemClock.elapsedRealtimeNanos()
        preflightLaunchPath = result.launchPath.name
        qemuVersion = result.versionLine
        qemuSha256 = result.qemuSha256
        runtimeManifestSha256 = result.runtimeManifestSha256
        runtimeFileCount = result.runtimeFileCount
        runtimeBytes = result.runtimeBytes
    }

    @Synchronized
    fun onGuestLog(line: String) {
        val now = SystemClock.elapsedRealtimeNanos()
        if (firstOutputNs == null) firstOutputNs = now
        if (bootingLinuxNs == null && line.contains("Booting Linux")) bootingLinuxNs = now
        if (markerNs == null && line.trim() == P1ProofContract.TERMINAL_MARKER) markerNs = now
        val lower = line.lowercase(Locale.US)
        if ((lower.contains("permission denied") && listOf("mmap", "mprotect", "tcg", "jit", "exec").any { lower.contains(it) }) ||
            lower.contains("dynamic translator buffer") || lower.contains("failed to set up tcg")) {
            diagnosticSignals += "EXEC_OR_JIT_DENIAL"
        }
        if (lower.contains("could not load kernel") || lower.contains("failed to load kernel") || lower.contains("invalid kernel")) {
            diagnosticSignals += "KERNEL_LOAD_ERROR"
        }
        if (lower.contains("kernel panic") || lower.contains("no working init found") ||
            lower.contains("failed to execute /init") || lower.contains("requested init /init failed")) {
            diagnosticSignals += "KERNEL_OR_INITRAMFS_PANIC"
        }
    }

    fun onExit(code: Int, path: QemuProcessLauncher.LaunchPath) {
        exitCode = code
        guestLaunchPath = path.name
    }

    fun onFailure(category: String, detail: String) {
        failureCategory = category
        failure = detail
    }

    fun markerSeen(): Boolean = markerNs != null
    fun bootingLinuxSeen(): Boolean = bootingLinuxNs != null
    fun firstOutputSeen(): Boolean = firstOutputNs != null

    fun earlyExitCategory(): String = when {
        "EXEC_OR_JIT_DENIAL" in diagnosticSignals -> "EXEC_OR_JIT_DENIED"
        "KERNEL_LOAD_ERROR" in diagnosticSignals -> "KERNEL_LOAD_ERROR"
        "KERNEL_OR_INITRAMFS_PANIC" in diagnosticSignals -> "KERNEL_OR_INITRAMFS_PANIC"
        bootingLinuxSeen() -> "INITRAMFS_OR_INIT_EARLY_EXIT"
        firstOutputSeen() -> "KERNEL_OR_MACHINE_EARLY_EXIT"
        else -> "QEMU_EARLY_EXIT_NO_OUTPUT"
    }

    fun timeoutCategory(): String = when {
        "EXEC_OR_JIT_DENIAL" in diagnosticSignals -> "EXEC_OR_JIT_DENIED"
        "KERNEL_OR_INITRAMFS_PANIC" in diagnosticSignals -> "KERNEL_OR_INITRAMFS_PANIC"
        bootingLinuxSeen() -> "INITRAMFS_OR_INIT_TIMEOUT"
        firstOutputSeen() -> "KERNEL_OR_MACHINE_TIMEOUT"
        else -> "QEMU_TIMEOUT_NO_OUTPUT"
    }

    fun write(context: Context): File {
        val finishedEpochMs = System.currentTimeMillis()
        val outDir = P1ReportStore.reportDir(context)
        val stamp = utcFileStamp(startedEpochMs)
        val safeRunId = runId.replace(Regex("[^A-Za-z0-9._-]"), "_").take(48)
        val out = File(outDir, "p1-$stamp-$safeRunId.json")
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionCode = if (Build.VERSION.SDK_INT >= 28) packageInfo.longVersionCode else packageInfo.versionCode.toLong()
        val success = markerSeen() && failure == null

        val json = JSONObject().apply {
            put("format", P1ProofContract.FORMAT)
            put("runId", runId)
            put("backend", P1ProofContract.BACKEND)
            put("terminalMarker", P1ProofContract.TERMINAL_MARKER)
            put("terminalMarkerSeen", if (markerSeen()) P1ProofContract.TERMINAL_MARKER else JSONObject.NULL)
            put("markerSeen", markerSeen())
            put("success", success)
            put("startedAtUtc", utcIso(startedEpochMs))
            put("finishedAtUtc", utcIso(finishedEpochMs))
            put("startedAtEpochMs", startedEpochMs)
            put("finishedAtEpochMs", finishedEpochMs)
            put("elapsedRealtimeStartNs", startedNs)
            put("lastBootStage", lastBootStage())
            put("app", JSONObject().apply {
                put("packageName", context.packageName)
                put("versionName", packageInfo.versionName ?: JSONObject.NULL)
                put("versionCode", versionCode)
                put("firstInstallTime", packageInfo.firstInstallTime)
                put("lastUpdateTime", packageInfo.lastUpdateTime)
                put("debuggable", (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0)
            })
            put("host", JSONObject().apply {
                put("manufacturer", Build.MANUFACTURER)
                put("brand", Build.BRAND)
                put("model", Build.MODEL)
                put("device", Build.DEVICE)
                put("product", Build.PRODUCT)
                put("buildId", Build.ID)
                put("buildFingerprint", Build.FINGERPRINT)
                put("buildIncremental", Build.VERSION.INCREMENTAL)
                put("sdk", Build.VERSION.SDK_INT)
                put("supportedAbis", Build.SUPPORTED_ABIS.joinToString(","))
            })
            put("guest", JSONObject().apply {
                put("memoryMiB", requestedMemoryMiB.coerceIn(256, 2048))
                put("vcpus", requestedVcpus.coerceIn(1, 4))
                put("kernelSha256", kernelSha256 ?: JSONObject.NULL)
                put("kernelBytes", kernelBytes ?: JSONObject.NULL)
                put("initramfsSha256", initramfsSha256 ?: JSONObject.NULL)
                put("initramfsBytes", initramfsBytes ?: JSONObject.NULL)
            })
            put("qemu", JSONObject().apply {
                put("version", qemuVersion ?: JSONObject.NULL)
                put("preflightLaunchPath", preflightLaunchPath ?: JSONObject.NULL)
                put("guestLaunchPath", guestLaunchPath ?: JSONObject.NULL)
                put("exitCode", exitCode ?: JSONObject.NULL)
                put("qemuSha256", qemuSha256 ?: JSONObject.NULL)
                put("runtimeManifestSha256", runtimeManifestSha256 ?: JSONObject.NULL)
                put("runtimeFileCount", runtimeFileCount ?: JSONObject.NULL)
                put("runtimeBytes", runtimeBytes ?: JSONObject.NULL)
            })
            put("timingMs", JSONObject().apply {
                putNullable("preflight", deltaMs(preflightDoneNs))
                putNullable("firstOutput", deltaMs(firstOutputNs))
                putNullable("bootingLinux", deltaMs(bootingLinuxNs))
                putNullable("bootMarker", deltaMs(markerNs))
                put("total", (SystemClock.elapsedRealtimeNanos() - startedNs) / 1_000_000L)
            })
            put("diagnosticSignals", JSONArray().apply { diagnosticSignals.forEach { put(it) } })
            put("failureCategory", failureCategory ?: JSONObject.NULL)
            put("failure", failure ?: JSONObject.NULL)
        }
        val text = json.toString(2) + "\n"
        out.writeText(text)
        P1ReportStore.latestFile(context).writeText(text)
        return out
    }

    private fun lastBootStage(): String = when {
        markerSeen() -> "TERMINAL_MARKER"
        bootingLinuxSeen() -> "BOOTING_LINUX"
        firstOutputSeen() -> "QEMU_OUTPUT"
        preflightDoneNs != null -> "PREFLIGHT_OK"
        else -> "PREPARING"
    }

    private fun deltaMs(valueNs: Long?): Long? = valueNs?.let { (it - startedNs) / 1_000_000L }

    private fun JSONObject.putNullable(name: String, value: Long?) {
        put(name, value ?: JSONObject.NULL)
    }

    private fun utcFileStamp(epochMs: Long): String = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(epochMs))

    private fun utcIso(epochMs: Long): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(epochMs))
}
