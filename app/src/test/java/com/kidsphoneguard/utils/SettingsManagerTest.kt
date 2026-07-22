package com.kidsphoneguard.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsManagerTest {

    @Test
    fun booleanSettings_defaultToFalse() {
        val manager = SettingsManager(FakeSettingsStorage())

        assertFalse(manager.isGlobalLockEnabled())
        assertFalse(manager.isGlobalUnlockEnabled())
        assertFalse(manager.isBrandSetupConfirmed())
        assertFalse(manager.isWeChatFinderBlockEnabled())
    }

    @Test
    fun booleanSettings_roundTripIndependently() {
        val manager = SettingsManager(FakeSettingsStorage())

        manager.setGlobalLock(true)
        manager.setGlobalUnlock(true)
        manager.setBrandSetupConfirmed(true)
        manager.setWeChatFinderBlockEnabled(true)

        assertTrue(manager.isGlobalLockEnabled())
        assertTrue(manager.isGlobalUnlockEnabled())
        assertTrue(manager.isBrandSetupConfirmed())
        assertTrue(manager.isWeChatFinderBlockEnabled())

        manager.setGlobalLock(false)
        assertFalse(manager.isGlobalLockEnabled())
        assertTrue(manager.isGlobalUnlockEnabled())
    }

    @Test
    fun setupSettingsAccess_expiresAtStrictDeadline() {
        var now = 1_000L
        val manager = SettingsManager(
            storage = FakeSettingsStorage(),
            currentTimeMillis = { now }
        )

        manager.allowSetupSettingsAccess(durationMillis = 500L)

        assertTrue(manager.isSetupSettingsAccessAllowed())
        now = 1_499L
        assertTrue(manager.isSetupSettingsAccessAllowed())
        now = 1_500L
        assertFalse(manager.isSetupSettingsAccessAllowed())
    }

    @Test
    fun clearSetupSettingsAccess_revokesAllowanceImmediately() {
        val manager = SettingsManager(
            storage = FakeSettingsStorage(),
            currentTimeMillis = { 1_000L }
        )
        manager.allowSetupSettingsAccess(durationMillis = 500L)

        manager.clearSetupSettingsAccess()

        assertFalse(manager.isSetupSettingsAccessAllowed())
    }
}

private class FakeSettingsStorage : SettingsStorage {
    private val values = mutableMapOf<String, Any>()

    override fun putBoolean(key: String, value: Boolean) {
        values[key] = value
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        values[key] as? Boolean ?: defaultValue

    override fun putLong(key: String, value: Long) {
        values[key] = value
    }

    override fun getLong(key: String, defaultValue: Long): Long =
        values[key] as? Long ?: defaultValue

    override fun remove(key: String) {
        values.remove(key)
    }
}
