package com.example.virtualandroid.core

data class CapabilityReport(
    val sdkInt: Int,
    val manufacturer: String,
    val model: String,
    val abis: List<String>,
    val arm64: Boolean,
    val logicalCpus: Int,
    val pageSizeBytes: Long,
    val totalRamMiB: Long,
    val availRamMiB: Long,
    val memoryClassMiB: Int,
    val largeMemoryClassMiB: Int,
    val lowRamDevice: Boolean,
    val avfFeature: Boolean,
    val manageVmPermissionGranted: Boolean,
    val kvmProbe: String,
    val executableMemoryProbe: String,
    val tunProbe: String,
    val vhostVsockProbe: String,
) {
    fun toPrettyText(): String = buildString {
        appendLine("=== HOST ===")
        appendLine("sdk=$sdkInt")
        appendLine("device=$manufacturer $model")
        appendLine("abis=${abis.joinToString()}")
        appendLine("arm64=$arm64")
        appendLine("logicalCpus=$logicalCpus")
        appendLine("pageSizeBytes=$pageSizeBytes")
        appendLine("totalRamMiB=$totalRamMiB")
        appendLine("availRamMiB=$availRamMiB")
        appendLine("memoryClassMiB=$memoryClassMiB")
        appendLine("largeMemoryClassMiB=$largeMemoryClassMiB")
        appendLine("lowRamDevice=$lowRamDevice")
        appendLine()
        appendLine("=== VIRTUALIZATION ===")
        appendLine("avfFeature=$avfFeature")
        appendLine("MANAGE_VIRTUAL_MACHINE granted=$manageVmPermissionGranted")
        appendLine("kvm=$kvmProbe")
        appendLine("execmem=$executableMemoryProbe")
        appendLine("tun=$tunProbe")
        appendLine("vhost-vsock=$vhostVsockProbe")
    }
}
