package com.example.virtualandroid

import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.virtualandroid.core.CapabilityProbe
import com.example.virtualandroid.core.EngineDecision
import com.example.virtualandroid.core.EngineMode
import com.example.virtualandroid.core.EngineSelector
import com.example.virtualandroid.databinding.ActivityMainBinding
import com.example.virtualandroid.display.GuestDisplayView
import com.example.virtualandroid.display.P3DisplayReportStore
import com.example.virtualandroid.vm.P1ReportStore
import com.example.virtualandroid.vm.P2ReportStore
import com.example.virtualandroid.vm.VmClient
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), VmClient.Listener, GuestDisplayView.Listener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var vmClient: VmClient
    private val worker = Executors.newSingleThreadExecutor()

    @Volatile private var decision: EngineDecision? = null
    @Volatile private var vmConnected = false
    private var autoP1Requested = false
    private var autoP1Started = false
    private var autoP2Requested = false
    private var autoP2Started = false
    private var autoP3Requested = false
    private var autoP3Started = false
    private var p3FrameworkReady = false
    private var p3FirstFrame: Pair<Int, Int>? = null
    private var p3ProofWritten = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        vmClient = VmClient(this, this)
        binding.guestDisplay.setListener(this)
        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        autoP1Requested = debuggable && intent.getBooleanExtra("auto_p1", false)
        autoP2Requested = debuggable && intent.getBooleanExtra("auto_p2", false)
        autoP3Requested = debuggable && intent.getBooleanExtra("auto_p3", false)

        binding.runProbe.setOnClickListener { runProbe() }
        binding.startP1.setOnClickListener {
            val d = decision
            if (d == null) appendLog("Run the capability probe first") else startP1(d)
        }
        binding.startP2.setOnClickListener {
            val d = decision
            if (d == null) appendLog("Run the capability probe first") else startP2(d)
        }
        binding.startP3.setOnClickListener {
            val d = decision
            if (d == null) appendLog("Run the capability probe first") else startP3(d)
        }
        binding.stopVm.setOnClickListener {
            binding.guestDisplay.disconnect()
            vmClient.stop()
        }
        binding.copyP1Proof.setOnClickListener { copyLatestP1Proof() }
        binding.copyP2Proof.setOnClickListener { copyLatestP2Proof() }
        binding.copyP3Proof.setOnClickListener { copyLatestP3Proof() }

        runProbe()
    }

    override fun onStart() {
        super.onStart()
        vmClient.bind()
    }

    override fun onStop() {
        binding.guestDisplay.disconnect()
        vmClient.unbind()
        super.onStop()
    }

    private fun runProbe() {
        binding.runProbe.isEnabled = false
        binding.report.text = "Probing…"

        worker.execute {
            val report = CapabilityProbe(this).run()
            val d = EngineSelector.select(report)
            decision = d
            val text = buildString {
                appendLine(report.toPrettyText())
                appendLine()
                appendLine("=== ENGINE DECISION ===")
                appendLine("mode=${d.mode}")
                appendLine("reason=${d.reason}")
                appendLine("recommendedGuestRamMiB=${d.recommendedGuestRamMiB}")
                appendLine("recommendedVcpus=${d.recommendedVcpus}")
            }
            runOnUiThread {
                binding.report.text = text
                binding.runProbe.isEnabled = true
                maybeAutoStartP1()
                maybeAutoStartP2()
                maybeAutoStartP3()
            }
        }
    }

    override fun onConnected() = runOnUiThread {
        vmConnected = true
        binding.startP1.isEnabled = true
        binding.stopVm.isEnabled = true
        binding.startP2.isEnabled = true
        binding.startP3.isEnabled = true
        appendLog("VM service connected (:vm process)")
        maybeAutoStartP1()
        maybeAutoStartP2()
        maybeAutoStartP3()
    }

    override fun onDisconnected() = runOnUiThread {
        vmConnected = false
        binding.startP1.isEnabled = false
        binding.stopVm.isEnabled = false
        binding.startP2.isEnabled = false
        binding.startP3.isEnabled = false
        binding.guestDisplay.disconnect()
        appendLog("VM service disconnected")
    }

    override fun onState(state: String, detail: String) = runOnUiThread {
        binding.vmState.text = "VM: $state — $detail"
        if (state == "RUNNING" && detail.contains("P3 Android framework ready")) {
            p3FrameworkReady = true
            maybeRecordP3Proof()
        }
    }

    override fun onLog(line: String) = runOnUiThread { appendLog(line) }

    override fun onDisplayStatus(text: String) = runOnUiThread {
        binding.displayStatus.text = "P3 display: $text"
    }

    override fun onFirstFrame(width: Int, height: Int) = runOnUiThread {
        p3FirstFrame = width to height
        binding.displayStatus.text = "P3 display: first frame ${width}x$height; waiting for current framework marker"
        appendLog("P3 first framebuffer received: ${width}x$height")
        maybeRecordP3Proof()
    }

    private fun maybeRecordP3Proof() {
        if (p3ProofWritten || !p3FrameworkReady) return
        val (width, height) = p3FirstFrame ?: return
        val file = runCatching { P3DisplayReportStore.recordFirstFrame(this, width, height) }.getOrNull()
        p3ProofWritten = file != null
        binding.displayStatus.text = if (file != null) {
            "P3 display: framework + ${width}x$height framebuffer — PASS"
        } else {
            "P3 display: proof write failed"
        }
        appendLog("P3 interactive acceptance: ${if (file != null) "PASS" else "FAIL"}; proof=${file?.absolutePath ?: "none"}")
    }

    private fun startP1(d: EngineDecision) {
        if (d.mode == EngineMode.UNSUPPORTED) {
            appendLog("P1 not started: ${d.reason}")
            return
        }
        appendLog("Requesting P1 guest: ${d.recommendedGuestRamMiB} MiB, ${d.recommendedVcpus} vCPU")
        vmClient.startP1(d.recommendedGuestRamMiB.coerceAtMost(2048), d.recommendedVcpus)
    }

    private fun startP2(d: EngineDecision) {
        if (d.mode == EngineMode.UNSUPPORTED) {
            appendLog("P2 not started: ${d.reason}")
            return
        }
        val ram = d.recommendedGuestRamMiB.coerceIn(1536, 4096)
        val cpus = d.recommendedVcpus.coerceIn(1, 8)
        appendLog("Requesting P2 AOSP guest: $ram MiB, $cpus vCPU")
        vmClient.startP2(ram, cpus)
    }

    private fun startP3(d: EngineDecision) {
        if (d.mode == EngineMode.UNSUPPORTED) {
            appendLog("P3 not started: ${d.reason}")
            return
        }
        if (!P2ReportStore.latestPassed(this)) {
            appendLog("P3 not started: latest P2 framework proof is not PASS")
            return
        }
        val ram = d.recommendedGuestRamMiB.coerceIn(1536, 4096)
        val cpus = d.recommendedVcpus.coerceIn(1, 8)
        p3FrameworkReady = false
        p3FirstFrame = null
        p3ProofWritten = false
        appendLog("Requesting P3 interactive AOSP: $ram MiB, $cpus vCPU, RFB loopback")
        vmClient.startP3(ram, cpus)
        binding.guestDisplay.connect()
    }

    private fun maybeAutoStartP1() {
        if (!autoP1Requested || autoP1Started || !vmConnected) return
        val d = decision ?: return
        autoP1Started = true
        appendLog("debug auto_p1 requested")
        startP1(d)
    }

    private fun maybeAutoStartP2() {
        if (!autoP2Requested || autoP2Started || !vmConnected) return
        val d = decision ?: return
        autoP2Started = true
        appendLog("debug auto_p2 requested")
        startP2(d)
    }

    private fun maybeAutoStartP3() {
        if (!autoP3Requested || autoP3Started || !vmConnected) return
        val d = decision ?: return
        autoP3Started = true
        appendLog("debug auto_p3 requested")
        startP3(d)
    }

    private fun copyLatestP1Proof() = copyProof(
        label = "Virtual Android P1 proof",
        text = P1ReportStore.latestText(this),
        missing = "No P1 report yet",
    )

    private fun copyLatestP2Proof() = copyProof(
        label = "Virtual Android P2 proof",
        text = P2ReportStore.latestText(this),
        missing = "No P2 report yet",
    )

    private fun copyLatestP3Proof() = copyProof(
        label = "Virtual Android P3 display proof",
        text = P3DisplayReportStore.latestText(this),
        missing = "No P3 display report yet",
    )

    private fun copyProof(label: String, text: String?, missing: String) {
        if (text == null) {
            Toast.makeText(this, missing, Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(this, "$label copied", Toast.LENGTH_SHORT).show()
    }

    private fun appendLog(line: String) {
        val old = binding.vmLog.text?.toString().orEmpty()
        val merged = if (old.isEmpty()) line else "$old\n$line"
        binding.vmLog.text = merged.takeLast(32_000)
        binding.logScroll.post { binding.logScroll.fullScroll(android.view.View.FOCUS_DOWN) }
    }

    override fun onDestroy() {
        binding.guestDisplay.setListener(null)
        worker.shutdownNow()
        super.onDestroy()
    }
}
