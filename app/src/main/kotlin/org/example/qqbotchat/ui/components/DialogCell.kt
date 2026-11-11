// 设计参考 Telegram for Android (GPL-2.0)
// 本文件实现会话列表单元，逐一对应 TG 的 DialogCell.java（本轮源码精读的权威常量）。
// 【逐条源码出处：文件:行号】
//   · DialogCell.java:174-177,173,2514,2502  行高 70dp(两行)/76dp(三行)、文字起始 72dp、
//     名字左侧 76dp(两行)/78dp(三行)、时间右 15dp、分隔线 1px #FFD9D9D9
//   · DialogCell.java:2518,2495,3196-3198  头像 52dp(两行)/56dp(三行)、圆角 26dp、60dp 仅为点击热区
//   · DialogCell.java:1298-1313  会话名 17sp(两行)/16sp(三行)、摘要 16sp(两行)/15sp(三行)
//   · Theme.java:8506 时间 12sp；ThemeColors.java:20 chats_name=#FF1A1D21、chats_message=#FF75787A、
//     chats_date=#FF848688、chats_unreadCounter=#FF229AF0、chats_unreadCounterMuted=#FFBEC3C7、
//     chats_pinnedIcon=#FF919294、chats_draft=#FFDD4B39、divider=#FFD9D9D9
//   · DialogCell.java:1275-1284,5386  未读角标 20.666dp 高 / 11.5dp 圆角 / 12sp（见 ui/tg/TgBadge.kt）
package org.example.qqbotchat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.example.qqbotchat.R
import org.example.qqbotchat.core.Conversation
import org.example.qqbotchat.ui.theme.TgDimens
import org.example.qqbotchat.ui.theme.TgTheme
import org.example.qqbotchat.util.listTime

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialogCell(
    conversation: Conversation,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val c = TgTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(TgDimens.ListItemHeight)                 // 两行 70dp
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(end = TgDimens.TimeRightPadding),       // 时间右 15dp
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(TgDimens.CellHorizontalPadding))
            TgAvatar(name = conversation.displayName, avatarUrl = conversation.avatarPath)

            // 名字左侧 76dp = 16dp 左边距 + 52dp 头像 + 8dp 间距
            Spacer(
                Modifier.width(
                    TgDimens.NameLeftTwoLine -
                        TgDimens.CellHorizontalPadding -
                        TgDimens.AvatarSize,
                ),
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = conversation.displayName,
                        fontSize = TgDimens.NameSize,             // 17sp
                        fontWeight = FontWeight.SemiBold,
                        color = c.name,                          // chats_name #FF1A1D21
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (conversation.pinned) {
                        Icon(
                            painter = painterResource(R.drawable.ic_pin),
                            contentDescription = "置顶",
                            tint = c.pinnedIcon,                 // chats_pinnedIcon #FF919294
                            modifier = Modifier.size(14.dp).clickable(onClick = onTogglePin),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = listTime(conversation.lastTimestamp),
                        fontSize = TgDimens.ListTimeSize,        // 12sp
                        color = c.time,                          // chats_date #FF848688
                    )
                }

                Spacer(Modifier.height(3.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = conversation.lastText.ifEmpty { "暂无消息" },
                        fontSize = TgDimens.SubtitleSize,        // 16sp
                        color = c.subtitle,                      // chats_message #FF75787A
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (conversation.unread > 0) {
                        UnreadBadge(count = conversation.unread, muted = conversation.muted)
                    }
                }
            }
        }

        // DialogCell 分隔线：1px #FFD9D9D9，左缩进 72dp（文字起始）。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = TgDimens.DividerStartPadding)
                .height(TgDimens.DividerThickness)
                .background(c.divider),
        )
    }
}

/** 未读角标：TG DialogCell.java:1275-1284,5386（20.666dp 高 / 11.5dp 圆角 / 12sp）。 */
@Composable
private fun UnreadBadge(count: Int, muted: Boolean) {
    val c = TgTheme.colors
    Box(
        modifier = Modifier
            .height(TgDimens.UnreadBadgeHeight)
            .defaultMinSize(minWidth = TgDimens.UnreadBadgeHeight)
            .clip(RoundedCornerShape(TgDimens.UnreadBadgeRadius))
            .background(if (muted) c.unreadBadgeMuted else c.unreadBadge)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            color = c.unreadText,
            fontSize = TgDimens.UnreadBadgeTextSize,
            fontWeight = FontWeight.Medium,
        )
    }
}
