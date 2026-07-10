package com.kidsphoneguard.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhitelistManagerTest {

    @Test
    fun `whitelist match accepts exact package`() {
        assertTrue(WhitelistManager.isInWhitelist("com.android.settings"))
    }

    @Test
    fun `whitelist match accepts controlled subpackage`() {
        assertTrue(WhitelistManager.isInWhitelist("com.google.android.inputmethod.latin"))
    }

    @Test
    fun `runtime discovered ime is exempt without legacy prefix`() {
        assertTrue(
            WhitelistManager.matchesWhitelist(
                packageName = "com.samsung.android.honeyboard",
                discoveredInputMethodPackages = setOf("com.samsung.android.honeyboard"),
                allowLegacyPrefixFallback = false
            )
        )
    }

    @Test
    fun `legacy ime prefix is only used while cache is unavailable`() {
        assertTrue(
            WhitelistManager.matchesWhitelist(
                packageName = "com.google.android.inputmethod.latin",
                discoveredInputMethodPackages = emptySet(),
                allowLegacyPrefixFallback = true
            )
        )
        assertFalse(
            WhitelistManager.matchesWhitelist(
                packageName = "com.google.android.inputmethod.latin",
                discoveredInputMethodPackages = emptySet(),
                allowLegacyPrefixFallback = false
            )
        )
    }

    @Test
    fun `whitelist match rejects substring spoofing`() {
        assertFalse(WhitelistManager.isInWhitelist("evil.com.android.settings.fake"))
    }

    @Test
    fun `self app match only accepts exact package family process suffix`() {
        assertTrue(WhitelistManager.isSelfApp("com.kidsphoneguard"))
        assertFalse(WhitelistManager.isSelfApp("com.kidsphoneguard.fake"))
    }

}
