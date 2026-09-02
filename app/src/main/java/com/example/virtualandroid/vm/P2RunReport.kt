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

class P2RunReport(
    private val requestedMemoryMiB: Int,
    private val requestedVcpus: Int,
) {
    private val startedNs = SystemClock.elapsedRealtimeNanos()
    private var preflightNs: Long? = null
    private var firstOutputNs: Long? = null
    private var linuxNs: Long? = null
    private var initNs: Long? = null
    private var servicemanagerNs: Long? = null
    private var zygoteNs: Long? = null
    private var frameworkNs: Long? = null
    private var stage = P2BootStage.STARTING
    private var launchPath: String? = null
    private var qemuVersion: String? = null
    private var runtimeManifestSha256: String? = null
    private var bundleManifestSha256: String? = null
    private var bundleBytes: Long? = null
    private var exitCode: Int? = null
    private var failure: String? = null

    fun onPreflight(result: QemuProcessLauncher.PreflightResult, guest: P2GuestArtifacts) {
        preflightNs = SystemClock.elapsedRealtimeNanos()
        launchPath = result.launchPath.name
        qemuVersion = result.versionLine
        runtimeManifestSha256 = result.runtimeManifestSha256
        bundleManifestSha256 = guest.bundleManifestSha256
        bundleBytes = guest.bundleBytes
    }

    @Synchronized
    fun onGuestLog(line: String): P2BootStage {
        val now = SystemClock.elapsedRealtimeNanos()
        if (firstOutputNs == null) firstOutputNs = now
        stage = P2BootStageClassifier.advance(stage, line)
        when (stage) {
            P2BootStage.LINUX -> if (linuxNs == null) linuxNs = now
            P2BootStage.ANDROID_INIT -> {
                if (linuxNs == null) linuxNs = now
                if (initNs == null) initNs = now
            }
            P2BootStage.SERVICEMANAGER -> {
                if (initNs == null) initNs = now
                if (servicemanagerNs == null) servicemanagerNs = now
            }
            P2BootStage.ZYGOTE -> {
                if (servicemanagerNs == null) servicemanagerNs = now
                if (zygoteNs == null) zygoteNs = now
            }
            P2BootStage.FRAMEWORK_READY -> {
                if (zygoteNs == null) zygoteNs = now
                if (frameworkNs == null) frameworkNs = now
            }
            P2BootStage.STARTING -> Unit
        }
        return stage
    }

    fun onExit(code: Int, path: QemuProcessLauncher.LaunchPath) {
        exitCode = code
        launchPath = path.name
    }

    fun onFailure(detail: String) { failure = detail }
    fun frameworkReady(): Boolean = frameworkNs != null
    fun currentStage(): P2BootStage = stage

    fun write(context: Context): File {
        val outDir = P1ReportStore.reportDir(context)
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        val out = File(outDir, "p2-$stamp.json")
        val json = JSONObject().apply {
            put("format", 2)
            put("success", frameworkReady())
            put("lastStage", stage.name)
            put("androidInitSeen", initNs != null)
            put("servicemanagerSeen", servicemanagerNs != null)
            put("zygoteSeen", zygoteNs != null)
            put("frameworkReady", frameworkReady())
            put("host", JSONObject().apply {
                put("manufacturer", Build.MANUFACTURER)
                put("model", Build.MODEL)
                put("device", Build.DEVICE)
                put("sdk", Build.VERSION.SDK_INT)
            })
            put("guest", JSONObject().apply {
                put("product", "va_fvp")
                put("hardware", "qemu")
                put("memoryMiB", requestedMemoryMiB)
                put("vcpus", requestedVcpus)
                put("bundleManifestSha256", bundleManifestSha256 ?: JSONObject.NULL)
                put("bundleBytes", bundleBytes ?: JSONObject.NULL)
            })
            put("qemu", JSONObject().apply {
                put("version", qemuVersion ?: JSONObject.NULL)
                put("launchPath", launchPath ?: JSONObject.NULL)
                put("exitCode", exitCode ?: JSONObject.NULL)
                put("runtimeManifestSha256", runtimeManifestSha256 ?: JSONObject.NULL)
            })
            put("timingMs", JSONObject().apply {
                putNullable("preflight", deltaMs(preflightNs))
                putNullable("firstOutput", deltaMs(firstOutputNs))
                putNullable("bootingLinux", deltaMs(linuxNs))
                putNullable("androidInit", deltaMs(initNs))
                putNullable("servicemanager", deltaMs(servicemanagerNs))
                putNullable("zygote", deltaMs(zygoteNs))
                putNullable("frameworkReady", deltaMs(frameworkNs))
            })
            put("failure", failure ?: JSONObject.NULL)
        }
        val text = json.toString(2) + "\n"
        out.writeText(text)
        P2ReportStore.latestFile(context).writeText(text)
        return out
    }

    private fun deltaMs(valueNs: Long?): Long? = valueNs?.let { (it - startedNs) / 1_000_000L }
    private fun JSONObject.putNullable(name: String, value: Long?) { put(name, value ?: JSONObject.NULL) }
}
