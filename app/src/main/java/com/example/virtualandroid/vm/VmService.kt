package com.example.virtualandroid.vm

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.RemoteCallbackList
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class VmService : Service() {
    companion object {
        private const val TAG = "VA-P1"
        private const val P1_TIMEOUT_SECONDS = 120L
        private const val P2_TIMEOUT_SECONDS = 1800L
        private const val P3_TIMEOUT_SECONDS = 3600L
    }

    private val callbacks = RemoteCallbackList<IVmCallback>()
    private val executor = Executors.newSingleThreadExecutor()
    private val controlExecutor = Executors.newSingleThreadExecutor()
    private val callbackLock = Any()
    private val runGeneration = AtomicLong(0)
    private lateinit var launcher: QemuProcessLauncher

    @Volatile
    private var state = VmState.IDLE

    override fun onCreate() {
        super.onCreate()
        launcher = QemuProcessLauncher(this)
    }

    private val binder = object : IVmService.Stub() {
        override fun registerCallback(callback: IVmCallback?) {
            if (callback != null) callbacks.register(callback)
            callback?.onStateChanged(state.name, "connected")
        }

        override fun unregisterCallback(callback: IVmCallback?) {
            if (callback != null) callbacks.unregister(callback)
        }

        override fun startP1Guest(memoryMiB: Int, vcpus: Int) {
            if (state in setOf(VmState.PREPARING, VmState.STARTING, VmState.RUNNING, VmState.STOPPING)) {
                emitLog("Start ignored: VM is already $state")
                return
            }
            val token = runGeneration.incrementAndGet()
            executor.execute { startP1(token, memoryMiB, vcpus) }
        }

        override fun startP2Guest(memoryMiB: Int, vcpus: Int) {
            if (state in setOf(VmState.PREPARING, VmState.STARTING, VmState.RUNNING, VmState.STOPPING)) {
                emitLog("P2 start ignored: VM is already $state")
                return
            }
            if (!P1ReportStore.latestPassed(this@VmService)) {
                emitLog("P2 blocked: latest P1 proof is not PASS")
                return
            }
            val token = runGeneration.incrementAndGet()
            executor.execute { startP2(token, memoryMiB, vcpus) }
        }

        override fun startP3Guest(memoryMiB: Int, vcpus: Int) {
            if (state in setOf(VmState.PREPARING, VmState.STARTING, VmState.RUNNING, VmState.STOPPING)) {
                emitLog("P3 start ignored: VM is already $state")
                return
            }
            if (!P2ReportStore.latestPassed(this@VmService)) {
                emitLog("P3 blocked: latest P2 framework proof is not PASS")
                return
            }
            val token = runGeneration.incrementAndGet()
            executor.execute { startP3(token, memoryMiB, vcpus) }
        }

        override fun stopVm() {
            // Invalidates PREPARING/STARTING work before touching the process.
            runGeneration.incrementAndGet()
            controlExecutor.execute {
                if (state == VmState.IDLE || state == VmState.STOPPED) {
                    emitLog("Stop ignored: VM is $state")
                    return@execute
                }
                setState(VmState.STOPPING, "stop requested")
                launcher.stop(::emitLog)
                    .onFailure { emitLog("stop failed: ${it.message}") }
                setState(VmState.STOPPED, "stopped")
            }
        }

        override fun getState(): String = state.name
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun isCurrent(token: Long): Boolean = runGeneration.get() == token

    private fun startP1(token: Long, memoryMiB: Int, vcpus: Int) {
        val report = P1RunReport(memoryMiB, vcpus)
        fun persistReport() {
            runCatching { report.write(this) }
                .onSuccess { emitLog("P1 report: ${it.absolutePath}") }
                .onFailure { emitLog("P1 report write failed: ${it.message}") }
        }
        fun cancelled(stage: String): Boolean {
            if (isCurrent(token)) return false
            report.onFailure("cancelled during $stage")
            emitLog("P1 run cancelled during $stage")
            persistReport()
            return true
        }

        setState(VmState.PREPARING, "installing guest assets")
        val artifacts = GuestAssetInstaller.installP1(this).getOrElse {
            val detail = "guest asset error: ${it.message}"
            report.onFailure(detail)
            if (isCurrent(token)) fail(detail)
            persistReport()
            return
        }
        if (cancelled("guest asset preparation")) return

        emitLog("Running QEMU runtime verification + host preflight (--version)")
        val preflight = launcher.preflight(::emitLog).getOrElse {
            val detail = "QEMU preflight failed: ${it.message}"
            report.onFailure(detail)
            if (isCurrent(token)) fail(detail)
            persistReport()
            return
        }
        report.onPreflight(preflight)
        emitLog("QEMU preflight OK: ${preflight.versionLine} via ${preflight.launchPath}")
        if (cancelled("QEMU preflight")) return

        val args = QemuArgs.p1(
            artifacts.kernel,
            artifacts.initramfs,
            memoryMiB,
            vcpus,
        )

        setState(VmState.STARTING, "starting QEMU/TCG (watchdog=${P1_TIMEOUT_SECONDS}s)")
        val stopAfterMarkerIssued = AtomicBoolean(false)
        val result = launcher.run(args, timeoutSeconds = P1_TIMEOUT_SECONDS) { line ->
            report.onGuestLog(line)
            if (line.contains("VA_P1_GUEST_OK") && stopAfterMarkerIssued.compareAndSet(false, true)) {
                if (isCurrent(token)) setState(VmState.RUNNING, "P1 guest boot marker received — PASS")
                controlExecutor.execute {
                    Thread.sleep(250)
                    launcher.stop(::emitLog)
                        .onFailure { emitLog("auto-stop failed: ${it.message}") }
                }
            }
            emitLog(line)
        }

        result.onSuccess { exit ->
            report.onExit(exit.exitCode, exit.launchPath)
            if (!isCurrent(token)) {
                report.onFailure("cancelled by user")
            } else if (report.markerSeen()) {
                setState(VmState.STOPPED, "P1 PASS; QEMU exited code=${exit.exitCode}")
            } else {
                val detail = "QEMU exited before guest boot marker; code=${exit.exitCode}"
                report.onFailure(detail)
                fail(detail)
            }
        }.onFailure {
            val detail = if (isCurrent(token)) {
                "QEMU launch failed: ${it.message}"
            } else {
                "cancelled by user: ${it.message}"
            }
            report.onFailure(detail)
            if (isCurrent(token)) fail(detail)
        }
        persistReport()
    }

    private fun startP2(token: Long, memoryMiB: Int, vcpus: Int) {
        val report = P2RunReport(memoryMiB, vcpus)
        fun persistReport() {
            runCatching { report.write(this) }
                .onSuccess { emitLog("P2 report: ${it.absolutePath}") }
                .onFailure { emitLog("P2 report write failed: ${it.message}") }
        }
        fun cancelled(stage: String): Boolean {
            if (isCurrent(token)) return false
            report.onFailure("cancelled during $stage")
            emitLog("P2 run cancelled during $stage")
            persistReport()
            return true
        }

        setState(VmState.PREPARING, "verifying P2 AOSP bundle")
        val artifacts = P2GuestInstaller.verify(this).getOrElse {
            val detail = "P2 artifact error: ${it.message}"
            report.onFailure(detail)
            if (isCurrent(token)) fail(detail)
            persistReport()
            return
        }
        emitLog("P2 bundle verified: bytes=${artifacts.bundleBytes} manifestSha256=${artifacts.bundleManifestSha256}")
        if (cancelled("P2 bundle verification")) return

        val preflight = launcher.preflight(::emitLog).getOrElse {
            val detail = "QEMU preflight failed: ${it.message}"
            report.onFailure(detail)
            if (isCurrent(token)) fail(detail)
            persistReport()
            return
        }
        report.onPreflight(preflight, artifacts)
        if (cancelled("QEMU preflight")) return

        val args = P2QemuArgs.framework(
            artifacts,
            memoryMiB.coerceAtLeast(1536),
            vcpus,
        )
        setState(VmState.STARTING, "starting P2 AOSP/QEMU (watchdog=${P2_TIMEOUT_SECONDS}s)")
        val stopAfterMarkerIssued = AtomicBoolean(false)
        var lastReportedStage = P2BootStage.STARTING
        val result = launcher.run(args, timeoutSeconds = P2_TIMEOUT_SECONDS) { line ->
            val stage = report.onGuestLog(line)
            Log.i("VA-P2", line)
            if (stage != lastReportedStage) {
                lastReportedStage = stage
                emitLog("P2 boot stage -> ${stage.name}")
                if (isCurrent(token) && stage.ordinal >= P2BootStage.ANDROID_INIT.ordinal) {
                    setState(VmState.RUNNING, "P2 stage=${stage.name}")
                }
            }
            if (stage == P2BootStage.FRAMEWORK_READY && stopAfterMarkerIssued.compareAndSet(false, true)) {
                if (isCurrent(token)) setState(VmState.RUNNING, "P2 framework boot marker received — PASS")
                controlExecutor.execute {
                    Thread.sleep(500)
                    launcher.stop(::emitLog)
                        .onFailure { emitLog("P2 auto-stop failed: ${it.message}") }
                }
            }
            emitLog(line)
        }

        result.onSuccess { exit ->
            report.onExit(exit.exitCode, exit.launchPath)
            if (!isCurrent(token)) {
                report.onFailure("cancelled by user")
            } else if (report.frameworkReady()) {
                setState(VmState.STOPPED, "P2 PASS; sys.boot_completed=1; QEMU exited code=${exit.exitCode}")
            } else {
                val detail = "P2 QEMU exited before framework marker; code=${exit.exitCode}; lastStage=${report.currentStage().name}"
                report.onFailure(detail)
                fail(detail)
            }
        }.onFailure {
            val detail = if (isCurrent(token)) {
                "P2 QEMU failed: ${it.message}"
            } else {
                "P2 cancelled by user: ${it.message}"
            }
            report.onFailure(detail)
            if (isCurrent(token)) fail(detail)
        }
        persistReport()
    }

    private fun startP3(token: Long, memoryMiB: Int, vcpus: Int) {
        fun cancelled(stage: String): Boolean {
            if (isCurrent(token)) return false
            emitLog("P3 run cancelled during $stage")
            return true
        }

        setState(VmState.PREPARING, "verifying P3 AOSP bundle")
        val artifacts = P2GuestInstaller.verify(this).getOrElse {
            val detail = "P3 artifact error: ${it.message}"
            if (isCurrent(token)) fail(detail)
            return
        }
        if (cancelled("bundle verification")) return

        val preflight = launcher.preflight(requireVnc = true, onLog = ::emitLog).getOrElse {
            val detail = "P3 QEMU/VNC preflight failed: ${it.message}"
            if (isCurrent(token)) fail(detail)
            return
        }
        emitLog("P3 display backend verified: vnc=${preflight.vncAvailable} port=${P3QemuArgs.VNC_PORT}")
        if (cancelled("QEMU/VNC preflight")) return

        val args = P3QemuArgs.interactive(
            artifacts,
            memoryMiB.coerceAtLeast(1536),
            vcpus,
        )
        setState(
            VmState.STARTING,
            "starting interactive AOSP; RFB=127.0.0.1:${P3QemuArgs.VNC_PORT}; watchdog=${P3_TIMEOUT_SECONDS}s",
        )
        var bootStage = P2BootStage.STARTING
        val result = launcher.run(args, timeoutSeconds = P3_TIMEOUT_SECONDS) { line ->
            val next = P2BootStageClassifier.advance(bootStage, line)
            if (next != bootStage) {
                bootStage = next
                emitLog("P3 boot stage -> ${next.name}")
                if (next == P2BootStage.FRAMEWORK_READY && isCurrent(token)) {
                    setState(VmState.RUNNING, "P3 Android framework ready; interactive display active")
                }
            }
            Log.i("VA-P3", line)
            emitLog(line)
        }

        result.onSuccess { exit ->
            if (!isCurrent(token)) {
                emitLog("P3 stopped by user; qemu exit=${exit.exitCode}")
            } else {
                fail("P3 QEMU exited unexpectedly; code=${exit.exitCode}; lastStage=${bootStage.name}")
            }
        }.onFailure {
            if (isCurrent(token)) fail("P3 QEMU failed: ${it.message}; lastStage=${bootStage.name}")
        }
    }

    private fun fail(detail: String) {
        emitLog(detail)
        setState(VmState.FAILED, detail)
    }

    private fun setState(newState: VmState, detail: String) {
        state = newState
        synchronized(callbackLock) {
            val n = callbacks.beginBroadcast()
            try {
                for (i in 0 until n) {
                    try {
                        callbacks.getBroadcastItem(i).onStateChanged(newState.name, detail)
                    } catch (_: Exception) {
                    }
                }
            } finally {
                callbacks.finishBroadcast()
            }
        }
    }

    private fun emitLog(line: String) {
        Log.i(TAG, line)
        synchronized(callbackLock) {
            val n = callbacks.beginBroadcast()
            try {
                for (i in 0 until n) {
                    try {
                        callbacks.getBroadcastItem(i).onLogLine(line)
                    } catch (_: Exception) {
                    }
                }
            } finally {
                callbacks.finishBroadcast()
            }
        }
    }

    override fun onDestroy() {
        runGeneration.incrementAndGet()
        launcher.stop(::emitLog)
        executor.shutdownNow()
        controlExecutor.shutdownNow()
        callbacks.kill()
        super.onDestroy()
    }
}
