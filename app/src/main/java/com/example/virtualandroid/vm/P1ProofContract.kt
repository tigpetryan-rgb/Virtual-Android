package com.example.virtualandroid.vm

import java.util.UUID

/** Constants shared by P1 execution and proof generation. */
object P1ProofContract {
    const val FORMAT = 3
    const val BACKEND = "TCG"
    const val TERMINAL_MARKER = "VA_P1_GUEST_OK: AArch64 Linux reached /init under QEMU TCG"

    private val runIdPattern = Regex("[A-Za-z0-9._:-]{8,128}")

    fun normalizeRunId(requested: String?): String {
        val candidate = requested?.trim().orEmpty()
        return if (runIdPattern.matches(candidate)) candidate else "p1-${UUID.randomUUID()}"
    }
}
