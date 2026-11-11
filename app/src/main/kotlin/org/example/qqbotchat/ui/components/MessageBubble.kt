// 设计参考 Telegram for Android (GPL-2.0)
// 本文件实现消息气泡，背景由 ui/tg/TgBubblePath.kt（MessageDrawable.java:704-832 generatePath 的 Kotlin 版）
// 用 android.graphics.Path + nativeCanvas 绘制，等价于 TG 运行时生成 NinePatchDrawable 的做法。
// 【逐条源码出处：文件:行号】
//   · SharedConfig.java:323 / MessageDrawable.java:610-616,704-832  圆角 17dp、相邻小圆角 6dp、
//     尾巴内圆角 6dp、绘制内缩 2dp、尾巴水平偏移 8dp、尖角 2.6dp
//   · ChatMessageCell.java:13726-13728  发出左右内边距 11dp / 接收 17dp、上下 10dp、
//     总高 textHeight+19.5dp、气泡宽 = 文本宽 + 31dp、最大宽度 min(parentW,displayH)-80dp-(有头像42dp)
//   · ThemeColors.java:20  chat_inBubble=#FFFFFFFF、chat_outBubble=#FFEFFFDE（可选蓝渐变 #57B2E0→#0088FF）、
//     chat_messageTextIn/Out=#FF000000、chat_inTimeText=#FFA1AAB3、chat_outTimeText=#FF70B15C、
//     chats_sentCheck=#FF46AA36（双勾）、chat_messageLinkIn/Out=#FF2678B6（作者名/链接）
//   · SharedConfig.java:323 / Theme.java:8133 正文 16sp；Theme.java:8506 时间 12sp；作者名 14sp bold
//   · DialogCell.java:1298-1313 会话名字号；日期胶囊 13sp（近似，由 ChatScreen 绘制）
package org.example.qqbotchat.ui.components

import android.graphics.Paint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.qqbotchat.core.ChatMessage
import org.example.qqbotchat.ui.theme.TgDimens
import org.example.qqbotchat.ui.theme.TgTheme
import org.example.qqbotchat.ui.tg.TgBubbleDefaults
import org.example.qqbotchat.ui.tg.TgBubblePath
import org.example.qqbotchat.util.clockTime

@Composable
fun MessageBubble(
    message: ChatMessage,
    onClick: () -> Unit,
    showAvatar: Boolean = false,
    drawFullTop: Boolean = true,
    drawFullBottom: Boolean = true,
    onButtonClick: (String) -> Unit = {},
    onOpenMedia: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val c = TgTheme.colors
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val outgoing = message.fromBot

    // 最大宽度：TG 公式 min(parentW,displayH)-80dp-(有头像42dp)，并与 iOS 规格 0.85 屏宽取较小值。
    val screenW = configuration.screenWidthDp.dp
    val screenH = configuration.screenHeightDp.dp
    val tgMax = minOf(screenW, screenH) -
        TgDimens.MaxWidthBaseDeduction -
        (if (showAvatar) TgDimens.MaxWidthAvatarDeduction else 0.dp)
    val maxBubbleWidth = minOf(screenW * TgDimens.BubbleMaxWidthFraction, tgMax)

    // MessageDrawable.java 的几何参数：dp -> px。
    val radPx = with(density) { TgDimens.BubbleRadius.toPx() }
    val nearRadPx = with(density) { TgDimens.BubbleNearRadius.toPx() }
    val smallRadPx = with(density) { TgDimens.BubbleSmallRadius.toPx() }
    val paddingPx = with(density) { TgDimens.BubbleDrawPadding.toPx() }
    val tailOffsetPx = with(density) { TgDimens.BubbleTailOffset.toPx() }
    val tipPx = with(density) { TgDimens.BubbleTip.toPx() }

    val metaColor = if (outgoing) c.outMeta else c.inMeta
    val body = buildAnnotatedString {
        if (message.status == "recalled") {
            withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = metaColor)) {
                append("消息已撤回")
            }
        } else {
            append(message.summary)
            append("   ")
            // 时间 12sp（Theme.java:8506），与正文同一行末尾（TG 气泡右下角）。
            withStyle(SpanStyle(fontSize = TgDimens.MessageTimeSize, color = metaColor)) {
                append(clockTime(message.timestamp))
            }
            if (outgoing) {
                append("  ")
                // 已读双勾：chats_sentCheck #FF46AA36（ThemeColors.java:20）。
                withStyle(
                    SpanStyle(
                        fontSize = TgDimens.MessageTimeSize,
                        color = c.sentCheck,
                        fontWeight = FontWeight.Bold,
                    ),
                ) {
                    append("✓✓")
                }
            }
        }
    }

    Column(
        modifier = modifier
            .widthIn(max = maxBubbleWidth)
            // 直接用 TG generatePath() 生成的 android.graphics.Path 绘制气泡（含尾巴与分组小圆角）。
            .drawBehind {
                val path = TgBubblePath.generatePath(
                    width = size.width,
                    height = size.height,
                    rad = radPx,
                    nearRad = nearRadPx,
                    smallRad = smallRadPx,
                    padding = paddingPx,
                    tailOffset = tailOffsetPx,
                    tip = tipPx,
                    isOut = outgoing,
                    drawFullTop = drawFullTop,
                    drawFullBottom = drawFullBottom,
                )
                val native = drawContext.canvas.nativeCanvas
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                val start = if (outgoing) c.outBubbleStart else c.inBubble
                val end = if (outgoing) c.outBubbleEnd else c.inBubble
                if (start != end) {
                    paint.shader = android.graphics.LinearGradient(
                        0f, 0f, size.width, size.height,
                        start.toArgb(), end.toArgb(),
                        android.graphics.Shader.TileMode.CLAMP,
                    )
                } else {
                    paint.color = start.toArgb()
                }
                native.drawPath(path, paint)
                // TG's chat_inBubble is pure white on a white chat background;
                // MessageDrawable.java:396-432 separates them with a shadow layer.
                // We approximate it with a 1px stroke (inBubbleBorder) so the
                // incoming bubble stays visible without changing TG's colors.
                if (!outgoing) {
                    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = with(density) { 1.dp.toPx() }
                        color = c.inBubbleBorder.toArgb()
                    }
                    native.drawPath(path, border)
                }
            }
            .clickable(onClick = onClick)
            .padding(
                start = if (outgoing) TgDimens.BubblePaddingOutHorizontal else TgDimens.BubblePaddingInHorizontal,
                end = if (outgoing) TgDimens.BubblePaddingOutHorizontal else TgDimens.BubblePaddingInHorizontal,
                top = TgDimens.BubblePaddingVertical,
                bottom = TgDimens.BubblePaddingVertical +
                    (if (drawFullBottom) TgDimens.BubbleSmallRadius else 0.dp),
            ),
    ) {
        // 群聊 / 频道接收消息显示作者名：14sp bold（TG 作者名字号）。
        if (showAvatar && !outgoing) {
            Text(
                text = message.authorName,
                fontSize = TgDimens.AuthorNameSize,
                fontWeight = FontWeight.Bold,
                color = c.link,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
        }
        if (!message.quotedText.isNullOrBlank()) {
            QuotePreview(quoted = message.quotedText!!, outgoing = outgoing)
            Spacer(Modifier.height(4.dp))
        }
        val hasMedia = message.isImage || message.isVideo || message.isAudio || message.isFile
        if (hasMedia) {
            MediaContent(
                message = message,
                outgoing = outgoing,
                onClickVideo = onOpenMedia,
                onClickAudio = onOpenMedia,
                onClickFile = onOpenMedia,
                onClickImage = onOpenMedia,
            )
        } else {
            val hasLink = message.text.contains("http://") ||
                message.text.contains("https://") || message.text.contains("www.")
            if (hasLink) {
                LinkifiedText(
                    text = message.text,
                    color = if (outgoing) c.outBubbleText else c.inBubbleText,
                    fontSize = TgDimens.MessageTextSize,
                )
                // 时间 / 双勾另起一行，避免 buildAnnotatedString 的 span 被链接解析覆盖。
                Text(
                    text = body.subSequence(message.text.length, body.length),
                    fontSize = TgDimens.MessageTimeSize,
                    color = metaColor,
                )
            } else {
                Text(
                    text = body,
                    fontSize = TgDimens.MessageTextSize,
                    color = if (outgoing) c.outBubbleText else c.inBubbleText,
                )
            }
        }
        if (message.buttons.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            KeyboardButtons(
                buttons = message.buttons,
                onButtonClick = onButtonClick,
                outgoing = outgoing,
            )
        }
        if (message.reactions.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            ReactionBadges(reactions = message.reactions)
        }
    }
}
