// Reference contract only; move into app module when the native QEMU backend is added.
package com.example.virtualandroid.engine

data class VmConfig(
    val memoryMiB: Int,
    val vcpus: Int,
    val kernelPath: String,
    val initrdPath: String?,
    val systemDiskPath: String,
    val dataDiskPath: String,
)

interface VmEngine {
    fun start(config: VmConfig): Result<Unit>
    fun stop(): Result<Unit>
    fun pause(): Result<Unit>
    fun resume(): Result<Unit>
}
