package com.kidsphoneguard.service

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kidsphoneguard.KidsPhoneGuardApp
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.FileInputStream

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class GuardRuntimeIntegrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    @After
    fun cleanUpRuntimeState() {
        DegradedLockManager.dismissLockScreen(context)
        GuardForegroundService.stop(context)
        SystemClock.sleep(300)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            alarmManager.cancelAll()
        }
        notificationManager.cancelAll()
    }

    @Test
    fun test01_applicationCreatesLowImportanceGuardChannel() {
        val channel = notificationManager.getNotificationChannel(
            KidsPhoneGuardApp.NOTIFICATION_CHANNEL_ID
        )

        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
        assertFalse(channel.canShowBadge())
    }

    @Test
    fun test02_watchdogSchedulingRegistersPackageAlarm() {
        GuardForegroundService.scheduleWatchdog(context, delayMillis = 60_000L)

        val alarmDump = executeShellCommand("dumpsys alarm")
        assertTrue(alarmDump.contains(context.packageName))
        assertTrue(alarmDump.contains(GuardForegroundService.ACTION_GUARD_WATCHDOG))
    }

    @Test
    fun test03_degradedLockAddsAndRemovesRealOverlayWindow() {
        executeShellCommand("input keyevent KEYCODE_WAKEUP")
        executeShellCommand("wm dismiss-keyguard")
        executeShellCommand(
            "appops set ${context.packageName} android:system_alert_window allow"
        )

        try {
            DegradedLockManager.showLockScreen(context)
            assertTrue(eventually { DegradedLockManager.isLockShowing() })

            DegradedLockManager.dismissLockScreen(context)
            assertTrue(eventually { !DegradedLockManager.isLockShowing() })
        } finally {
            executeShellCommand(
                "appops set ${context.packageName} android:system_alert_window deny"
            )
        }
    }

    @Test
    fun test04_foregroundServiceStartsNotificationAndWatchdog_thenStops() {
        executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")

        GuardForegroundService.start(context)

        assertTrue(
            eventually {
                notificationManager.activeNotifications.any {
                    it.id == GuardForegroundService.NOTIFICATION_ID
                }
            }
        )
        assertTrue(
            executeShellCommand("dumpsys activity services ${context.packageName}")
                .contains(GuardForegroundService::class.java.simpleName)
        )
        assertTrue(
            executeShellCommand("dumpsys alarm")
                .contains(GuardForegroundService.ACTION_GUARD_WATCHDOG)
        )

        GuardForegroundService.stop(context)
        assertTrue(
            eventually {
                notificationManager.activeNotifications.none {
                    it.id == GuardForegroundService.NOTIFICATION_ID
                }
            }
        )
    }

    private fun eventually(
        timeoutMs: Long = 5_000L,
        condition: () -> Boolean
    ): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        do {
            if (condition()) return true
            SystemClock.sleep(100L)
        } while (SystemClock.elapsedRealtime() < deadline)
        return condition()
    }

    private fun executeShellCommand(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand(command)
        return descriptor.use {
            FileInputStream(it.fileDescriptor).bufferedReader().use { reader ->
                reader.readText()
            }
        }
    }
}
