package com.kidsphoneguard.service.guard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UninstallGuardTest {

    @Test
    fun miuiTargetedDialogScan_isLimitedToXiaomiLauncher() {
        assertTrue(
            UninstallGuard.shouldUseMiuiTargetedDialogScan(
                isXiaomiFamilyDevice = true,
                windowPackageName = "com.miui.home",
                targetPackageName = "com.miui.home"
            )
        )
        assertFalse(
            UninstallGuard.shouldUseMiuiTargetedDialogScan(
                isXiaomiFamilyDevice = false,
                windowPackageName = "com.hihonor.android.launcher",
                targetPackageName = "com.hihonor.android.launcher"
            )
        )
        assertFalse(
            UninstallGuard.shouldUseMiuiTargetedDialogScan(
                isXiaomiFamilyDevice = true,
                windowPackageName = "com.android.settings",
                targetPackageName = "com.android.settings"
            )
        )
    }

    @Test
    fun shortcutMenuGeometry_matchesCapturedTargetIconBelowMenu() {
        assertTrue(
            UninstallGuard.isShortcutMenuAnchoredToTarget(
                menuLeft = 78,
                menuTop = 301,
                menuRight = 628,
                menuBottom = 461,
                targetLeft = 32,
                targetTop = 483,
                targetRight = 286,
                targetBottom = 758
            )
        )
    }

    @Test
    fun shortcutMenuGeometry_rejectsIconBehindMenuAndDifferentColumn() {
        assertFalse(
            UninstallGuard.isShortcutMenuAnchoredToTarget(
                menuLeft = 78,
                menuTop = 301,
                menuRight = 628,
                menuBottom = 461,
                targetLeft = 32,
                targetTop = 208,
                targetRight = 286,
                targetBottom = 483
            )
        )
        assertFalse(
            UninstallGuard.isShortcutMenuAnchoredToTarget(
                menuLeft = 78,
                menuTop = 301,
                menuRight = 628,
                menuBottom = 461,
                targetLeft = 794,
                targetTop = 483,
                targetRight = 1048,
                targetBottom = 758
            )
        )
    }
}
