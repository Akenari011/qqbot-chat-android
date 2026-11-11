// 设计参考 Telegram for Android (GPL-2.0)
// 本文件实现聊天输入栏，对应 TG 的 ChatActivityEnterView / ChatMessageCell 引用条。
// 【逐条源码出处：文件:行号】
//   · SharedConfig.java:323 / Theme.java:8133  正文 16sp（输入框文字同值）
//   · ThemeColors.java:20  divider=#FFD9D9D9（输入胶囊边框）、chats_message=#FF75787A（占位文字）、
//     chat_messageLinkIn/Out=#FF2678B6（引用竖条/主色）
//   · 本任务 iOS 规格（保留）：玻璃感半透明胶囊、高 48dp、圆角 24dp、附件/表情按钮、发送/麦克风切换
package org.example.qqbotchat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.qqbotchat.R
import org.example.qqbotchat.core.ChatMessage
import org.example.qqbotchat.ui.theme.TgDimens
import org.example.qqbotchat.ui.theme.TgTheme

@Composable
fun ChatComposer(
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    replyingTo: ChatMessage?,
    onCancelReply: () -> Unit,
    onAttach: () -> Unit,
    onEmoji: () -> Unit,
    onVoice: () -> Unit,
    isRecording: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val c = TgTheme.colors
    Column(modifier = modifier.fillMaxWidth().background(c.surface)) {
        if (replyingTo != null) {
            ReplyBar(message = replyingTo, onCancel = onCancelReply)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            // 玻璃感胶囊：半透明白底 + 1dp divider 边框 + 24dp 圆角
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = TgDimens.ComposerHeight)
                    .clip(RoundedCornerShape(TgDimens.ComposerCorner))
                    .background(c.composerBackground)
                    .border(1.dp, c.composerBorder, RoundedCornerShape(TgDimens.ComposerCorner))
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onAttach,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_attach),
                        contentDescription = "附件",
                        tint = c.icon,
                    )
                }

                Box(
                    modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (draft.isEmpty()) {
                        Text(
                            text = "输入消息…",
                            color = c.subtitle,
                            fontSize = TgDimens.MessageTextSize,
                        )
                    }
                    BasicTextField(
                        value = draft,
                        onValueChange = onDraftChange,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(
                            fontSize = TgDimens.MessageTextSize,    // 16sp
                            color = c.actionBarTitle,
                        ),
                        maxLines = 4,
                    )
                }

                IconButton(
                    onClick = onEmoji,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_emoji),
                        contentDescription = "表情",
                        tint = c.icon,
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            // 发送 / 麦克风切换：有草稿显示纸飞机，无草稿显示麦克风
            val actionModifier = Modifier
                .size(TgDimens.ComposerHeight)
                .clip(CircleShape)
                .background(c.primary)
            if (draft.isBlank() || isRecording) {
                Box(
                    modifier = actionModifier
                        .background(if (isRecording) Color(0xFFE53935) else c.primary)
                        .clickable(onClick = onVoice),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(
                            if (isRecording) R.drawable.ic_close else R.drawable.ic_mic,
                        ),
                        contentDescription = if (isRecording) "停止录音" else "语音",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            } else {
                Box(
                    modifier = actionModifier.clickable(onClick = onSend),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_send),
                        contentDescription = "发送",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

/** 引用条：对应 TG ChatActivityEnterView 的 reply preview（左侧 3dp 竖条）。 */
@Composable
private fun ReplyBar(message: ChatMessage, onCancel: () -> Unit) {
    val c = TgTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(c.replyBar)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(c.primary),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = message.authorName,
                fontSize = 13.sp,
                color = c.primary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                text = message.summary,
                fontSize = 13.sp,
                color = c.subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onCancel, modifier = Modifier.size(28.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = "取消引用",
                tint = c.icon,
            )
        }
    }
}
