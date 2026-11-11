// 设计参考 Telegram for Android (GPL-2.0)
// 本文件实现媒体消息内容（图片 / 视频 / 语音 / 文件）、内联键盘按钮与反应胶囊，
// 按 TG ChatMessageCell / Theme 的常量以 Jetpack Compose 重写；媒体上的时间戳取 chat_mediaTimeText 白色。
// 【逐条源码出处：文件:行号】
//   · ChatMessageCell.java:6887,13726-13728  气泡最大宽度 min(parentW,displayH)-dp(80+有头像42)、
//     左右内边距 发出 11dp / 接收 17dp、上下 10dp；媒体按气泡宽度上限缩放
//     （本文件图片上限 260dp 宽 / 300dp 高、视频固定 240x160dp、圆角 12dp 近似
//      MessageDrawable.java:704-832 / SharedConfig.java:323 的 17dp 气泡圆角）
//   · ChatMessageCell.java:12268,12273  时间戳贴气泡右下角（发出 layoutWidth-timeW-dp(38.5)）
//   · ThemeColors.java:436  chat_mediaTimeText = 0xFFFFFFFF（媒体上的时间/信息文字为白色，即 onMedia）
//   · ThemeColors.java:256  chats_sentCheck = 0xFF46AA36（已读双勾）
//   · Theme.java:8483      chat_infoPaint dp(12)（媒体信息文字 12sp）
//   · Theme.java:8506      chat_timePaint dp(12)（气泡时间戳 12sp）
//   · Theme.java:8493-8494 chat_botButtonPaint dp(15)（内联键盘按钮字号 15sp）
//   · Theme.java:8486      文件名 / 按钮 dp(15)
//   · ThemeColors.java:14  TELEGRAM_COLOR 0xFF229AF0；:360-361 chat_messageLinkIn/Out 0xFF2678B6
//     （键盘按钮半透明蓝底、蓝色文字）
package org.example.qqbotchat.ui.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import org.example.qqbotchat.core.ChatMessage
import org.example.qqbotchat.ui.theme.TgDimens
import org.example.qqbotchat.ui.theme.TgTheme
import org.example.qqbotchat.util.clockTime

// ---- TG 常量对应的固定色值（只使用 TgTheme.colors 已列出的字段）----
/** ThemeColors.java:436 chat_mediaTimeText = 0xFFFFFFFF：媒体上的时间/信息文字统一用白色。 */
private val OnMedia = Color.White
/** 时间戳底衬：TG 在媒体上先画半透明暗色圆角再写白色时间（chat_mediaTimeText）。 */
private val MediaTimeScrim = Color(0x66000000)
/** 视频 / 语音 / 文件卡片的深色底，保证白色 onMedia 文字可读。 */
private val MediaDarkSurface = Color(0xFF24313D)
/** ThemeColors.java:14 TELEGRAM_COLOR 0xFF229AF0（键盘按钮半透明蓝底）。 */
private val BotButtonBackground = Color(0x33229AF0)
/** ThemeColors.java:360-361 chat_messageLinkIn/Out 0xFF2678B6（键盘按钮蓝色文字）。 */
private val BotButtonText = Color(0xFF2678B6)
/** 反应胶囊半透明底。 */
private val ReactionBackground = Color(0x1F000000)

/** 媒体圆角与尺寸上限，见文件头 ChatMessageCell / MessageDrawable 源码出处。 */
private val MediaCorner = 12.dp
private val MediaMaxWidth = 260.dp
private val MediaMaxHeight = 300.dp

/**
 * 媒体消息内容：按 imageUrl / videoUrl / audioUrl / fileName 的优先级只渲染一种媒体，
 * 并在媒体下方（或内部右下）绘制 12sp 时间戳与已读双勾。
 * 内联键盘与反应由调用方在气泡外分别用 [KeyboardButtons] / [ReactionBadges] 渲染。
 */
@Composable
fun MediaContent(
    message: ChatMessage,
    outgoing: Boolean,
    modifier: Modifier = Modifier,
    onClickVideo: (String) -> Unit = {},
    onClickAudio: (String) -> Unit = {},
    onClickFile: (String) -> Unit = {},
    onClickImage: (String) -> Unit = {},
) {
    Column(modifier = modifier) {
        when {
            message.imageUrl != null -> ImageMedia(
                url = message.imageUrl!!,
                localPath = message.localImage,
                message = message,
                outgoing = outgoing,
                onClickImage = onClickImage,
            )
            message.videoUrl != null -> VideoMedia(
                url = message.videoUrl!!,
                message = message,
                outgoing = outgoing,
                onClickVideo = onClickVideo,
            )
            message.audioUrl != null -> AudioMedia(
                url = message.audioUrl!!,
                message = message,
                outgoing = outgoing,
                onClickAudio = onClickAudio,
            )
            message.fileName != null -> FileMedia(
                name = message.fileName!!,
                message = message,
                outgoing = outgoing,
                onClickFile = onClickFile,
            )
        }
    }
}

/**
 * 图片：Coil AsyncImage 加载，协议相对地址（//host）补 https:；尺寸上限 260dp 宽 / 300dp 高，
 * 圆角 12dp、clipToBounds；加载中盖半透明占位 Box，失败显示「图片加载失败」。
 */
@Composable
private fun ImageMedia(
    url: String,
    localPath: String?,
    message: ChatMessage,
    outgoing: Boolean,
    onClickImage: (String) -> Unit,
) {
    // 自己发出的图片优先用本地文件预览（发送前后都能看到真实图片）。
    val resolvedUrl = localPath?.takeIf { it.isNotBlank() } ?: normalizeUrl(url)
    // 本地待发送媒体用 "local" 占位（ViewModel.sendMedia 落库时写入），
    // 不应交给 Coil 走网络加载而误报失败；直接显示「图片 · 发送中」占位。
    val isLocalPlaceholder = url == "local" || (!resolvedUrl.startsWith("http") &&
        !resolvedUrl.startsWith("content") && !resolvedUrl.startsWith("file"))
    // 0=加载中、1=成功、2=失败：由 Coil 状态回调切换占位与失败文案。
    var loadState by remember(url) { mutableStateOf(0) }
    val imageBounds = Modifier
        .widthIn(min = 200.dp, max = MediaMaxWidth)
        .heightIn(min = 140.dp, max = MediaMaxHeight)

    Box(
        modifier = imageBounds
            .clip(RoundedCornerShape(MediaCorner))
            .clipToBounds(),
    ) {
        if (isLocalPlaceholder) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color(0xFFDCE3EB)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "🖼", fontSize = 32.sp, color = Color(0xFF5A6B7B))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (message.status == "sending" || message.status == "failed")
                            "图片 · " + when (message.status) { "failed" -> "发送失败"; else -> "发送中" }
                        else "图片",
                        fontSize = TgDimens.MessageTimeSize,
                        color = Color(0xFF5A6B7B),
                    )
                }
            }
        } else {
            AsyncImage(
                model = resolvedUrl,
                contentDescription = "图片",
                contentScale = ContentScale.FillBounds,
                clipToBounds = true,
                onLoading = { loadState = 0 },
                onSuccess = { loadState = 1 },
                onError = { loadState = 2 },
                modifier = imageBounds
                    .clip(RoundedCornerShape(MediaCorner))
                    .clickable { onClickImage(resolvedUrl) },
            )
            // 加载中：半透明占位 Box（matchParentSize 不参与决定 Box 尺寸）。
            if (loadState == 0) {
                Box(modifier = Modifier.matchParentSize().background(Color(0x33000000)))
            }
            // 失败：暗色蒙层 + 「图片加载失败」。
            if (loadState == 2) {
                Box(
                    modifier = Modifier.matchParentSize().background(Color(0x66000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "图片加载失败",
                        fontSize = TgDimens.MessageTimeSize,
                        color = OnMedia,
                    )
                }
            }
        }
        // chat_mediaTimeText 白色时间 + 双勾，压在图片右下角（ChatMessageCell.java:12268）。
        MediaMetaRow(
            message = message,
            outgoing = outgoing,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(6.dp),
        )
    }
}

/** 视频：240x160dp 深色底 + 居中播放三角 + 右下「视频」标签，整块可点。 */
@Composable
private fun VideoMedia(
    url: String,
    message: ChatMessage,
    outgoing: Boolean,
    onClickVideo: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .size(width = 240.dp, height = 160.dp)
            .clip(RoundedCornerShape(MediaCorner))
            .clipToBounds()
            .background(MediaDarkSurface)
            .clickable { onClickVideo(url) },
    ) {
        // 自绘播放三角（避免依赖 material-icons-extended 的 PlayCircle）。
        PlayGlyph(modifier = Modifier.size(48.dp).align(Alignment.Center))
        // 右下角「视频」标签。
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(6.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MediaTimeScrim)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(text = "视频", fontSize = TgDimens.MessageTimeSize, color = OnMedia)
        }
        // 时间戳放左下角，避免与右下「视频」标签重叠。
        MediaMetaRow(
            message = message,
            outgoing = outgoing,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp),
        )
    }
}

/** 语音：播放图标 + 高低不同的波形竖条 + mm:ss 时长，整块可点。 */
@Composable
private fun AudioMedia(
    url: String,
    message: ChatMessage,
    outgoing: Boolean,
    onClickAudio: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .widthIn(max = MediaMaxWidth)
            .clip(RoundedCornerShape(MediaCorner))
            .clipToBounds()
            .background(MediaDarkSurface)
            .clickable { onClickAudio(url) }
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayGlyph(modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(8.dp))
            // 波形占位：几个高低不同的竖条 Bar（TG 语音波形按振幅绘制，这里用静态占位）。
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                listOf(8.dp, 16.dp, 11.dp, 20.dp, 14.dp, 9.dp, 17.dp).forEach { barHeight ->
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(barHeight)
                            .clip(RoundedCornerShape(2.dp))
                            .background(OnMedia.copy(alpha = 0.85f)),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            // 时长 mm:ss（ChatMessage.duration 单位为秒）。
            Text(
                text = formatDuration(message.duration),
                fontSize = TgDimens.MessageTimeSize,
                color = OnMedia,
            )
        }
        Spacer(Modifier.height(6.dp))
        // 时间戳在内容下方右对齐（chat_mediaTimeText 白色）。
        MediaMetaRow(
            message = message,
            outgoing = outgoing,
            modifier = Modifier.align(Alignment.End),
        )
    }
}

/** 文件：文件图标 + 单行省略的 fileName + 「文件」小字，整块可点。 */
@Composable
private fun FileMedia(
    name: String,
    message: ChatMessage,
    outgoing: Boolean,
    onClickFile: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .widthIn(max = MediaMaxWidth)
            .clip(RoundedCornerShape(MediaCorner))
            .clipToBounds()
            .background(MediaDarkSurface)
            .clickable { onClickFile(name) }
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 自绘文档图标：带折角的矩形，避免依赖不存在的 drawable 资源。
            Canvas(modifier = Modifier.size(34.dp)) {
                val w = size.width
                val h = size.height
                val fold = w * 0.28f
                val document = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(w - fold, 0f)
                    lineTo(w, fold)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                drawPath(document, color = OnMedia)
                val corner = Path().apply {
                    moveTo(w - fold, 0f)
                    lineTo(w, fold)
                    lineTo(w - fold, fold)
                    close()
                }
                drawPath(corner, color = MediaDarkSurface)
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.widthIn(max = 180.dp)) {
                Text(
                    text = name,
                    fontSize = TgDimens.MessageTextSize,
                    color = OnMedia,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "文件",
                    fontSize = TgDimens.MessageTimeSize,
                    color = OnMedia.copy(alpha = 0.7f),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        MediaMetaRow(
            message = message,
            outgoing = outgoing,
            modifier = Modifier.align(Alignment.End),
        )
    }
}

/**
 * 媒体时间戳：12sp（Theme.java:8483 chat_infoPaint dp(12) / :8506 chat_timePaint dp(12)），
 * 白色 chat_mediaTimeText + 半透明暗底衬；发出时追加 chats_sentCheck 双勾。
 */
@Composable
private fun MediaMetaRow(
    message: ChatMessage,
    outgoing: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MediaTimeScrim)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = clockTime(message.timestamp),
            fontSize = TgDimens.MessageTimeSize,
            color = OnMedia,
        )
        if (outgoing) {
            Spacer(Modifier.width(3.dp))
            Text(
                text = "✓✓",
                fontSize = TgDimens.MessageTimeSize,
                fontWeight = FontWeight.Bold,
                color = TgTheme.colors.sentCheck,
            )
        }
    }
}

/** 自绘播放三角：半透明白圆 + 白色三角，供视频与语音共用。 */
@Composable
private fun PlayGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawCircle(color = Color(0x55FFFFFF), radius = size.minDimension / 2f)
        val triangle = Path().apply {
            moveTo(size.width * 0.40f, size.height * 0.28f)
            lineTo(size.width * 0.40f, size.height * 0.72f)
            lineTo(size.width * 0.76f, size.height * 0.50f)
            close()
        }
        drawPath(triangle, color = OnMedia)
    }
}

/** 协议相对地址（//host/path）补 https:，其余原样返回。 */
private fun normalizeUrl(url: String): String =
    if (url.startsWith("//")) "https:$url" else url

/** 秒数格式化为 mm:ss（语音时长，ChatMessage.duration 为 Double?）。 */
private fun formatDuration(seconds: Double?): String {
    val total = (seconds ?: 0.0).coerceAtLeast(0.0).toLong()
    val mm = (total / 60).toString().padStart(2, '0')
    val ss = (total % 60).toString().padStart(2, '0')
    return "$mm:$ss"
}

/** key 为纯数字时按 Unicode 标量值转 emoji，否则原样返回。 */
private fun reactionEmoji(key: String): String {
    if (key.isEmpty() || !key.all { it in '0'..'9' }) return key
    val codePoint = key.toIntOrNull() ?: key.toIntOrNull(16) ?: return key
    if (codePoint <= 0 || codePoint > 0x10FFFF || codePoint in 0xD800..0xDFFF) return key
    return try {
        String(Character.toChars(codePoint))
    } catch (e: IllegalArgumentException) {
        key
    }
}

/**
 * 内联键盘按钮：一列、每行一个，圆角 8dp、半透明蓝底、蓝色文字，
 * 字号 15sp（Theme.java:8493-8494 chat_botButtonPaint dp(15)），按钮间距 4dp，
 * 宽度与气泡一致（fillMaxWidth），由调用方放在气泡下方。
 */
@Composable
fun KeyboardButtons(
    buttons: List<String>,
    onButtonClick: (String) -> Unit,
    outgoing: Boolean,
    modifier: Modifier = Modifier,
) {
    // 发出方按钮底色略深一点，保留 outgoing 语义但不改变 TG 的蓝色基调。
    val background = BotButtonBackground.copy(alpha = if (outgoing) 0.24f else 0.18f)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        buttons.forEach { label ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(background)
                    .clickable { onButtonClick(label) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = 15.sp,
                    color = BotButtonText,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 反应胶囊：横向排列、间距 4dp，emoji + 数量 12sp，半透明背景。 */
@Composable
fun ReactionBadges(reactions: Map<String, Int>, modifier: Modifier = Modifier) {
    val c = TgTheme.colors
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        reactions.forEach { (key, count) ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(ReactionBackground)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = reactionEmoji(key),
                    fontSize = 12.sp,
                    color = c.inBubbleText,
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = count.toString(),
                    fontSize = 12.sp,
                    color = c.inMeta,
                )
            }
        }
    }
}
