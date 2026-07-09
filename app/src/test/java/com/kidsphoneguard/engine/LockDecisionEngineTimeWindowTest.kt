package com.kidsphoneguard.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/**
 * LockDecisionEngine 时段判定纯逻辑单测（ISS-015）。
 *
 * 验证 [LockDecisionEngine.isInBlockedTimeWindow] 对常规窗口、跨午夜窗口、
 * 全天窗口（start==end）、多窗口、非法格式边界的行为。
 */
class LockDecisionEngineTimeWindowTest {

    @Test
    fun normalWindow_inside_returnsTrue() {
        // 22:00-23:00，22:30 在内
        assertTrue(LockDecisionEngine.isInBlockedTimeWindow("22:00-23:00", LocalTime.of(22, 30)))
    }

    @Test
    fun normalWindow_outside_returnsFalse() {
        // 22:00-23:00，21:30 在外
        assertFalse(LockDecisionEngine.isInBlockedTimeWindow("22:00-23:00", LocalTime.of(21, 30)))
    }

    @Test
    fun normalWindow_boundaryStart_returnsTrue() {
        // 起点边界包含
        assertTrue(LockDecisionEngine.isInBlockedTimeWindow("22:00-23:00", LocalTime.of(22, 0)))
    }

    @Test
    fun normalWindow_boundaryEnd_returnsTrue() {
        // 终点边界包含
        assertTrue(LockDecisionEngine.isInBlockedTimeWindow("22:00-23:00", LocalTime.of(23, 0)))
    }

    @Test
    fun crossMidnightWindow_afterStart_returnsTrue() {
        // 跨午夜 22:00-06:00，23:30 在内
        assertTrue(LockDecisionEngine.isInBlockedTimeWindow("22:00-06:00", LocalTime.of(23, 30)))
    }

    @Test
    fun crossMidnightWindow_beforeEnd_returnsTrue() {
        // 跨午夜 22:00-06:00，03:00 在内
        assertTrue(LockDecisionEngine.isInBlockedTimeWindow("22:00-06:00", LocalTime.of(3, 0)))
    }

    @Test
    fun crossMidnightWindow_between_returnsFalse() {
        // 跨午夜 22:00-06:00，12:00 在外（白天）
        assertFalse(LockDecisionEngine.isInBlockedTimeWindow("22:00-06:00", LocalTime.of(12, 0)))
    }

    @Test
    fun fullDayWindow_startEqualsEnd_returnsTrue() {
        // start == end 视为全天禁用
        assertTrue(LockDecisionEngine.isInBlockedTimeWindow("00:00-00:00", LocalTime.of(12, 0)))
    }

    @Test
    fun multipleWindows_firstMatch_returnsTrue() {
        // 多窗口，命中第一个
        assertTrue(
            LockDecisionEngine.isInBlockedTimeWindow(
                "09:00-12:00,14:00-17:00",
                LocalTime.of(10, 30)
            )
        )
    }

    @Test
    fun multipleWindows_secondMatch_returnsTrue() {
        // 多窗口，命中第二个
        assertTrue(
            LockDecisionEngine.isInBlockedTimeWindow(
                "09:00-12:00,14:00-17:00",
                LocalTime.of(15, 30)
            )
        )
    }

    @Test
    fun multipleWindows_noMatch_returnsFalse() {
        // 多窗口，都不命中
        assertFalse(
            LockDecisionEngine.isInBlockedTimeWindow(
                "09:00-12:00,14:00-17:00",
                LocalTime.of(13, 0)
            )
        )
    }

    @Test
    fun invalidFormat_skipped_returnsFalse() {
        // 非法格式跳过，无合法窗口 → false
        assertFalse(LockDecisionEngine.isInBlockedTimeWindow("invalid", LocalTime.of(12, 0)))
    }

    @Test
    fun invalidFormatMixedWithValid_returnsTrueForValid() {
        // 非法与合法混合，合法窗口仍判定
        assertTrue(
            LockDecisionEngine.isInBlockedTimeWindow(
                "invalid,22:00-23:00",
                LocalTime.of(22, 30)
            )
        )
    }

    @Test
    fun emptyWindows_returnsFalse() {
        assertFalse(LockDecisionEngine.isInBlockedTimeWindow("", LocalTime.of(12, 0)))
    }
}
