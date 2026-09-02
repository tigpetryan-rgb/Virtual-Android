package com.example.virtualandroid.display

import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object P3DisplayReportStore {
    private const val LATEST = "latest-p3-display.json"

    fun latestText(context: Context): String? = File(File(context.filesDir, "reports"), LATEST)
        .takeIf { it.isFile && it.length() > 0L }
        ?.readText()

    fun recordFirstFrame(context: Context, width: Int, height: Int): File {
        require(width > 0 && height > 0)
        val reportDir = File(context.filesDir, "reports").apply { mkdirs() }
        val p2 = File(reportDir, "latest-p2.json").takeIf { it.isFile }?.readBytes()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        val json = JSONObject().apply {
            put("format", 1)
            put("success", true)
            put("frameworkReadyInSameRun", true)
            put("frameReceivedInSameRun", true)
            put("transport", "RFB_3_8_RAW_LOOPBACK")
            put("width", width)
            put("height", height)
            put("p2ProofSha256", p2?.let(::sha256) ?: JSONObject.NULL)
            put("host", JSONObject().apply {
                put("manufacturer", Build.MANUFACTURER)
                put("model", Build.MODEL)
                put("device", Build.DEVICE)
                put("sdk", Build.VERSION.SDK_INT)
            })
        }
        val text = json.toString(2) + "\n"
        val out = File(reportDir, "p3-display-$stamp.json")
        out.writeText(text)
        File(reportDir, LATEST).writeText(text)
        return out
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
