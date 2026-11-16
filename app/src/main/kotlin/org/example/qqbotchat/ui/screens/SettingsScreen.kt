// 设计参考 Telegram for Android (GPL-2.0)
// 设置页，对应 TG 的 SettingsActivity 分组列表（主题 / 字体 / 数据 / 账号）。
// 来自 TG 的常量：ThemeColors.java:20 actionBarDefault/chats_name/chats_message/divider 色值、
// SharedConfig.java:323 正文 16sp、DialogCell.java:1275-1284 角标尺寸（见 Dimens.kt）、
// 分隔线 #FFD9D9D9；界面声明保留 TG GPL-2.0 归属说明。
package org.example.qqbotchat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import org.example.qqbotchat.ui.components.ChatWallpaper
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
    val context = LocalContext.current
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
            .statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()),
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

        Box(Modifier.fillMaxWidth().height(174.dp)) {
            ChatWallpaper(Modifier.matchParentSize())
            Text(stringResource(R.string.preview_incoming), color = c.inBubbleText,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 20.dp, top = 24.dp)
                    .clip(RoundedCornerShape(16.dp)).background(c.inBubble).padding(12.dp))
            Text(stringResource(R.string.preview_outgoing), color = c.outBubbleText,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 24.dp)
                    .clip(RoundedCornerShape(16.dp)).background(c.outBubbleStart).padding(12.dp))
        }
        SectionTitle(stringResource(R.string.appearance))
        SettingRow(
            title = stringResource(R.string.dark_theme),
            subtitle = stringResource(R.string.dark_theme_hint),
            trailing = {
                Switch(checked = state.darkTheme, onCheckedChange = onDarkThemeChange)
            },
        )
        SettingRow(
            title = stringResource(R.string.blue_bubbles),
            subtitle = stringResource(R.string.blue_bubbles_hint),
            trailing = {
                Switch(checked = state.blueGradient, onCheckedChange = onBlueGradientChange)
            },
        )
        SettingRow(
            title = stringResource(R.string.font_size),
            subtitle = stringResource(R.string.font_size_value, (state.fontScale * 100).toInt()),
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onBumpFontScale(-0.1f) }) { Text("A−") }
                    TextButton(onClick = { onBumpFontScale(0.1f) }) { Text("A+") }
                }
            },
        )

        SectionTitle(stringResource(R.string.data_account))
        SettingRow(title = "清空聊天记录", subtitle = stringResource(R.string.clear_chats_hint)) { confirmClear = true }
        SettingRow(title = "导出日志", subtitle = stringResource(R.string.export_logs_hint)) { onExportLogs() }
        SettingRow(title = "退出登录", subtitle = stringResource(R.string.logout_hint)) { confirmLogout = true }

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
            text = { Text(text, fontSize = TgDimens.MessageTimeSize, modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = {
                TextButton(onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
                    context.startActivity(Intent.createChooser(intent, null))
                }) { Text(stringResource(R.string.share)) }
            },
            dismissButton = { TextButton(onClick = onDismissExport) { Text(stringResource(R.string.close)) } },
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
