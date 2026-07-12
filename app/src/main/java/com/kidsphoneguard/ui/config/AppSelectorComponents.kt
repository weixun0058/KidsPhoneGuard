package com.kidsphoneguard.ui.config

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.kidsphoneguard.utils.AppScanner
import kotlinx.coroutines.launch

/** 应用规则的选择、搜索和列表/图标展示。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectorDialog(
    onDismiss: () -> Unit,
    onAppSelected: (AppScanner.AppInfo) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var apps by remember { mutableStateOf<List<AppScanner.AppInfo>>(emptyList()) }
    var filteredApps by remember { mutableStateOf<List<AppScanner.AppInfo>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var showSystemApps by remember { mutableStateOf(false) }
    var useGridView by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        scope.launch {
            apps = AppScanner.getInstalledApps(context, includeSystemApps = false)
            filteredApps = apps
            isLoading = false
        }
    }

    LaunchedEffect(searchQuery, showSystemApps) {
        scope.launch {
            filteredApps = if (searchQuery.isBlank()) {
                if (showSystemApps) {
                    AppScanner.getInstalledApps(context, includeSystemApps = true)
                } else {
                    apps
                }
            } else {
                AppScanner.searchApps(context, searchQuery, showSystemApps)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择应用") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("搜索应用") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Switch(
                        checked = showSystemApps,
                        onCheckedChange = { showSystemApps = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("显示系统应用")
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { useGridView = false },
                        modifier = Modifier.weight(1f),
                        colors = if (!useGridView) {
                            ButtonDefaults.buttonColors()
                        } else {
                            ButtonDefaults.outlinedButtonColors()
                        }
                    ) {
                        Text("列表")
                    }
                    Button(
                        onClick = { useGridView = true },
                        modifier = Modifier.weight(1f),
                        colors = if (useGridView) {
                            ButtonDefaults.buttonColors()
                        } else {
                            ButtonDefaults.outlinedButtonColors()
                        }
                    ) {
                        Text("图标")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                if (isLoading) {
                    AppSelectorStatus("加载中...")
                } else if (filteredApps.isEmpty()) {
                    AppSelectorStatus("未找到应用")
                } else if (!useGridView) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredApps) { appInfo ->
                            AppListItem(
                                appInfo = appInfo,
                                onClick = { onAppSelected(appInfo) }
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredApps.size) { index ->
                            val appInfo = filteredApps[index]
                            AppGridSelectItem(
                                appInfo = appInfo,
                                onClick = { onAppSelected(appInfo) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun AppSelectorStatus(text: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text, color = Color.Gray)
    }
}

@Composable
fun AppListItem(
    appInfo: AppScanner.AppInfo,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            appInfo.icon?.let { icon ->
                Image(
                    bitmap = icon.toBitmap().asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appInfo.appName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = appInfo.packageName,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                if (appInfo.isSystemApp) {
                    Text(
                        text = "系统应用",
                        fontSize = 10.sp,
                        color = Color(0xFF2196F3)
                    )
                }
            }
        }
    }
}

@Composable
fun AppGridSelectItem(
    appInfo: AppScanner.AppInfo,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            appInfo.icon?.let { icon ->
                Image(
                    bitmap = icon.toBitmap().asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(42.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = appInfo.appName,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (appInfo.isSystemApp) {
                Text(
                    text = "系统",
                    fontSize = 10.sp,
                    color = Color(0xFF2196F3)
                )
            }
        }
    }
}
