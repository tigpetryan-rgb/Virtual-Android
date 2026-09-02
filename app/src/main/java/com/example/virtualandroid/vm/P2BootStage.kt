package com.example.virtualandroid.vm

enum class P2BootStage {
    STARTING,
    LINUX,
    ANDROID_INIT,
    SERVICEMANAGER,
    ZYGOTE,
    FRAMEWORK_READY,
}

/** Pure parser so milestone classification can be host-JVM tested. */
object P2BootStageClassifier {
    fun advance(current: P2BootStage, line: String): P2BootStage {
        val observed = when {
            line.contains("VA_P2_FRAMEWORK_OK") -> P2BootStage.FRAMEWORK_READY
            line.contains("VA_P2_ZYGOTE_OK") -> P2BootStage.ZYGOTE
            line.contains("VA_P2_SERVICEMANAGER_OK") -> P2BootStage.SERVICEMANAGER
            line.contains("VA_P2_ANDROID_INIT_OK") -> P2BootStage.ANDROID_INIT
            line.contains("Booting Linux") -> P2BootStage.LINUX
            else -> current
        }
        return if (observed.ordinal > current.ordinal) observed else current
    }
}
