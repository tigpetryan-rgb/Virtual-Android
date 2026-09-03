package com.example.virtualandroid.vm

import android.content.Context
import android.os.Build
import java.io.File
import org.json.JSONObject

object P1ReportStore {
    private const val LATEST = "latest-p1.json"

    fun reportDir(context: Context): File = File(context.filesDir, "reports").apply { mkdirs() }

    fun latestFile(context: Context): File = File(reportDir(context), LATEST)

    fun latestText(context: Context): String? = latestFile(context)
        .takeIf { it.isFile && it.length() > 0L }
        ?.readText()

    /**
     * P2 may consume only a proof produced by the currently installed app build on the
     * current host build. This rejects legacy v0.7 reports and reports surviving an APK
     * update/reinstall; adb_smoke_p1 adds the stronger same-run runId check.
     */
    fun latestPassed(context: Context): Boolean = runCatching {
        val json = JSONObject(latestText(context) ?: return false)
        if (json.optInt("format", -1) != P1ProofContract.FORMAT) return false
        if (!json.optBoolean("success", false) || !json.optBoolean("markerSeen", false)) return false
        if (json.optString("backend") != P1ProofContract.BACKEND) return false
        if (json.optString("terminalMarkerSeen") != P1ProofContract.TERMINAL_MARKER) return false
        if (json.optString("runId").isBlank()) return false
        val host = json.optJSONObject("host") ?: return false
        if (host.optString("buildFingerprint") != Build.FINGERPRINT) return false
        val app = json.optJSONObject("app") ?: return false
        if (app.optString("packageName") != context.packageName) return false
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionCode = if (Build.VERSION.SDK_INT >= 28) packageInfo.longVersionCode else packageInfo.versionCode.toLong()
        if (app.optLong("versionCode", -1L) != versionCode) return false
        val startedAt = json.optLong("startedAtEpochMs", -1L)
        if (startedAt < packageInfo.lastUpdateTime) return false
        true
    }.getOrDefault(false)
}
