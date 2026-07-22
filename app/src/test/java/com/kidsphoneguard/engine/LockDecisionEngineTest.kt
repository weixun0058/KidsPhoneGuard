package com.kidsphoneguard.engine

import com.kidsphoneguard.data.model.AppRule
import com.kidsphoneguard.data.model.LimitMode
import com.kidsphoneguard.data.model.RuleType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class LockDecisionEngineTest {

    @Test
    fun globalUnlock_overridesSettingsSurfaceAndGlobalLock() = runBlocking {
        val fake = FakeLockDecisionData(globalUnlock = true, globalLock = true)

        val decision = engine(fake).getBlockDecision("com.android.settings")

        assertAllowed(decision)
    }

    @Test
    fun ownPackage_isAllowedEvenDuringGlobalLock() = runBlocking {
        val fake = FakeLockDecisionData(globalLock = true)

        val decision = engine(fake).getBlockDecision(OWN_PACKAGE)

        assertAllowed(decision)
    }

    @Test
    fun settingsAndMarketSurfaces_areBlockedBeforeAppRules() = runBlocking {
        val settingsDecision = engine().getBlockDecision("com.miui.securitycenter.power")
        val marketDecision = engine().getBlockDecision("com.xiaomi.market")

        assertEquals(BlockReason.APP_BLOCKED, settingsDecision.reason)
        assertEquals("系统设置", settingsDecision.appName)
        assertEquals(BlockReason.APP_BLOCKED, marketDecision.reason)
        assertEquals("安装器/应用市场", marketDecision.appName)
    }

    @Test
    fun globalLock_blocksRegularPackageAndKeepsConfiguredAppName() = runBlocking {
        val fake = FakeLockDecisionData(globalLock = true)
        fake.rules[GAME_PACKAGE] = rule(ruleType = RuleType.ALLOW, appName = "Game")

        val decision = engine(fake).getBlockDecision(GAME_PACKAGE)

        assertTrue(decision.shouldBlock)
        assertEquals(BlockReason.GLOBAL_LOCK, decision.reason)
        assertEquals("Game", decision.appName)
    }

    @Test
    fun missingRule_isAllowed() = runBlocking {
        assertAllowed(engine().getBlockDecision(GAME_PACKAGE))
    }

    @Test
    fun blockRule_isBlocked() = runBlocking {
        val fake = FakeLockDecisionData()
        fake.rules[GAME_PACKAGE] = rule(ruleType = RuleType.BLOCK)

        val decision = engine(fake).getBlockDecision(GAME_PACKAGE)

        assertTrue(decision.shouldBlock)
        assertEquals(BlockReason.APP_BLOCKED, decision.reason)
    }

    @Test
    fun allowRule_isAllowed() = runBlocking {
        val fake = FakeLockDecisionData()
        fake.rules[GAME_PACKAGE] = rule(ruleType = RuleType.ALLOW)

        assertAllowed(engine(fake).getBlockDecision(GAME_PACKAGE))
    }

    @Test
    fun tamperDetected_blocksRuleThatChecksTimeWindow() = runBlocking {
        val fake = FakeLockDecisionData(tamperDetected = true)
        fake.rules[GAME_PACKAGE] = rule(
            ruleType = RuleType.LIMIT,
            limitMode = LimitMode.WINDOW_ONLY,
            blockedTimeWindows = "22:00-23:00"
        )

        val decision = engine(fake).getBlockDecision(GAME_PACKAGE)

        assertTrue(decision.shouldBlock)
        assertEquals(BlockReason.TIME_WINDOW_BLOCKED, decision.reason)
    }

    @Test
    fun currentTimeInsideConfiguredWindow_isBlocked() = runBlocking {
        val fake = FakeLockDecisionData(currentTime = LocalTime.of(22, 30))
        fake.rules[GAME_PACKAGE] = rule(
            ruleType = RuleType.LIMIT,
            limitMode = LimitMode.WINDOW_ONLY,
            blockedTimeWindows = "22:00-23:00"
        )

        val decision = engine(fake).getBlockDecision(GAME_PACKAGE)

        assertTrue(decision.shouldBlock)
        assertEquals(BlockReason.TIME_WINDOW_BLOCKED, decision.reason)
    }

    @Test
    fun durationAtAllowedBoundaryIncludingBonus_isBlocked() = runBlocking {
        val fake = FakeLockDecisionData(
            usedSeconds = 15 * 60L,
            bonusSeconds = 5 * 60L
        )
        fake.rules[GAME_PACKAGE] = rule(
            ruleType = RuleType.LIMIT,
            limitMode = LimitMode.DURATION_ONLY,
            dailyAllowedMinutes = 10
        )

        val decision = engine(fake).getBlockDecision(GAME_PACKAGE)

        assertTrue(decision.shouldBlock)
        assertEquals(BlockReason.TIME_LIMIT_EXCEEDED, decision.reason)
    }

    @Test
    fun durationBelowAllowedBoundaryIncludingBonus_isAllowed() = runBlocking {
        val fake = FakeLockDecisionData(
            usedSeconds = 15 * 60L - 1L,
            bonusSeconds = 5 * 60L
        )
        fake.rules[GAME_PACKAGE] = rule(
            ruleType = RuleType.LIMIT,
            limitMode = LimitMode.DURATION_ONLY,
            dailyAllowedMinutes = 10
        )

        assertAllowed(engine(fake).getBlockDecision(GAME_PACKAGE))
    }

    @Test
    fun durationOnlyMode_ignoresWindowAndTamperState() = runBlocking {
        val fake = FakeLockDecisionData(
            tamperDetected = true,
            currentTime = LocalTime.of(22, 30),
            usedSeconds = 1L
        )
        fake.rules[GAME_PACKAGE] = rule(
            ruleType = RuleType.LIMIT,
            limitMode = LimitMode.DURATION_ONLY,
            dailyAllowedMinutes = 10,
            blockedTimeWindows = "22:00-23:00"
        )

        assertAllowed(engine(fake).getBlockDecision(GAME_PACKAGE))
    }

    @Test
    fun windowOnlyMode_ignoresExceededDurationOutsideWindow() = runBlocking {
        val fake = FakeLockDecisionData(
            currentTime = LocalTime.of(12, 0),
            usedSeconds = 24 * 60 * 60L
        )
        fake.rules[GAME_PACKAGE] = rule(
            ruleType = RuleType.LIMIT,
            limitMode = LimitMode.WINDOW_ONLY,
            dailyAllowedMinutes = 1,
            blockedTimeWindows = "22:00-23:00"
        )

        assertAllowed(engine(fake).getBlockDecision(GAME_PACKAGE))
    }

    private fun engine(fake: FakeLockDecisionData = FakeLockDecisionData()): LockDecisionEngine =
        LockDecisionEngine(fake.dependencies())

    private fun rule(
        ruleType: RuleType,
        appName: String = "Game",
        limitMode: LimitMode = LimitMode.BOTH,
        dailyAllowedMinutes: Int = 0,
        blockedTimeWindows: String = ""
    ): AppRule = AppRule(
        packageName = GAME_PACKAGE,
        appName = appName,
        ruleType = ruleType,
        limitMode = limitMode,
        dailyAllowedMinutes = dailyAllowedMinutes,
        blockedTimeWindows = blockedTimeWindows
    )

    private fun assertAllowed(decision: BlockDecision) {
        assertFalse(decision.shouldBlock)
        assertEquals(BlockReason.NONE, decision.reason)
    }

    companion object {
        private const val OWN_PACKAGE = "com.kidsphoneguard"
        private const val GAME_PACKAGE = "com.example.game"
    }
}

private class FakeLockDecisionData(
    var globalUnlock: Boolean = false,
    var globalLock: Boolean = false,
    var usedSeconds: Long = 0L,
    var bonusSeconds: Long = 0L,
    var tamperDetected: Boolean = false,
    var currentTime: LocalTime = LocalTime.NOON
) {
    val rules = mutableMapOf<String, AppRule>()

    fun dependencies(): LockDecisionDependencies = LockDecisionDependencies(
        appPackageName = "com.kidsphoneguard",
        isGlobalUnlockEnabled = { globalUnlock },
        isGlobalLockEnabled = { globalLock },
        getRuleByPackageName = { packageName -> rules[packageName] },
        getTodayUsageSeconds = { usedSeconds },
        getTodayBonusSeconds = { bonusSeconds },
        isTamperDetected = { tamperDetected },
        currentTime = { currentTime }
    )
}
