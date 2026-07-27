package com.kidsphoneguard.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryCodeEngineTest {

    @Test
    fun generateCode_matchesCalculatorGoldenVector() {
        assertEquals(
            "19381938",
            RecoveryCodeEngine.generateCode(
                recoveryId = "7D4A-92FC-381B-6E20",
                recoveryDate = "2026-07-26",
                masterSecret = TEST_MASTER_SECRET
            )
        )
    }

    @Test
    fun recoveryId_isNormalizedAndFormattedForReading() {
        assertEquals(
            "7D4A92FC381B6E20",
            RecoveryCodeEngine.normalizeRecoveryId("7d4a-92fc 381b-6e20")
        )
        assertEquals(
            "7D4A-92FC-381B-6E20",
            RecoveryCodeEngine.formatRecoveryId("7d4a92fc381b6e20")
        )
    }

    @Test
    fun code_changesWithDeviceOrDate() {
        val baseline = RecoveryCodeEngine.generateCode(
            "7D4A92FC381B6E20",
            "2026-07-26",
            TEST_MASTER_SECRET
        )

        assertNotEquals(
            baseline,
            RecoveryCodeEngine.generateCode(
                "7D4A92FC381B6E21",
                "2026-07-26",
                TEST_MASTER_SECRET
            )
        )
        assertNotEquals(
            baseline,
            RecoveryCodeEngine.generateCode(
                "7D4A92FC381B6E20",
                "2026-07-27",
                TEST_MASTER_SECRET
            )
        )
    }

    @Test
    fun verification_acceptsFormattedCorrectCodeOnly() {
        assertTrue(
            RecoveryCodeEngine.isValidCode(
                recoveryId = "7D4A-92FC-381B-6E20",
                recoveryDate = "2026-07-26",
                enteredCode = "1938-1938",
                masterSecret = TEST_MASTER_SECRET
            )
        )
        assertFalse(
            RecoveryCodeEngine.isValidCode(
                recoveryId = "7D4A-92FC-381B-6E20",
                recoveryDate = "2026-07-26",
                enteredCode = "19381939",
                masterSecret = TEST_MASTER_SECRET
            )
        )
        assertFalse(
            RecoveryCodeEngine.isValidCode(
                recoveryId = "7D4A-92FC-381B-6E20",
                recoveryDate = "2026-07-26",
                enteredCode = "1938193",
                masterSecret = TEST_MASTER_SECRET
            )
        )
    }

    private companion object {
        const val TEST_MASTER_SECRET =
            "45250811B5D0C9934D02ADDC38EA65B745D05A73F85521C6C22E7B6BFE89881E"
    }
}
