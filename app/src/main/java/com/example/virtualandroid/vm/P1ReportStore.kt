package com.example.virtualandroid.vm

import android.content.Context
import java.io.File
import org.json.JSONObject

object P1ReportStore {
    private const val LATEST = "latest-p1.json"

    fun reportDir(context: Context): File = File(context.filesDir, "reports").apply { mkdirs() }

    fun latestFile(context: Context): File = File(reportDir(context), LATEST)

    fun latestText(context: Context): String? = latestFile(context)
        .takeIf { it.isFile && it.length() > 0L }
        ?.readText()

    fun latestPassed(context: Context): Boolean = runCatching {
        JSONObject(latestText(context) ?: return false).optBoolean("success", false)
    }.getOrDefault(false)
}
