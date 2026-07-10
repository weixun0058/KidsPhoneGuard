package com.kidsphoneguard.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class PasswordManagerMigrationTest {

    @Test
    fun onlyLegacyPassword_isMigratedInsteadOfReset() {
        assertEquals(
            LegacyPasswordMigrationAction.MIGRATE,
            PasswordManager.legacyPasswordMigrationAction(false, "legacy-secret")
        )
    }

    @Test
    fun hashAndLegacyPassword_removesOnlyRedundantPlaintext() {
        assertEquals(
            LegacyPasswordMigrationAction.REMOVE_REDUNDANT,
            PasswordManager.legacyPasswordMigrationAction(true, "legacy-secret")
        )
    }

    @Test
    fun noLegacyPassword_requiresNoMigration() {
        assertEquals(
            LegacyPasswordMigrationAction.NONE,
            PasswordManager.legacyPasswordMigrationAction(false, null)
        )
    }
}
