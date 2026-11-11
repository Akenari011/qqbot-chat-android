// 设计参考 Telegram for Android (GPL-2.0)
// 设置页，对应 TG 的 SettingsActivity 分组列表（主题 / 字体 / 数据 / 账号）。
// 来自 TG 的常量：ThemeColors.java:20 actionBarDefault/chats_name/chats_message/divider 色值、
// SharedConfig.java:323 正文 16sp、DialogCell.java:1275-1284 角标尺寸（见 Dimens.kt）、
// 分隔线 #FFD9D9D9；界面声明保留 TG GPL-2.0 归属说明。
package org.example.qqbotchat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.qqbotchat.R
import org.example.qqbotchat.ui.theme.TgDimens
import org.example.qqbotchat.ui.theme.TgTheme
import org.example.qqbotchat.vm.SettingsUiState

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onDarkThemeChange: (Boolean) -> Unit,
    onBlueGradientChange: (Boolean) -> Unit,
    onBumpFontScale: (Float) -> Unit,
    onClearAll: () -> Unit,
    onExportLogs: () -> Unit,
    onDismissExport: () -> Unit,
    onDismissStatus: () -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = TgTheme.colors
    var confirmClear by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }

    LaunchedEffect(state.status) {
        if (state.status.isNotBlank()) {
            kotlinx.coroutines.delay(2500)
            onDismissStatus()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(TgDimens.ActionBarHeight)
                .background(c.actionBar)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_back), "返回", tint = c.icon)
            }
            Text(
                text = "设置",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = c.actionBarTitle,
            )
        }

        SectionTitle("外观")
        SettingRow(
            title = "深色主题",
            subtitle = "对应 TG Dark / Night 主题",
            trailing = {
                Switch(checked = state.darkTheme, onCheckedChange = onDarkThemeChange)
            },
        )
        SettingRow(
            title = "蓝色渐变气泡",
            subtitle = "默认使用 TG 浅色 #FFEFFFDE；开启后使用 iOS 版 #57B2E0→#0088FF",
            trailing = {
                Switch(checked = state.blueGradient, onCheckedChange = onBlueGradientChange)
            },
        )
        SettingRow(
            title = "字体大小",
            subtitle = "当前 " + (state.fontScale * 100).toInt() + "%（基准 16sp）",
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onBumpFontScale(-0.1f) }) { Text("A−") }
                    TextButton(onClick = { onBumpFontScale(0.1f) }) { Text("A+") }
                }
            },
        )

        SectionTitle("数据与账号")
        SettingRow(title = "清空聊天记录", subtitle = "删除所有会话与消息") { confirmClear = true }
        SettingRow(title = "导出日志", subtitle = "查看会话消息与网关日志") { onExportLogs() }
        SettingRow(title = "退出登录", subtitle = "断开网关并返回登录页") { confirmLogout = true }

        if (state.status.isNotBlank()) {
            Text(
                text = state.status,
                fontSize = TgDimens.MessageTimeSize,
                color = c.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        Text(
            text = "界面设计参考 Telegram for Android（DrKLO/Telegram，GPL-2.0）",
            fontSize = TgDimens.MessageTimeSize,
            color = c.subtitle,
            modifier = Modifier.padding(16.dp),
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空聊天记录") },
            text = { Text("确定清空所有会话与消息？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = { onClearAll(); confirmClear = false }) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            },
        )
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("退出登录") },
            text = { Text("确定断开网关并退出登录？") },
            confirmButton = {
                TextButton(onClick = { confirmLogout = false; onLogout() }) { Text("退出") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text("取消") }
            },
        )
    }

    state.exportText?.let { text ->
        AlertDialog(
            onDismissRequest = onDismissExport,
            title = { Text("日志预览") },
            text = { Text(text, fontSize = TgDimens.MessageTimeSize) },
            confirmButton = {
                TextButton(onClick = onDismissExport) { Text("关闭") }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    val c = TgTheme.colors
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = c.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val c = TgTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = TgDimens.MessageTextSize, color = c.actionBarTitle)
            if (subtitle != null) {
                Text(subtitle, fontSize = TgDimens.MessageTimeSize, color = c.subtitle)
            }
        }
        trailing?.invoke()
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(TgDimens.DividerThickness)
            .background(c.divider),
    )
}
