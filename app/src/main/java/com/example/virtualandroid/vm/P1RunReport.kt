package com.example.virtualandroid.vm

import android.content.Context
import android.os.Build
import android.os.SystemClock
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Small proof artifact written after every P1 attempt. */
class P1RunReport(
    private val requestedMemoryMiB: Int,
    private val requestedVcpus: Int,
) {
    private val startedNs = SystemClock.elapsedRealtimeNanos()
    private var preflightDoneNs: Long? = null
    private var firstOutputNs: Long? = null
    private var bootingLinuxNs: Long? = null
    private var markerNs: Long? = null
    private var launchPath: String? = null
    private var qemuVersion: String? = null
    private var runtimeManifestSha256: String? = null
    private var runtimeFileCount: Int? = null
    private var runtimeBytes: Long? = null
    private var exitCode: Int? = null
    private var failure: String? = null

    fun onPreflight(result: QemuProcessLauncher.PreflightResult) {
        preflightDoneNs = SystemClock.elapsedRealtimeNanos()
        launchPath = result.launchPath.name
        qemuVersion = result.versionLine
        runtimeManifestSha256 = result.runtimeManifestSha256
        runtimeFileCount = result.runtimeFileCount
        runtimeBytes = result.runtimeBytes
    }

    fun onGuestLog(line: String) {
        val now = SystemClock.elapsedRealtimeNanos()
        if (firstOutputNs == null) firstOutputNs = now
        if (bootingLinuxNs == null && line.contains("Booting Linux")) bootingLinuxNs = now
        if (markerNs == null && line.contains("VA_P1_GUEST_OK")) markerNs = now
    }

    fun onExit(code: Int, path: QemuProcessLauncher.LaunchPath) {
        exitCode = code
        launchPath = path.name
    }

    fun onFailure(detail: String) {
        failure = detail
    }

    fun markerSeen(): Boolean = markerNs != null

    fun write(context: Context): File {
        val outDir = P1ReportStore.reportDir(context)
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        val out = File(outDir, "p1-$stamp.json")
        val json = JSONObject().apply {
            put("format", 2)
            put("markerSeen", markerSeen())
            put("success", markerSeen())
            put("host", JSONObject().apply {
                put("manufacturer", Build.MANUFACTURER)
                put("model", Build.MODEL)
                put("device", Build.DEVICE)
                put("sdk", Build.VERSION.SDK_INT)
                put("supportedAbis", Build.SUPPORTED_ABIS.joinToString(","))
            })
            put("guest", JSONObject().apply {
                put("memoryMiB", requestedMemoryMiB)
                put("vcpus", requestedVcpus)
            })
            put("qemu", JSONObject().apply {
                put("version", qemuVersion ?: JSONObject.NULL)
                put("launchPath", launchPath ?: JSONObject.NULL)
                put("exitCode", exitCode ?: JSONObject.NULL)
                put("runtimeManifestSha256", runtimeManifestSha256 ?: JSONObject.NULL)
                put("runtimeFileCount", runtimeFileCount ?: JSONObject.NULL)
                put("runtimeBytes", runtimeBytes ?: JSONObject.NULL)
            })
            put("timingMs", JSONObject().apply {
                putNullable("preflight", deltaMs(preflightDoneNs))
                putNullable("firstOutput", deltaMs(firstOutputNs))
                putNullable("bootingLinux", deltaMs(bootingLinuxNs))
                putNullable("bootMarker", deltaMs(markerNs))
            })
            put("failure", failure ?: JSONObject.NULL)
        }
        val text = json.toString(2) + "\n"
        out.writeText(text)
        P1ReportStore.latestFile(context).writeText(text)
        return out
    }

    private fun deltaMs(valueNs: Long?): Long? = valueNs?.let { (it - startedNs) / 1_000_000L }

    private fun JSONObject.putNullable(name: String, value: Long?) {
        put(name, value ?: JSONObject.NULL)
    }
}
