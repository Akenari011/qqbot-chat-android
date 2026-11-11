// 设计参考 Telegram for Android (GPL-2.0)
// 聊天页，对应 TG 的 ChatActivity + ChatMessageCell + ChatActivityEnterView。
// 来自 TG 的常量（源码:行号）：SharedConfig.java:323 圆角 17dp、MessageDrawable.java:704-832 气泡 Path、
// ChatMessageCell.java:13726-13728 内边距 11/17dp 与上下 10dp、Theme.java:8133 正文 16sp、
// Theme.java:8506 时间 12sp、ThemeColors.java:20 chat_outBubble=#FFEFFFDE / chat_inBubble=#FFFFFFFF /
// chats_sentCheck=#FF46AA36、日期胶囊 13sp（近似）、ActionBar 高 56dp（tg_dimens.xml）。
package org.example.qqbotchat.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import org.example.qqbotchat.util.AudioRecorder
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.qqbotchat.R
import org.example.qqbotchat.core.ChatMessage
import org.example.qqbotchat.core.MessageParser
import org.example.qqbotchat.ui.components.ChatComposer
import org.example.qqbotchat.ui.components.ChannelToolsMenu
import org.example.qqbotchat.ui.components.ChannelToolsDialog
import org.example.qqbotchat.ui.components.MessageActionSheet
import org.example.qqbotchat.ui.components.MessageBubble
import org.example.qqbotchat.ui.components.TgAvatar
import org.example.qqbotchat.ui.theme.TgDimens
import org.example.qqbotchat.ui.theme.TgTheme
import org.example.qqbotchat.util.dateChip
import org.example.qqbotchat.util.dayKey
import org.example.qqbotchat.vm.ChatUiState

/** 消息列表条目：日期胶囊或一条消息（附 TG 分组圆角标志）。 */
private sealed interface ChatItem {
    data class Date(val iso: String) : ChatItem
    data class Msg(val message: ChatMessage, val fullTop: Boolean, val fullBottom: Boolean) : ChatItem
}

private fun buildChatItems(messages: List<ChatMessage>): List<ChatItem> {
    val out = mutableListOf<ChatItem>()
    var lastDay = ""
    messages.forEachIndexed { index, m ->
        val day = dayKey(m.timestamp)
        if (day != lastDay) {
            out += ChatItem.Date(m.timestamp)
            lastDay = day
        }
        val prev = messages.getOrNull(index - 1)
        val next = messages.getOrNull(index + 1)
        // TG ChatMessageCell：同一组消息只画 6dp 小圆角，组首/组尾画 17dp 完整圆角。
        val fullTop = prev == null || dayKey(prev.timestamp) != day || !MessageParser.isSameGroup(prev, m)
        val fullBottom = next == null || dayKey(next.timestamp) != day || !MessageParser.isSameGroup(m, next)
        out += ChatItem.Msg(m, fullTop, fullBottom)
    }
    return out
}

@Composable
fun ChatScreen(
    state: ChatUiState,
    onBack: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onReply: (ChatMessage) -> Unit,
    onCancelReply: () -> Unit,
    onRecall: (String) -> Unit,
    onSearch: (String) -> Unit,
    onShowActions: (ChatMessage) -> Unit,
    onDismissActions: () -> Unit,
    onCopy: (String) -> Unit,
    onForward: (String) -> Unit,
    onReact: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onButtonClick: (String) -> Unit,
    onOpenMedia: (String) -> Unit,
    onAttachMedia: (ByteArray, String, Int) -> Unit,
    onChannelAction: (org.example.qqbotchat.ui.components.ChannelAction) -> Unit,
    onDismissTools: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = TgTheme.colors
    val context = LocalContext.current
    // 系统文件选择器：读取字节后交给 ViewModel.sendMedia（真实走 QQ 富媒体上传 + msg_type 7）。
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull() ?: "*/*"
            val fileType = when {
                mime.startsWith("image") -> 1
                mime.startsWith("video") -> 2
                mime.startsWith("audio") -> 3
                else -> 4
            }
            val name = queryDisplayName(context, uri) ?: ("file-" + System.currentTimeMillis())
            val bytes = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
            if (bytes != null && bytes.isNotEmpty()) onAttachMedia(bytes, name, fileType)
        }
    }
    // 语音录制：麦克风按钮点第一次开始、再点一次停止并作为 file_type=3 发送。
    val recorder = remember { AudioRecorder(context) }
    var recording by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && recorder.start()) recording = true
    }
    fun toggleRecording() {
        if (recording) {
            val file = recorder.stop()
            recording = false
            if (file != null) {
                val bytes = runCatching { file.readBytes() }.getOrNull()
                if (bytes != null && bytes.isNotEmpty()) {
                    onAttachMedia(bytes, file.name, 3)
                }
            }
        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            if (recorder.start()) recording = true
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    var searchOpen by remember { mutableStateOf(false) }
    var toolsExpanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    // While searching, render only the matching messages instead of the full list.
    val visibleMessages = if (searchOpen && state.searchQuery.isNotBlank()) {
        state.searchResults
    } else {
        state.messages
    }
    val grouped = remember(visibleMessages) { buildChatItems(visibleMessages) }

    LaunchedEffect(grouped.size) {
        if (grouped.isNotEmpty()) listState.animateScrollToItem(grouped.lastIndex)
    }

    Column(modifier = modifier.fillMaxSize().background(c.background)) {
        // ActionBar：返回 + 头像 + 标题/场景 + 搜索（对应 TG ChatActivity 顶栏）。
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
            if (state.scene != "c2c") {
                TgAvatar(state.title, null, TgDimens.ChatAvatarSize)
                Spacer(Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.title,
                    fontSize = TgDimens.NameSize,
                    fontWeight = FontWeight.SemiBold,
                    color = c.actionBarTitle,
                    maxLines = 1,
                )
                Text(
                    text = when (state.scene) {
                        "group" -> "群聊"
                        "channel" -> "频道"
                        else -> "私聊"
                    },
                    fontSize = TgDimens.MessageTimeSize,
                    color = c.subtitle,
                )
            }
            IconButton(onClick = { searchOpen = !searchOpen }) {
                Icon(painterResource(R.drawable.ic_search), "搜索", tint = c.icon)
            }
            // 频道能力「更多」菜单（对应 TG ChatActivity ActionBar 溢出菜单）。
            Box {
                IconButton(onClick = { toolsExpanded = true }) {
                    Icon(painterResource(R.drawable.ic_more), "更多", tint = c.icon)
                }
                ChannelToolsMenu(
                    expanded = toolsExpanded,
                    onDismiss = { toolsExpanded = false },
                    onAction = { action ->
                        toolsExpanded = false
                        onChannelAction(action)
                    },
                )
            }
        }

        if (searchOpen) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearch,
                placeholder = { Text("搜索消息…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(24.dp),
            )
            if (state.searchQuery.isNotBlank()) {
                Text(
                    text = "找到 " + state.searchResults.size + " 条相关消息",
                    fontSize = TgDimens.MessageTimeSize,
                    color = c.subtitle,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState,
        ) {
            items(
                items = grouped,
                key = { item ->
                    when (item) {
                        is ChatItem.Date -> "date-" + item.iso
                        is ChatItem.Msg -> "msg-" + item.message.id
                    }
                },
            ) { item ->
                when (item) {
                    is ChatItem.Date -> DateChip(item.iso)
                    is ChatItem.Msg -> MessageRow(
                        item = item,
                        scene = state.scene,
                        onButtonClick = onButtonClick,
                        onOpenMedia = onOpenMedia,
                    ) { onShowActions(item.message) }
                }
            }
        }

        ChatComposer(
            draft = state.draft,
            onDraftChange = onDraftChange,
            onSend = onSend,
            replyingTo = state.replyingTo,
            onCancelReply = onCancelReply,
            onAttach = { picker.launch("*/*") },
            onEmoji = { onDraftChange(state.draft + "🙂") },
            onVoice = { toggleRecording() },
            isRecording = recording,
        )
    }

    // 频道能力 API 的结果展示。
    state.toolsTitle?.let { title ->
        ChannelToolsDialog(
            title = title,
            items = state.toolsItems,
            onDismiss = onDismissTools,
        )
    }

    // 长按/点击气泡弹出 TG 风格操作面板：表情回应 + 回复/复制/转发/撤回/删除。
    state.actionTarget?.let { message ->
        MessageActionSheet(
            message = message,
            onDismiss = onDismissActions,
            onReply = {
                onReply(message)
                onDismissActions()
            },
            onCopy = {
                onCopy(message.text)
                onDismissActions()
            },
            onForward = {
                onForward(message.id)
                onDismissActions()
            },
            onRecall = {
                onRecall(message.id)
                onDismissActions()
            },
            onDelete = {
                onDelete(message.id)
                onDismissActions()
            },
            onReact = { emoji ->
                onReact(message.id, emoji)
                onDismissActions()
            },
        )
    }
}

@Composable
private fun MessageRow(
    item: ChatItem.Msg,
    scene: String,
    onButtonClick: (String) -> Unit,
    onOpenMedia: (String) -> Unit,
    onClick: () -> Unit,
) {
    val showAvatar = scene != "c2c" && !item.message.fromBot
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = if (item.message.fromBot) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (showAvatar) {
            TgAvatar(item.message.authorName, item.message.authorAvatar, TgDimens.ChatAvatarSize)
            Spacer(Modifier.width(6.dp))
        }
        MessageBubble(
            message = item.message,
            onClick = onClick,
            showAvatar = showAvatar,
            drawFullTop = item.fullTop,
            drawFullBottom = item.fullBottom,
            onButtonClick = onButtonClick,
            onOpenMedia = onOpenMedia,
        )
    }
}

/** 居中日期胶囊：13sp / 22dp（TG chat_serviceBackground，近似值）。 */
@Composable
private fun DateChip(iso: String) {
    val c = TgTheme.colors
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .height(TgDimens.DateChipHeight)
                .clip(RoundedCornerShape(50))
                .background(c.dateChip)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(dateChip(iso), fontSize = TgDimens.DateChipTextSize, color = c.dateChipText)
        }
    }
}

/** 从 content:// URI 取显示文件名（OpenableColumns.DISPLAY_NAME）。 */
private fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
    }
}.getOrNull()
