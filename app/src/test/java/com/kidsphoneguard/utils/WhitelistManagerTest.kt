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
        assertFalse(WhitelistManager.isSelfApp("com.kidsphoneguard.fake"))
    }

    @Test
    fun `installer and app market classifications are separated`() {
        assertTrue(WhitelistManager.isInstallerOrMarket("com.xiaomi.market"))
        assertTrue(WhitelistManager.isAppMarket("com.xiaomi.market"))
        assertFalse(WhitelistManager.isPackageInstaller("com.xiaomi.market"))

        assertTrue(WhitelistManager.isInstallerOrMarket("com.miui.packageinstaller"))
        assertTrue(WhitelistManager.isPackageInstaller("com.miui.packageinstaller"))
        assertFalse(WhitelistManager.isAppMarket("com.miui.packageinstaller"))
    }
}
