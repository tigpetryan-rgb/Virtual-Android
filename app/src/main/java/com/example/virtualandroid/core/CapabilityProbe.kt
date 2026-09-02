package com.example.virtualandroid.core

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

class CapabilityProbe(private val context: Context) {
    fun run(): CapabilityReport {
        val am = context.getSystemService(ActivityManager::class.java)
        val mem = ActivityManager.MemoryInfo().also(am::getMemoryInfo)
        val feature = context.packageManager.hasSystemFeature(
            "android.software.virtualization_framework"
        )

        val manageVmGranted = context.checkSelfPermission(
            "android.permission.MANAGE_VIRTUAL_MACHINE"
        ) == PackageManager.PERMISSION_GRANTED

        return CapabilityReport(
            sdkInt = Build.VERSION.SDK_INT,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            abis = Build.SUPPORTED_ABIS.toList(),
            arm64 = Build.SUPPORTED_ABIS.contains("arm64-v8a"),
            logicalCpus = Runtime.getRuntime().availableProcessors(),
            pageSizeBytes = NativeProbe.pageSizeBytes(),
            totalRamMiB = mem.totalMem / (1024L * 1024L),
            availRamMiB = mem.availMem / (1024L * 1024L),
            memoryClassMiB = am.memoryClass,
            largeMemoryClassMiB = am.largeMemoryClass,
            lowRamDevice = am.isLowRamDevice,
            avfFeature = feature,
            manageVmPermissionGranted = manageVmGranted,
            kvmProbe = NativeProbe.probeKvm(),
            executableMemoryProbe = NativeProbe.probeExecutableMemory(),
            tunProbe = NativeProbe.probeDevice("/dev/tun"),
            vhostVsockProbe = NativeProbe.probeDevice("/dev/vhost-vsock"),
        )
    }
}
