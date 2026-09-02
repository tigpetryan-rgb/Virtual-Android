package com.example.virtualandroid.vm

import android.content.Context
import java.io.File

object P2ReportStore {
    private const val LATEST = "latest-p2.json"
    fun latestFile(context: Context): File = File(P1ReportStore.reportDir(context), LATEST)
    fun latestText(context: Context): String? = latestFile(context)
        .takeIf { it.isFile && it.length() > 0L }
        ?.readText()

    fun latestPassed(context: Context): Boolean = latestText(context)?.let { text ->
        runCatching { org.json.JSONObject(text).optBoolean("success", false) }.getOrDefault(false)
    } ?: false
}
