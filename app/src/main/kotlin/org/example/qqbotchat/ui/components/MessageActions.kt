// 设计参考 Telegram for Android (GPL-2.0)
// 本文件实现「消息长按操作面板 / 引用条 / 可点击链接正文」，视觉规范逐条对标 TG Android 源码：
//   · ChatMessageCell 的 reply bar：引用消息左侧竖条 + 右侧两行（引用名 / 引用文本）。
//     本文件 QuotePreview 即其 Compose 版：左侧 2dp 竖线、右侧两行、半透明底、4dp 圆角、约 36dp 高。
//   · chat_inReplyNameText（Theme.java 引用作者名配色 / chat_messageLinkIn）：
//     QuotePreview 第一行「引用」小字与左侧竖线据此取强调色（接收用蓝色 link，发出用白/浅色）。
//   · Theme.java:8140 chat_quoteTextPaint（13dp 引用文本画笔）：
//     QuotePreview 第二行被引用文本 13sp、单行省略。
//   · chat_botButtonPaint（Theme.java 机器人按钮文字画笔）：
//     MessageActionSheet 操作行 16sp 文字、48dp 行高据此。
//   · Theme.java:8133 正文 16sp / Theme.java:8506 时间 12sp：面板顶部摘要采用 12sp 灰色单行省略。
//   · ThemeColors.java:20 divider=#FFD9D9D9：操作行之间分割线取 TgTheme.colors.divider。
// 说明：MessageBubble.kt 已有 private fun QuoteStrip，本文件的引用条改名为 QuotePreview 以免同包冲突。
// 兼容性：不使用 ModalBottomSheet / LinkAnnotation 等 experimental API，不依赖未声明的 material-icons，
//         链接点击改用 ClickableText + pushStringAnnotation（foundation 1.7 稳定可用）。
package org.example.qqbotchat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.example.qqbotchat.core.ChatMessage
import org.example.qqbotchat.ui.theme.TgDimens
import org.example.qqbotchat.ui.theme.TgTheme

/** 常用表情回应，顺序对齐 TG 的 reactions 快捷栏。 */
private val REACTION_EMOJIS = listOf("👍", "❤️", "😂", "😮", "😢", "🔥")

/** URL 识别：https?://... 或 www. ...（\S+ 表示直到空白为止）。 */
private val URL_REGEX = Regex("(https?://\\S+)|(www\\.\\S+)", RegexOption.IGNORE_CASE)

/** 链接尾部常见标点，不计入 URL 本身，避免把句号/括号并进链接。 */
private val URL_TRAILING_PUNCT = charArrayOf(
    '.', ',', ';', ':', '!', '?', '，', '。', '、', '；', '：', '）', ']', ')',
)

/** pushStringAnnotation 的标签，供点击时取回 URL。 */
private const val URL_TAG = "URL"

/** 操作面板的一行数据；icon 用文字符号，避免依赖未声明的 material-icons 依赖。 */
private class SheetAction(
    val icon: String,
    val label: String,
    val onClick: () -> Unit,
    val danger: Boolean = false,
)

/**
 * 消息长按操作面板：顶部摘要 + 表情回应栏 + 回复/复制/转发/撤回/删除。
 * 用 Dialog 自绘卡片，规避 ModalBottomSheet 的 ExperimentalMaterial3Api 编译风险。
 * 回调只负责派发事件，是否关闭面板由调用方在回调里决定。
 */
@Composable
fun MessageActionSheet(
    message: ChatMessage,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onForward: () -> Unit,
    onRecall: () -> Unit,
    onDelete: () -> Unit,
    onReact: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = TgTheme.colors

    // 撤回仅对机器人（自己发出）的消息显示；删除用 error 强调色。
    val actions = buildList {
        add(SheetAction("↩", "回复", onReply))
        add(SheetAction("📋", "复制", onCopy))
        add(SheetAction("↪", "转发", onForward))
        if (message.fromBot) add(SheetAction("↶", "撤回", onRecall))
        add(SheetAction("🗑", "删除", onDelete, danger = true))
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(c.surface)
                .padding(vertical = 8.dp),
        ) {
            // 顶部消息摘要：12sp 灰色、单行省略（Theme.java:8506 / chat_inTimeText）。
            Text(
                text = message.summary,
                fontSize = TgDimens.MessageTimeSize,
                color = c.subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
            SheetDivider(c.divider)

            // 一排常用表情回应：点击回调 onReact(emoji)。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (emoji in REACTION_EMOJIS) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onReact(emoji) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = emoji, fontSize = 20.sp)
                    }
                }
            }
            SheetDivider(c.divider)

            // 操作行：16sp 文字、48dp 行高、行间 1dp 分割线（chat_botButtonPaint）。
            actions.forEachIndexed { index, action ->
                ActionRow(
                    icon = action.icon,
                    label = action.label,
                    color = if (action.danger) c.error else c.actionBarTitle,
                    onClick = action.onClick,
                )
                if (index != actions.lastIndex) SheetDivider(c.divider)
            }
        }
    }
}

/** 单个操作行：左侧文字符号 + 右侧文字标签，整行可点击，固定 48dp 行高。 */
@Composable
private fun ActionRow(
    icon: String,
    label: String,
    color: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = icon,
            fontSize = 18.sp,
            color = color,
            modifier = Modifier.width(28.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(text = label, fontSize = TgDimens.MessageTextSize, color = color)
    }
}

/** 1dp 分割线，颜色取 TgTheme.colors.divider（ThemeColors.java:20 divider）。 */
@Composable
private fun SheetDivider(color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color),
    )
}

/**
 * TG 引用条（ChatMessageCell reply bar）。
 * 因 MessageBubble.kt 已有 private QuoteStrip，此处改名 QuotePreview 避免同包重名。
 * @param quoted   被引用文本，第二行 13sp 单行省略
 * @param outgoing 发出消息用白/浅色，接收消息用蓝色 link
 */
@Composable
fun QuotePreview(quoted: String, outgoing: Boolean, modifier: Modifier = Modifier) {
    val c = TgTheme.colors
    // 发出消息用白/浅色，接收消息用蓝色 link（chat_inReplyNameText / chat_messageLinkIn）。
    val accent = if (outgoing) Color.White else c.link
    val bodyColor = if (outgoing) c.outBubbleText.copy(alpha = 0.85f) else c.subtitle

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (outgoing) Color(0x33FFFFFF) else Color(0x0D000000))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左侧 2dp 竖线（ChatMessageCell reply bar）。
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(accent),
        )
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            // 第一行「引用」小字；本函数无作者名入参，故显示默认标签（对应 chat_inReplyNameText）。
            Text(
                text = "引用",
                fontSize = 11.sp,
                color = accent,
                maxLines = 1,
            )
            // 第二行被引用文本：13sp（Theme.java:8140 chat_quoteTextPaint）、单行省略。
            Text(
                text = quoted,
                fontSize = 13.sp,
                color = bodyColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 可点击链接正文：https?://... 与 www. ... 识别为蓝色下划线链接，
 * 点击时通过 LocalUriHandler.openUri 打开；普通文本沿用传入的 color / fontSize。
 * 用 ClickableText + pushStringAnnotation（getStringAnnotations），不触碰 experimental 的 LinkAnnotation。
 */
@Composable
fun LinkifiedText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val linkColor = TgTheme.colors.link

    // 把链接片段切成带 URL 注解的区间，链接片段着蓝色并加下划线。
    val annotated = buildAnnotatedString {
        var cursor = 0
        for (match in URL_REGEX.findAll(text)) {
            append(text.substring(cursor, match.range.first))
            val full = match.value
            val url = full.trimEnd(*URL_TRAILING_PUNCT)
            val tail = full.substring(url.length)
            pushStringAnnotation(tag = URL_TAG, annotation = url)
            withStyle(
                SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
            ) {
                append(url)
            }
            pop()
            if (tail.isNotEmpty()) append(tail)
            cursor = match.range.last + 1
        }
        append(text.substring(cursor))
    }

    // 普通文本使用调用方给定的 color / fontSize。
    ClickableText(
        text = annotated,
        modifier = modifier,
        style = TextStyle(color = color, fontSize = fontSize),
        onClick = { offset ->
            annotated
                .getStringAnnotations(tag = URL_TAG, start = offset, end = offset)
                .firstOrNull()
                ?.let { annotation ->
                    val raw = annotation.item
                    val target = if (raw.startsWith("http", ignoreCase = true)) {
                        raw
                    } else {
                        "https://$raw"
                    }
                    // 无浏览器可处理该 Intent 时静默忽略，避免崩溃。
                    runCatching { uriHandler.openUri(target) }
                }
        },
    )
}
