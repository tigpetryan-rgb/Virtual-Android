package com.example.virtualandroid.core

enum class EngineMode {
    AVF_PRIVILEGED,
    SOFTWARE_TCG,
    UNSUPPORTED
}

data class EngineDecision(
    val mode: EngineMode,
    val reason: String,
    val recommendedGuestRamMiB: Int,
    val recommendedVcpus: Int,
)

object EngineSelector {
    fun select(r: CapabilityReport): EngineDecision {
        val ram = recommendRam(r)
        val cpus = (r.logicalCpus / 2).coerceIn(1, 4)

        if (r.avfFeature && r.manageVmPermissionGranted) {
            return EngineDecision(
                EngineMode.AVF_PRIVILEGED,
                "AVF is present and MANAGE_VIRTUAL_MACHINE is granted. This is a dev/preinstalled path, not a normal universal APK path.",
                ram,
                cpus,
            )
        }

        if (r.arm64 && r.executableMemoryProbe.startsWith("OK")) {
            return EngineDecision(
                EngineMode.SOFTWARE_TCG,
                "Stock-app fallback: run an ARM64 guest with a user-space system emulator. /dev/kvm is not required.",
                ram,
                cpus,
            )
        }

        return EngineDecision(
            EngineMode.UNSUPPORTED,
            "No usable ARM64 + executable-memory path detected for the planned TCG backend.",
            0,
            0,
        )
    }

    private fun recommendRam(r: CapabilityReport): Int {
        // Conservative prototype policy: never promise RAM the OS may reclaim.
        val byTotal = (r.totalRamMiB / 4).toInt()
        val byAvailable = (r.availRamMiB / 2).toInt()
        return minOf(byTotal, byAvailable, 3072).coerceIn(512, 3072)
    }
}
