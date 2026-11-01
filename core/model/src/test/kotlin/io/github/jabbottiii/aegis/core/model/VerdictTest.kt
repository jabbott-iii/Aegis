package io.github.jabbottiii.aegis.core.model

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class VerdictTest {
    @Test
    fun `only stalkerware verdicts require the safety flow`() {
        val safetyFlow = Verdict.entries.filter { it.requiresSafetyFlow }

        assertEquals(listOf(Verdict.STALKERWARE, Verdict.STALKERWARE_POSSIBLE), safetyFlow)
    }
}
