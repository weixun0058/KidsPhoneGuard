package com.kidsphoneguard.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kidsphoneguard.utils.PasswordManager
import com.kidsphoneguard.utils.RecoveryCodeEngine
import com.kidsphoneguard.utils.RecoveryCodeManager
import com.kidsphoneguard.utils.RecoveryVerificationResult
import com.kidsphoneguard.utils.TrustedTimeProvider

class PasswordRecoveryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PasswordRecoveryScreen()
                }
            }
        }
    }

    private companion object {
        const val TAG = "PasswordRecovery"
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PasswordRecoveryScreen() {
    val activity = LocalContext.current as? ComponentActivity
    val context = LocalContext.current
    val snapshot = remember { RecoveryCodeManager.snapshot(context) }
    val passwordManager = remember { PasswordManager.getInstance(context) }
    var recoveryCode by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("忘记家长密码") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "请把下面的设备号和计算日期报给客服，获取当天 8 位恢复码。",
                modifier = Modifier.fillMaxWidth()
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("恢复设备号", style = MaterialTheme.typography.labelLarge)
                    Text(
                        snapshot.displayRecoveryId,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text("计算日期：${snapshot.recoveryDate}")
                    TextButton(
                        onClick = {
                            val clipboard = context.getSystemService(
                                Context.CLIPBOARD_SERVICE
                            ) as ClipboardManager
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText(
                                    "KidsPhoneGuard恢复信息",
                                    "${snapshot.displayRecoveryId} ${snapshot.recoveryDate}"
                                )
                            )
                            Toast.makeText(context, "恢复信息已复制", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("复制恢复信息")
                    }
                }
            }

            OutlinedTextField(
                value = recoveryCode,
                onValueChange = {
                    recoveryCode = RecoveryCodeEngine
                        .normalizeEnteredCode(it)
                        .take(RecoveryCodeEngine.CODE_LENGTH)
                },
                label = { Text("客服提供的 8 位恢复码") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it.filter(Char::isDigit) },
                label = { Text("新家长密码（至少 6 位数字）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it.filter(Char::isDigit) },
                label = { Text("确认新家长密码") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = { activity?.finish() }) {
                    Text("取消")
                }
                Button(
                    enabled = !submitting,
                    onClick = {
                        errorMessage = validateRecoveryInput(
                            recoveryCode = recoveryCode,
                            newPassword = newPassword,
                            confirmPassword = confirmPassword
                        )
                        if (errorMessage != null) {
                            return@Button
                        }

                        submitting = true
                        when (
                            val result = RecoveryCodeManager.verify(
                                context = context,
                                enteredCode = recoveryCode,
                                recoverySnapshot = snapshot
                            )
                        ) {
                            RecoveryVerificationResult.Success -> {
                                try {
                                    passwordManager.setPassword(newPassword)
                                    TrustedTimeProvider.clearTamperFlag(context)
                                    Log.w(
                                        "PasswordRecovery",
                                        "parent_password_reset_by_support_code"
                                    )
                                    Toast.makeText(
                                        context,
                                        "家长密码已重设",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    activity?.finish()
                                } catch (e: Exception) {
                                    Log.e(
                                        "PasswordRecovery",
                                        "parent_password_reset_failed",
                                        e
                                    )
                                    errorMessage = "新密码保存失败，请重试"
                                    submitting = false
                                }
                            }

                            is RecoveryVerificationResult.RateLimited -> {
                                errorMessage =
                                    "尝试次数过多，请 ${result.retryAfterSeconds} 秒后再试"
                                submitting = false
                            }

                            is RecoveryVerificationResult.Rejected -> {
                                errorMessage = if (result.retryAfterSeconds > 0L) {
                                    "恢复码错误，请 ${result.retryAfterSeconds} 秒后再试"
                                } else {
                                    "恢复码错误，还可尝试 ${result.remainingAttempts} 次"
                                }
                                submitting = false
                            }
                        }
                    }
                ) {
                    Text("验证并重设密码")
                }
            }
        }
    }
}

private fun validateRecoveryInput(
    recoveryCode: String,
    newPassword: String,
    confirmPassword: String
): String? = when {
    recoveryCode.length != RecoveryCodeEngine.CODE_LENGTH -> "请输入完整的 8 位恢复码"
    newPassword.length < 6 -> "新密码至少需要 6 位数字"
    newPassword != confirmPassword -> "两次输入的新密码不一致"
    else -> null
}
