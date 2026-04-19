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
    fun `whitelist match rejects substring spoofing`() {
        assertFalse(WhitelistManager.isInWhitelist("evil.com.android.settings.fake"))
    }

    @Test
    fun `self app match only accepts exact package family process suffix`() {
        assertTrue(WhitelistManager.isSelfApp("com.kidsphoneguard"))
        assertTrue(WhitelistManager.isSelfApp("com.kidsphoneguard:observer"))
        assertFalse(WhitelistManager.isSelfApp("com.kidsphoneguard.fake"))
    }
}
