// Telegram-inspired chat: stable timeline, contextual actions and short transitions.
package org.example.qqbotchat.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.example.qqbotchat.R
import org.example.qqbotchat.core.ChatMessage
import org.example.qqbotchat.core.Conversation
import org.example.qqbotchat.core.MessageParser
import org.example.qqbotchat.ui.components.*
import org.example.qqbotchat.ui.theme.TgDimens
import org.example.qqbotchat.ui.theme.TgMotion
import org.example.qqbotchat.ui.theme.TgTheme
import org.example.qqbotchat.util.dateChip
import org.example.qqbotchat.util.dayKey
import org.example.qqbotchat.vm.ChatNotice
import org.example.qqbotchat.vm.ChatUiState

private sealed interface ChatItem {
    data class Date(val iso: String) : ChatItem
    data class Msg(val message: ChatMessage, val fullTop: Boolean, val fullBottom: Boolean) : ChatItem
}

private fun buildChatItems(messages: List<ChatMessage>): List<ChatItem> = buildList {
    var lastDay = ""
    messages.forEachIndexed { index, message ->
        val day = dayKey(message.timestamp)
        if (day != lastDay) { add(ChatItem.Date(message.timestamp)); lastDay = day }
        val prev = messages.getOrNull(index - 1)
        val next = messages.getOrNull(index + 1)
        add(ChatItem.Msg(message,
            prev == null || dayKey(prev.timestamp) != day || !MessageParser.isSameGroup(prev, message),
            next == null || dayKey(next.timestamp) != day || !MessageParser.isSameGroup(message, next)))
    }
}.reversed()

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
    onForwardConfirm: (String) -> Unit,
    onDismissForward: () -> Unit,
    onRetry: (String) -> Unit,
    onDismissNotice: () -> Unit,
    onReact: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onButtonClick: (String) -> Unit,
    onOpenMedia: (String) -> Unit,
    onAttachMedia: (ByteArray, String, Int, String?) -> Unit,
    onChannelAction: (ChannelAction) -> Unit,
    onDismissTools: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = TgTheme.colors
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val media = rememberChatAttachments(onAttachMedia)
    var searchOpen by rememberSaveable(state.conversationId) { mutableStateOf(false) }
    var toolsExpanded by remember { mutableStateOf(false) }
    var attachOpen by remember { mutableStateOf(false) }
    var emojiOpen by rememberSaveable { mutableStateOf(false) }
    var forwardTarget by remember { mutableStateOf<Conversation?>(null) }
    var jumpTo by remember { mutableStateOf<String?>(null) }
    var imagePreview by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val visibleMessages = if (searchOpen && state.searchQuery.isNotBlank()) state.searchResults else state.messages
    val grouped = remember(visibleMessages) { buildChatItems(visibleMessages) }
    val awayFromBottom by remember { derivedStateOf { listState.firstVisibleItemIndex > 1 } }
    var previousLastId by remember(state.conversationId) { mutableStateOf(state.messages.lastOrNull()?.id) }
    LaunchedEffect(state.messages.lastOrNull()?.id) {
        val last = state.messages.lastOrNull()
        // Reverse layout preserves the reader's anchor when incoming messages arrive.
        // Sending is an explicit request to return to the end of the conversation.
        if (last?.id != previousLastId && last?.fromBot == true && !searchOpen) listState.animateScrollToItem(0)
        previousLastId = last?.id
    }
    val missingQuote = stringResource(R.string.quoted_message_missing)
    LaunchedEffect(jumpTo, grouped) {
        val target = jumpTo ?: return@LaunchedEffect
        val index = grouped.indexOfFirst { it is ChatItem.Msg && (it.message.id == target || it.message.serverId == target) }
        if (index >= 0) listState.animateScrollToItem(index) else snackbar.showSnackbar(missingQuote)
        jumpTo = null
    }
    val noticeText = state.error ?: state.notice?.let {
        stringResource(when (it) {
            ChatNotice.FORWARDED -> R.string.forward_queued
            ChatNotice.MEDIA_RESELECT -> R.string.media_reselect
            ChatNotice.TEXT_FORWARD_ONLY -> R.string.text_forward_only
            else -> R.string.operation_failed
        })
    }
    val mediaError = media.error?.let { stringResource(it) }
    LaunchedEffect(noticeText) { noticeText?.let { snackbar.showSnackbar(it); onDismissNotice() } }
    LaunchedEffect(mediaError) { mediaError?.let { snackbar.showSnackbar(it); media.dismissError() } }
    BackHandler(searchOpen || emojiOpen || media.recording) {
        when {
            media.recording -> media.cancelRecording()
            emojiOpen -> emojiOpen = false
            else -> { searchOpen = false; onSearch("") }
        }
    }

    Column(modifier.fillMaxSize().background(c.wallpaper).navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().background(c.actionBar).statusBarsPadding().height(56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back), tint = c.icon) }
            TgAvatar(state.title, null, 40.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(state.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = c.name,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(if (state.demoMode) R.string.demo_mode else when (state.scene) {
                    "group" -> R.string.group_chats; "channel" -> R.string.channels; else -> R.string.private_chats
                }), fontSize = 13.sp, color = c.subtitle)
            }
            IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) onSearch("") }) {
                Icon(painterResource(R.drawable.ic_search), stringResource(R.string.search), tint = c.icon)
            }
            if (state.scene == "channel") Box {
                IconButton(onClick = { toolsExpanded = true }) {
                    Icon(painterResource(R.drawable.ic_more), stringResource(R.string.more), tint = c.icon)
                }
                ChannelToolsMenu(toolsExpanded, onDismiss = { toolsExpanded = false },
                    onAction = { toolsExpanded = false; onChannelAction(it) })
            }
        }
        AnimatedVisibility(searchOpen, enter = expandVertically(TgMotion.content()) + fadeIn(TgMotion.content()),
            exit = shrinkVertically(TgMotion.content()) + fadeOut(TgMotion.content())) {
            Column(Modifier.background(c.surface).padding(horizontal = 16.dp)) {
                ChatSearchField(state.searchQuery, onSearch, stringResource(R.string.search_messages),
                    onClose = { searchOpen = false; onSearch("") })
                if (state.searchQuery.isNotBlank()) Text(stringResource(R.string.search_count, state.searchResults.size),
                    color = c.subtitle, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            ChatWallpaper(Modifier.matchParentSize())
            LazyColumn(Modifier.fillMaxSize(), state = listState, reverseLayout = true,
                contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp)) {
                items(grouped, key = { when (it) {
                    is ChatItem.Date -> "date-" + dayKey(it.iso)
                    is ChatItem.Msg -> "msg-" + it.message.id
                } }, contentType = { if (it is ChatItem.Date) "date" else "message" }) { item ->
                    when (item) {
                        is ChatItem.Date -> DateChip(item.iso)
                        is ChatItem.Msg -> MessageRow(item, state.scene, onButtonClick, { url ->
                            if (item.message.isImage && url.isNotBlank() && url != "local") imagePreview = url
                            else onOpenMedia(url)
                        },
                            onActions = { onShowActions(item.message) }, onReply = { onReply(item.message) },
                            onQuote = { target -> searchOpen = false; onSearch(""); jumpTo = target },
                            onRetry = { onRetry(item.message.id) },
                            modifier = Modifier.animateItem(fadeInSpec = TgMotion.content(), placementSpec = TgMotion.content(),
                                fadeOutSpec = TgMotion.content()))
                    }
                }
            }
            if (visibleMessages.isEmpty()) Text(stringResource(if (state.searchQuery.isNotBlank()) R.string.no_search_results else R.string.no_messages),
                modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(20.dp)).background(c.dateChip).padding(16.dp),
                color = c.dateChipText)
            androidx.compose.animation.AnimatedVisibility(awayFromBottom, modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                enter = fadeIn(TgMotion.content()), exit = fadeOut(TgMotion.content())) {
                SmallFloatingActionButton(onClick = { scope.launch { listState.animateScrollToItem(0) } },
                    shape = CircleShape, containerColor = c.surface, contentColor = c.primary) {
                    Icon(painterResource(R.drawable.ic_down), stringResource(R.string.latest_messages))
                }
            }
            SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp))
        }
        if (media.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        ChatComposer(state.draft, onDraftChange, onSend, state.replyingTo, onCancelReply,
            onAttach = { keyboard?.hide(); emojiOpen = false; attachOpen = true },
            onEmoji = { keyboard?.hide(); emojiOpen = !emojiOpen },
            onVoice = { emojiOpen = false; media.toggleRecording() }, isRecording = media.recording,
            onCancelRecording = media.cancelRecording, elapsedSeconds = media.elapsedSeconds)
        AnimatedVisibility(emojiOpen, enter = expandVertically(TgMotion.content()) + fadeIn(TgMotion.content()),
            exit = shrinkVertically(TgMotion.content()) + fadeOut(TgMotion.content())) {
            Column(Modifier.fillMaxWidth().background(c.surface)) {
                Text(stringResource(R.string.emoji_panel), color = c.subtitle, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                val emojis = remember { listOf("😀", "😄", "😂", "🥹", "😊", "😍", "🥰", "😎", "🤔", "😮", "😭", "😅",
                    "👍", "👎", "👏", "🙌", "🤝", "🙏", "❤️", "🔥", "✨", "🎉", "💯", "✅", "👀", "💡", "🚀", "☕", "🌙", "🌸") }
                LazyVerticalGrid(columns = GridCells.Adaptive(48.dp), modifier = Modifier.fillMaxWidth().height(200.dp)) {
                    items(emojis, key = { it }, contentType = { "emoji" }) { emoji ->
                        Box(Modifier.size(48.dp).clickable { onDraftChange(state.draft + emoji) }, contentAlignment = Alignment.Center) {
                            Text(emoji, fontSize = 26.sp)
                        }
                    }
                }
            }
        }
    }
    if (attachOpen) AttachmentSheet(onDismiss = { attachOpen = false }, onPick = media.pick)
    imagePreview?.let { ImageViewer(it, onDismiss = { imagePreview = null }) }
    state.toolsTitle?.let { ChannelToolsDialog(it, state.toolsItems, onDismissTools) }
    state.actionTarget?.let { message ->
        MessageActionSheet(message, onDismiss = onDismissActions,
            onReply = { onReply(message); onDismissActions() },
            onCopy = { onCopy(message.text); onDismissActions() },
            onForward = { onForward(message.id); onDismissActions() },
            onRecall = { onRecall(message.id); onDismissActions() },
            onDelete = { onDelete(message.id); onDismissActions() },
            onReact = { onReact(message.id, it); onDismissActions() },
            onRetry = { onRetry(message.id); onDismissActions() },
            canReact = state.scene == "channel" && (state.demoMode || message.remoteId != null), demoMode = state.demoMode)
    }
    state.forwardMessage?.let { message ->
        if (forwardTarget == null) ConversationPicker(state.forwardTargets, stringResource(R.string.forward_title), message.text,
            onDismiss = onDismissForward, onSelect = { forwardTarget = it })
    }
    forwardTarget?.let { target ->
        AlertDialog(onDismissRequest = { forwardTarget = null },
            title = { Text(stringResource(R.string.forward)) },
            text = { Text(stringResource(R.string.forward_confirm, target.displayName)) },
            confirmButton = { TextButton(onClick = { onForwardConfirm(target.id); forwardTarget = null }) { Text(stringResource(R.string.forward)) } },
            dismissButton = { TextButton(onClick = { forwardTarget = null }) { Text(stringResource(R.string.cancel)) } })
    }
}

@Composable
private fun MessageRow(item: ChatItem.Msg, scene: String, onButtonClick: (String) -> Unit, onOpenMedia: (String) -> Unit,
                       onActions: () -> Unit, onReply: () -> Unit, onQuote: (String) -> Unit, onRetry: () -> Unit,
                       modifier: Modifier = Modifier) {
    val showAvatar = scene != "c2c" && !item.message.fromBot
    val threshold = with(LocalDensity.current) { 56.dp.toPx() }
    val translation = remember(item.message.id) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val readyToReply by remember { derivedStateOf { translation.value <= -threshold } }
    Box(modifier.fillMaxWidth()) {
        if (readyToReply) Icon(painterResource(R.drawable.ic_reply), stringResource(R.string.reply),
            tint = TgTheme.colors.primary, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 20.dp))
        Row(Modifier.fillMaxWidth().graphicsLayer { translationX = translation.value }
            .draggable(rememberDraggableState { delta ->
                scope.launch { translation.snapTo((translation.value + delta).coerceIn(-threshold * 1.5f, 0f)) }
            },
                Orientation.Horizontal, enabled = item.message.status != "recalled",
                startDragImmediately = translation.isRunning,
                onDragStarted = { translation.stop() },
                onDragStopped = { velocity ->
                    if (translation.value <= -threshold) onReply()
                    translation.animateTo(0f, TgMotion.replyReturn(), initialVelocity = velocity)
                })
            .padding(horizontal = 8.dp, vertical = if (item.fullTop) 3.dp else 1.dp),
            horizontalArrangement = if (item.message.fromBot) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom) {
            if (showAvatar) {
                if (item.fullBottom) TgAvatar(item.message.authorName, item.message.authorAvatar, TgDimens.ChatAvatarSize)
                else Spacer(Modifier.width(TgDimens.ChatAvatarSize))
                Spacer(Modifier.width(4.dp))
            }
            MessageBubble(item.message, onActions, showAvatar, item.fullTop, item.fullBottom,
                onButtonClick, onOpenMedia, onQuote, onRetry)
        }
    }
}

@Composable
private fun DateChip(iso: String) {
    val c = TgTheme.colors
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(dateChip(iso), fontSize = 12.sp, color = c.dateChipText,
            modifier = Modifier.clip(RoundedCornerShape(24.dp)).background(c.dateChip).padding(horizontal = 12.dp, vertical = 4.dp))
    }
}
