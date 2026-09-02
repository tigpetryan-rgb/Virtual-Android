package com.example.virtualandroid.vm

import java.io.File

data class P2GuestArtifacts(
    val kernel: File,
    val combinedRamdisk: File,
    val systemQemu: File,
    val userdata: File,
    val bundleManifestSha256: String,
    val bundleBytes: Long,
)
