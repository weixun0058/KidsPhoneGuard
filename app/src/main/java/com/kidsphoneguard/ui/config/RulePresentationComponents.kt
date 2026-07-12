package com.kidsphoneguard.ui.config

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.kidsphoneguard.data.model.*
import com.kidsphoneguard.utils.AppScanner

@Composable
fun ConfigRuleCard(rule: AppRule, usedSeconds: Long, bonusSeconds: Long, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = when (rule.ruleType) {
        RuleType.ALLOW -> Color(0xFFE8F5E9); RuleType.BLOCK -> Color(0xFFFFEBEE); RuleType.LIMIT -> Color(0xFFFFF3E0)
    })) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(rule.appName.ifEmpty { rule.packageName }, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(rule.packageName, fontSize = 12.sp, color = Color.Gray)
                }
                Row { TextButton(onEdit) { Text("修改") }; TextButton(onDelete) { Text("删除", color = Color.Red) } }
            }
            Spacer(Modifier.height(8.dp))
            when (rule.ruleType) {
                RuleType.ALLOW -> Text("规则: 放行", color = Color(0xFF4CAF50))
                RuleType.BLOCK -> Text("规则: 永久禁用", color = Color(0xFFF44336))
                RuleType.LIMIT -> Column {
                    Text(when (rule.limitMode) { LimitMode.BOTH -> "模式: 限时长+限时段"; LimitMode.DURATION_ONLY -> "模式: 仅限时长"; LimitMode.WINDOW_ONLY -> "模式: 仅限时段" })
                    if (rule.dailyAllowedMinutes > 0) Text("每日限制: ${rule.dailyAllowedMinutes} 分钟")
                    RuleUsageFormatter.summary(rule, usedSeconds, bonusSeconds).takeIf { it.isNotEmpty() }?.let { Text(it, color = Color(0xFF5D4037)) }
                    if (rule.blockedTimeWindows.isNotEmpty()) Text("禁用时段: ${rule.blockedTimeWindows}")
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConfigRuleGridCard(rule: AppRule, usedSeconds: Long, bonusSeconds: Long, onLongPress: () -> Unit) {
    val context = LocalContext.current
    val icon = remember(rule.packageName) { AppScanner.getAppInfo(context, rule.packageName)?.icon }
    val (label, labelColor, background) = when (rule.ruleType) {
        RuleType.ALLOW -> Triple("放行", Color(0xFF2E7D32), Color(0xFFE8F5E9))
        RuleType.BLOCK -> Triple("禁用", Color(0xFFC62828), Color(0xFFFFEBEE))
        RuleType.LIMIT -> Triple("限时", Color(0xFFEF6C00), Color.White)
    }
    Card(Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = onLongPress), colors = CardDefaults.cardColors(background)) {
        Column(Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (icon != null) Image(icon.toBitmap().asImageBitmap(), null, Modifier.size(40.dp))
            else Text(rule.appName.ifBlank { "?" }.take(1), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
            Spacer(Modifier.height(6.dp))
            Text(rule.appName.ifBlank { rule.packageName }, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(label, fontSize = 11.sp, color = labelColor, fontWeight = FontWeight.Medium)
            RuleUsageFormatter.summary(rule, usedSeconds, bonusSeconds).takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 10.sp, color = Color.Gray, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
    }
}
