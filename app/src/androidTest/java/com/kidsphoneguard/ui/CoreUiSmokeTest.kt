package com.kidsphoneguard.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CoreUiSmokeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun passwordDialog_requiresInput_andReturnsEnteredPassword() {
        var confirmedPassword: String? = null

        composeRule.setContent {
            MaterialTheme {
                PasswordDialog(
                    onDismiss = {},
                    onConfirm = { confirmedPassword = it }
                )
            }
        }

        composeRule.onNodeWithText("确认").assertIsNotEnabled()
        composeRule.onNode(hasSetTextAction()).performTextInput("2468")
        composeRule.onNodeWithText("确认").assertIsEnabled().performClick()

        composeRule.runOnIdle {
            assertEquals("2468", confirmedPassword)
        }
    }

    @Test
    fun passwordDialog_showsError_andInvokesDismiss() {
        var dismissed = false

        composeRule.setContent {
            MaterialTheme {
                PasswordDialog(
                    onDismiss = { dismissed = true },
                    onConfirm = {},
                    errorMessage = "密码错误，请重试"
                )
            }
        }

        composeRule.onNodeWithText("密码错误，请重试").assertIsDisplayed()
        composeRule.onNodeWithText("取消").performClick()

        composeRule.runOnIdle {
            assertTrue(dismissed)
        }
    }

    @Test
    fun permissionCard_reflectsGrantedState() {
        composeRule.setContent {
            MaterialTheme {
                PermissionCard(
                    title = "悬浮窗权限",
                    description = "用于显示降级锁",
                    isGranted = true,
                    onClick = {}
                )
            }
        }

        composeRule.onNodeWithText("悬浮窗权限").assertIsDisplayed()
        composeRule.onNodeWithText("已开启").assertIsDisplayed()
        composeRule.onNodeWithText("去开启").assertDoesNotExist()
    }

    @Test
    fun permissionCard_ungrantedState_invokesAction() {
        var clicked = false

        composeRule.setContent {
            MaterialTheme {
                PermissionCard(
                    title = "无障碍服务",
                    description = "守护核心权限",
                    isGranted = false,
                    onClick = { clicked = true }
                )
            }
        }

        composeRule.onNodeWithText("去开启").performClick()

        composeRule.runOnIdle {
            assertTrue(clicked)
        }
    }
}
