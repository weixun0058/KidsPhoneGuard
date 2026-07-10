package com.kidsphoneguard.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemSurfaceClassifierTest {

    @Test
    fun settingsFamilyMatchesControlledSubpackageButRejectsSubstringSpoof() {
        assertTrue(SystemSurfaceClassifier.isSettingsSurface("com.miui.securitycenter.power"))
        assertFalse(SystemSurfaceClassifier.isSettingsSurface("evil.com.miui.securitycenter.fake"))
    }

    @Test
    fun installerAndMarketClassificationsRemainSeparated() {
        assertTrue(SystemSurfaceClassifier.isInstallerOrMarketSurface("com.xiaomi.market"))
        assertTrue(SystemSurfaceClassifier.isAppMarketSurface("com.xiaomi.market"))
        assertFalse(SystemSurfaceClassifier.isPackageInstallerSurface("com.xiaomi.market"))

        assertTrue(SystemSurfaceClassifier.isInstallerOrMarketSurface("com.miui.packageinstaller"))
        assertTrue(SystemSurfaceClassifier.isPackageInstallerSurface("com.miui.packageinstaller"))
        assertFalse(SystemSurfaceClassifier.isAppMarketSurface("com.miui.packageinstaller"))
    }
}
