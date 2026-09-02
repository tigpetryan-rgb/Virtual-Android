package com.example.virtualandroid.core

object NativeProbe {
    init {
        System.loadLibrary("vaprobes")
    }

    external fun probeKvm(): String
    external fun pageSizeBytes(): Long
    external fun probeExecutableMemory(): String
    external fun probeDevice(path: String): String
}
