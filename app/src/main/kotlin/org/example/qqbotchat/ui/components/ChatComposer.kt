package org.example.qqbotchat.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.example.qqbotchat.R
import org.example.qqbotchat.core.ChatMessage
import org.example.qqbotchat.ui.theme.TgMotion
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
    onCancelRecording: () -> Unit = {},
    elapsedSeconds: () -> Long = { 0L },
    modifier: Modifier = Modifier,
) {
    val c = TgTheme.colors
    Column(modifier.fillMaxWidth()) {
        AnimatedContent(replyingTo, contentKey = { it?.id }, label = "reply-preview", transitionSpec = {
            (fadeIn(TgMotion.content()) togetherWith fadeOut(TgMotion.content()))
                .using(SizeTransform { _, _ -> TgMotion.content() })
        }) { reply ->
            reply?.let { ReplyBar(it, onCancelReply) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.Bottom) {
            Row(Modifier.weight(1f).heightIn(min = 50.dp).clip(RoundedCornerShape(25.dp)).background(c.surface),
                verticalAlignment = Alignment.CenterVertically) {
                if (isRecording) {
                    IconButton(onClick = onCancelRecording) {
                        Icon(painterResource(R.drawable.ic_close), stringResource(R.string.cancel_recording), tint = c.error)
                    }
                    RecordingIndicator(elapsedSeconds, Modifier.weight(1f))
                } else {
                    IconButton(onClick = onEmoji) {
                        Icon(painterResource(R.drawable.ic_emoji), stringResource(R.string.emoji), tint = c.icon)
                    }
                    BasicTextField(value = draft, onValueChange = onDraftChange,
                        modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                        textStyle = TextStyle(color = c.name, fontSize = 16.sp),
                        cursorBrush = SolidColor(c.primary), maxLines = 5,
                        decorationBox = { inner ->
                            Box {
                                if (draft.isEmpty()) Text(stringResource(R.string.message_hint), color = c.subtitle, fontSize = 16.sp)
                                inner()
                            }
                        })
                    IconButton(onClick = onAttach) {
                        Icon(painterResource(R.drawable.ic_attach), stringResource(R.string.attach), tint = c.icon)
                    }
                }
            }
            Spacer(Modifier.width(6.dp))
            val send = draft.isNotBlank() && !isRecording
            FilledIconButton(onClick = if (send) onSend else onVoice, shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = if (isRecording) c.error else c.primary,
                    contentColor = c.unreadText), modifier = Modifier.size(50.dp)) {
                Crossfade(targetState = if (isRecording) 2 else if (send) 1 else 0,
                    animationSpec = TgMotion.content(), label = "composer-action") { action ->
                    Icon(painterResource(when (action) { 2 -> R.drawable.ic_send; 1 -> R.drawable.ic_send; else -> R.drawable.ic_mic }),
                        stringResource(when (action) { 2 -> R.string.stop_recording; 1 -> R.string.send; else -> R.string.voice }))
                }
            }
        }
    }
}

@Composable
private fun RecordingIndicator(elapsedSeconds: () -> Long, modifier: Modifier) {
    var seconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) { seconds = elapsedSeconds(); delay(1000) } }
    Text(stringResource(R.string.recording_time, "%02d:%02d".format(seconds / 60, seconds % 60)),
        color = TgTheme.colors.error, fontSize = 15.sp, modifier = modifier, maxLines = 1)
}

@Composable
private fun ReplyBar(message: ChatMessage, onCancel: () -> Unit) {
    val c = TgTheme.colors
    Row(Modifier.fillMaxWidth().background(c.surface).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_reply), null, tint = c.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text(message.authorName, fontSize = 14.sp, color = c.primary, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(message.summary, fontSize = 14.sp, color = c.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onCancel) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.cancel_reply), tint = c.icon) }
    }
}
