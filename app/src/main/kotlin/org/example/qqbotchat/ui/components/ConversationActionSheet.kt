// 设计参考 Telegram for Android (GPL-2.0)
// 会话长按操作面板，对应 TG DialogsActivity 长按会话弹出的上下文菜单
// （TG 用 DialogCell 的 onLongClick -> showDialogPopup，见 DialogsActivity.java）。
// 来自 TG 的常量：ThemeColors.java:20 divider=#FFD9D9D9、chats_message=#FF75787A；
// Theme.java:8133 正文 16sp、:8506 时间 12sp；操作行 48dp 高（TG 弹出菜单行高）。
package org.example.qqbotchat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.example.qqbotchat.core.Conversation
import org.example.qqbotchat.ui.theme.TgDimens
import org.example.qqbotchat.ui.theme.TgTheme

/**
 * 会话操作面板：标记已读 / 置顶 / 静音 / 归档 / 删除。
 *
 * @param conversation 目标会话。
 * @param onDismiss 关闭面板。
 * @param onMarkRead 标记已读。
 * @param onTogglePin 置顶 / 取消置顶。
 * @param onToggleMute 静音 / 取消静音。
 * @param onToggleArchive 归档 / 取消归档。
 * @param onDelete 删除会话（含本地记录）。
 */
@Composable
fun ConversationActionSheet(
    conversation: Conversation,
    onDismiss: () -> Unit,
    onMarkRead: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = TgTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(c.surface),
        ) {
            // 头部：会话名 + 当前状态摘要
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    text = conversation.displayName,
                    fontSize = TgDimens.MessageTextSize,
                    fontWeight = FontWeight.SemiBold,
                    color = c.actionBarTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = buildString {
                        append(if (conversation.scene == "group") "群聊" else if (conversation.scene == "channel") "频道" else "私聊")
                        if (conversation.pinned) append(" · 已置顶")
                        if (conversation.muted) append(" · 已静音")
                        if (conversation.archived) append(" · 已归档")
                        if (conversation.unread > 0) append(" · ").append(conversation.unread).append(" 条未读")
                    },
                    fontSize = TgDimens.MessageTimeSize,
                    color = c.subtitle,
                )
            }
            SheetDivider(c.divider)

            ActionLine("标记已读", enabled = conversation.unread > 0, onClick = onMarkRead)
            SheetDivider(c.divider)
            ActionLine(if (conversation.pinned) "取消置顶" else "置顶", onClick = onTogglePin)
            SheetDivider(c.divider)
            ActionLine(if (conversation.muted) "取消静音" else "静音", onClick = onToggleMute)
            SheetDivider(c.divider)
            ActionLine(if (conversation.archived) "取消归档" else "归档", onClick = onToggleArchive)
            SheetDivider(c.divider)
            ActionLine("删除会话", danger = true, onClick = onDelete)
            SheetDivider(c.divider)
            ActionLine("取消", onClick = onDismiss)
        }
    }
}

/** 单行操作：48dp 高、16sp 文字（TG 弹出菜单行高与字号）。 */
@Composable
private fun ActionLine(
    label: String,
    danger: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val c = TgTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = TgDimens.MessageTextSize,
            color = when {
                !enabled -> c.subtitle.copy(alpha = 0.5f)
                danger -> c.error
                else -> c.actionBarTitle
            },
        )
    }
}

/** 1px 分割线（ThemeColors.java:20 divider）。 */
@Composable
private fun SheetDivider(color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color),
    )
}
