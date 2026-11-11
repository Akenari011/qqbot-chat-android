// 设计参考 Telegram for Android (GPL-2.0)
// 会话列表页，对应 TG 的 DialogsActivity + DialogCell。
// 来自 TG 的常量（源码:行号）：DialogCell.java:174-177 行高 70dp、:2518 头像 52dp、
// :2514 名字左侧 76dp、:1298-1313 会话名 17sp / 摘要 16sp、Theme.java:8506 时间 12sp、
// ThemeColors.java:20 chats_name/chats_message/chats_date/chats_unreadCounter/divider 色值、
// styles.xml 分隔线 1dp #FFD9D9D9（见 ui/theme/Dimens.kt 与 Color.kt 逐条出处）。
package org.example.qqbotchat.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import org.example.qqbotchat.ui.components.ConversationActionSheet
import org.example.qqbotchat.ui.components.DialogCell
import org.example.qqbotchat.ui.theme.TgDimens
import org.example.qqbotchat.ui.theme.TgTheme
import org.example.qqbotchat.vm.ConversationListUiState

@Composable
fun ConversationListScreen(
    state: ConversationListUiState,
    onQueryChange: (String) -> Unit,
    onOpen: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    onMarkRead: (String) -> Unit,
    onToggleMute: (String) -> Unit,
    onToggleArchive: (String) -> Unit,
    onDelete: (String) -> Unit,
    onToggleArchivedView: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = TgTheme.colors
    var searchOpen by remember { mutableStateOf(false) }
    // 长按选中的会话（TG DialogsActivity 的长按上下文菜单）。
    var actionTarget by remember { mutableStateOf<org.example.qqbotchat.core.Conversation?>(null) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(c.background)
            // 顶栏自绘，需要自己避让状态栏。
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // 自绘 ActionBar（避免 material3 TopAppBar 的 experimental 依赖），对应 TG ActionBar。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(TgDimens.ActionBarHeight)
                .background(c.actionBar)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "QQBot Chat",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = c.actionBarTitle,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { searchOpen = !searchOpen }) {
                Icon(painterResource(R.drawable.ic_search), "搜索", tint = c.icon)
            }
            IconButton(onClick = onSettings) {
                Icon(painterResource(R.drawable.ic_settings), "设置", tint = c.icon)
            }
        }

        if (searchOpen) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                placeholder = { Text("搜索会话…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(24.dp),
            )
        }

        if (state.conversations.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (state.query.isBlank()) "暂无会话，连接网关后消息会显示在这里" else "没有找到相关会话",
                    color = c.subtitle,
                    fontSize = TgDimens.MessageTextSize,
                )
            }
        } else {
            val shown = if (state.showingArchived) state.archivedConversations else state.conversations
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                // TG DialogsActivity：普通列表顶部有「已归档」入口行。
                if (!state.showingArchived && state.archivedConversations.isNotEmpty()) {
                    item(key = "archived-row") {
                        ArchivedRow(
                            count = state.archivedConversations.size,
                            onClick = onToggleArchivedView,
                        )
                    }
                }
                if (state.showingArchived) {
                    item(key = "back-row") {
                        ArchivedRow(count = shown.size, onClick = onToggleArchivedView, back = true)
                    }
                }
                if (shown.isEmpty()) {
                    item(key = "empty") {
                        Box(
                            Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "暂无已归档会话",
                                color = c.subtitle,
                                fontSize = TgDimens.MessageTextSize,
                            )
                        }
                    }
                }
                items(shown, key = { it.id }) { conversation ->
                    DialogCell(
                        conversation = conversation,
                        onClick = { onOpen(conversation.id) },
                        onTogglePin = { onTogglePin(conversation.id) },
                        onLongClick = { actionTarget = conversation },
                    )
                }
            }
        }
    }

    // 长按会话弹出的操作面板：标记已读 / 置顶 / 静音 / 归档 / 删除。
    actionTarget?.let { conv ->
        ConversationActionSheet(
            conversation = conv,
            onDismiss = { actionTarget = null },
            onMarkRead = {
                onMarkRead(conv.id)
                actionTarget = null
            },
            onTogglePin = {
                onTogglePin(conv.id)
                actionTarget = null
            },
            onToggleMute = {
                onToggleMute(conv.id)
                actionTarget = null
            },
            onToggleArchive = {
                onToggleArchive(conv.id)
                actionTarget = null
            },
            onDelete = {
                onDelete(conv.id)
                actionTarget = null
            },
        )
    }
}

/**
 * 「已归档」入口行，对应 TG DialogsActivity 的 archived row
 * （图标 + 文案 + 数量，行高 70dp，分隔线左缩进 72dp）。
 */
@Composable
private fun ArchivedRow(count: Int, onClick: () -> Unit, back: Boolean = false) {
    val c = TgTheme.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(TgDimens.ListItemHeight)
                .clickable(onClick = onClick)
                .padding(end = TgDimens.TimeRightPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(TgDimens.CellHorizontalPadding))
            Box(
                modifier = Modifier
                    .size(TgDimens.AvatarSize)
                    .clip(RoundedCornerShape(26.dp))
                    .background(c.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = if (back) "←" else "▣", fontSize = 22.sp, color = c.primary)
            }
            Spacer(
                Modifier.width(
                    TgDimens.NameLeftTwoLine - TgDimens.CellHorizontalPadding - TgDimens.AvatarSize,
                ),
            )
            Text(
                text = if (back) "返回聊天列表" else "已归档",
                fontSize = TgDimens.NameSize,
                fontWeight = FontWeight.SemiBold,
                color = c.name,
                modifier = Modifier.weight(1f),
            )
            if (!back) {
                Text(text = count.toString(), fontSize = TgDimens.ListTimeSize, color = c.time)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = TgDimens.DividerStartPadding)
                .height(TgDimens.DividerThickness)
                .background(c.divider),
        )
    }
}
