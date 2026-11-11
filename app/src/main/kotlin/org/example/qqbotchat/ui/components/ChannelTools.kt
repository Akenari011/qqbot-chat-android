// 设计参考 Telegram for Android (GPL-2.0)
// 本文件是 TG 频道/聊天「更多」菜单（对应 ChatActivity 的 ActionBar 溢出菜单）的 Compose 实现，
// 为 :core 已有的频道能力 API（org.example.qqbotchat.core.QQBotApi：
// getMyGuilds / getGuildChannels / getChannelPins / addChannelPin / removeChannelPin /
// getChannelSchedules / getChannelMessages / addReaction / removeReaction）补齐 UI 层入口。
//
// 组成：
//   · ChannelToolsMenu   —— 溢出下拉菜单，13 个入口，点击只派发 ChannelAction；
//   · ChannelAction      —— sealed interface，每个入口一个 data object，供 ChatViewModel 的 when 穷尽分发；
//   · ChannelToolsDialog —— 用 Dialog 自绘卡片，展示频道 API 返回的文本列表。
//
// 兼容性：DropdownMenu / DropdownMenuItem 在 material3 1.3（Compose BOM 2024.10.01）为稳定 API；
//         不使用 ModalBottomSheet / experimental API；图标用文字符号，不依赖未声明的 material-icons。
// 取色：仅使用 TgColors 中已确认存在的字段（surface / actionBarTitle / subtitle / divider /
//       primary / icon / inBubbleText），字号取 TgDimens 的 MessageTextSize(16sp) 与 AuthorNameSize(14sp)。
package org.example.qqbotchat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.example.qqbotchat.ui.theme.TgDimens
import org.example.qqbotchat.ui.theme.TgTheme

/**
 * 「更多」菜单的每一个动作。全部用 data object（无参），便于 ChatViewModel 直接 `when (action)` 穷尽分发。
 * 其中 Pin / Unpin / React 所需的 messageId（以及 React 的 emojiId）由调用方从「当前选中消息」取得，
 * 不在事件里携带参数，保持菜单与 ViewModel 解耦。
 * 每个子类的注释里注明该动作对应的 :core 频道 API，供接线时参考。
 */
sealed interface ChannelAction {
    /** 频道列表：getMyGuilds()。 */
    data object MyGuilds : ChannelAction

    /** 子频道列表：getGuildChannels(guildId)。 */
    data object GuildChannels : ChannelAction

    /** 频道成员。 */
    data object GuildMembers : ChannelAction

    /** 置顶消息：getChannelPins(channelId)。 */
    data object ChannelPins : ChannelAction

    /** 日程：getChannelSchedules(channelId)。 */
    data object ChannelSchedules : ChannelAction

    /** 拉取历史 50 条：getChannelMessages(channelId, limit = 50)。 */
    data object ChannelHistory : ChannelAction

    /** 设为置顶：addChannelPin(channelId, messageId)；messageId 取当前选中消息。 */
    data object Pin : ChannelAction

    /** 取消置顶：removeChannelPin(channelId, messageId)；messageId 取当前选中消息。 */
    data object Unpin : ChannelAction

    /** 发布公告。 */
    data object PublishAnnouncement : ChannelAction

    /** 取消公告。 */
    data object CancelAnnouncement : ChannelAction

    /** 表情回应：addReaction(channelId, messageId, emojiId, emojiType)；参数取当前选中消息与表情。 */
    data object React : ChannelAction

    /** 消息表态用户。 */
    data object ReactionUsers : ChannelAction

    /** 导出日志。 */
    data object ExportLog : ChannelAction
}

/** 菜单行数据：文字符号图标 + 中文文案 + 派发的动作。 */
private data class ChannelMenuEntry(
    val icon: String,
    val label: String,
    val action: ChannelAction,
)

/** 13 个入口，顺序即菜单展示顺序（对应 ChatActivity 溢出菜单的分组）。 */
private val CHANNEL_MENU_ENTRIES = listOf(
    ChannelMenuEntry("🗂", "频道列表", ChannelAction.MyGuilds),
    ChannelMenuEntry("☰", "子频道列表", ChannelAction.GuildChannels),
    ChannelMenuEntry("👥", "频道成员", ChannelAction.GuildMembers),
    ChannelMenuEntry("📌", "置顶消息", ChannelAction.ChannelPins),
    ChannelMenuEntry("📅", "日程", ChannelAction.ChannelSchedules),
    ChannelMenuEntry("⏬", "拉取历史 50 条", ChannelAction.ChannelHistory),
    ChannelMenuEntry("⭐", "设为置顶", ChannelAction.Pin),
    ChannelMenuEntry("✖", "取消置顶", ChannelAction.Unpin),
    ChannelMenuEntry("📢", "发布公告", ChannelAction.PublishAnnouncement),
    ChannelMenuEntry("🔕", "取消公告", ChannelAction.CancelAnnouncement),
    ChannelMenuEntry("😀", "表情回应", ChannelAction.React),
    ChannelMenuEntry("🙋", "消息表态用户", ChannelAction.ReactionUsers),
    ChannelMenuEntry("📤", "导出日志", ChannelAction.ExportLog),
)

/**
 * TG 频道/聊天「更多」下拉菜单（ActionBar 溢出菜单的 Compose 实现）。
 *
 * @param expanded  是否展开，由调用方（ChatScreen 的溢出按钮状态）控制
 * @param onDismiss 点击菜单外 / 返回键时回调，交给 DropdownMenu 的 onDismissRequest
 * @param onAction  点击任一菜单项时派发对应 [ChannelAction]
 * @param modifier  作用于 DropdownMenu 容器
 *
 * 注意：菜单项点击只调用 onAction，**不主动关闭菜单**；是否关闭由调用方在 onAction 里
 * 自行调用 onDismiss 决定，避免与「先选中消息、再执行操作」的流程冲突。
 */
@Composable
fun ChannelToolsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAction: (ChannelAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = TgTheme.colors

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        CHANNEL_MENU_ENTRIES.forEach { entry ->
            DropdownMenuItem(
                // 文案 16sp（TgDimens.MessageTextSize），标题色。
                text = {
                    Text(
                        text = entry.label,
                        fontSize = TgDimens.MessageTextSize,
                        color = c.actionBarTitle,
                    )
                },
                // 文字符号图标：不依赖未声明的 material-icons 依赖。
                leadingIcon = {
                    Text(
                        text = entry.icon,
                        fontSize = 18.sp,
                        color = c.icon,
                        modifier = Modifier.width(24.dp),
                    )
                },
                onClick = { onAction(entry.action) },
            )
        }
    }
}

/**
 * 频道 API 结果展示卡片：置顶消息 / 日程 / 历史 / 频道列表等文本列表的通用容器。
 * 用 androidx.compose.ui.window.Dialog 自绘卡片，规避 experimental 的 ModalBottomSheet。
 *
 * @param title     卡片标题，16sp semibold
 * @param items     已格式化为文本的列表项（14sp）；空列表显示「（暂无数据）」
 * @param onDismiss 点击「关闭」或卡片外时回调
 * @param modifier  作用于卡片根节点
 */
@Composable
fun ChannelToolsDialog(
    title: String,
    items: List<String>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = TgTheme.colors

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(c.surface)
                .padding(vertical = 8.dp),
        ) {
            // 顶部标题：16sp semibold（MessageTextSize = 16sp）。
            Text(
                text = title,
                fontSize = TgDimens.MessageTextSize,
                fontWeight = FontWeight.SemiBold,
                color = c.actionBarTitle,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(c.divider),
            )

            // 列表区：最多约 360dp，超出后可滚动（Column + verticalScroll，稳定 API）。
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (items.isEmpty()) {
                    Text(
                        text = "（暂无数据）",
                        fontSize = TgDimens.AuthorNameSize,   // 14sp
                        color = c.subtitle,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                } else {
                    items.forEachIndexed { index, line ->
                        Text(
                            text = line,
                            fontSize = TgDimens.AuthorNameSize, // 14sp
                            color = c.inBubbleText,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                        if (index != items.lastIndex) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp)
                                    .height(1.dp)
                                    .background(c.divider),
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(c.divider),
            )
            // 底部「关闭」TextButton（material3 稳定 API）。
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "关闭",
                    fontSize = TgDimens.MessageTextSize,
                    color = c.primary,
                )
            }
        }
    }
}
