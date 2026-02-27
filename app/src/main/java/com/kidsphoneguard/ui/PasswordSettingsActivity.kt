package com.kidsphoneguard.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kidsphoneguard.utils.PasswordManager

/**
 * 密码设置页面
 * 用于修改家长配置密码
 */
class PasswordSettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                PasswordSettingsScreen(
                    onBackClick = { finish() },
                    onPasswordChanged = { message ->
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordSettingsScreen(
    onBackClick: () -> Unit,
    onPasswordChanged: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val passwordManager = remember { PasswordManager.getInstance(context) }

    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("密码设置") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 说明文字
            Text(
                text = "修改家长配置密码",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "默认密码: 123456\n建议修改为您自己的密码",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 当前密码输入
            OutlinedTextField(
                value = currentPassword,
                onValueChange = { currentPassword = it },
                label = { Text("当前密码") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth()
            )

            // 新密码输入
            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = { Text("新密码") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth()
            )

            // 确认新密码
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("确认新密码") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 修改密码按钮
            Button(
                onClick = {
                    when {
                        currentPassword.isEmpty() -> {
                            onPasswordChanged("请输入当前密码")
                        }
                        !passwordManager.verifyPassword(currentPassword) -> {
                            onPasswordChanged("当前密码错误")
                        }
                        newPassword.isEmpty() -> {
                            onPasswordChanged("请输入新密码")
                        }
                        newPassword.length < 4 -> {
                            onPasswordChanged("密码长度至少4位")
                        }
                        newPassword != confirmPassword -> {
                            onPasswordChanged("两次输入的新密码不一致")
                        }
                        else -> {
                            passwordManager.setPassword(newPassword)
                            onPasswordChanged("密码修改成功")
                            currentPassword = ""
                            newPassword = ""
                            confirmPassword = ""
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("修改密码")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 重置密码按钮
            Button(
                onClick = {
                    if (passwordManager.verifyPassword(currentPassword)) {
                        passwordManager.resetToDefault()
                        onPasswordChanged("已重置为默认密码: 123456")
                        currentPassword = ""
                        newPassword = ""
                        confirmPassword = ""
                    } else {
                        onPasswordChanged("当前密码错误，无法重置")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("重置为默认密码")
            }
        }
    }
}
