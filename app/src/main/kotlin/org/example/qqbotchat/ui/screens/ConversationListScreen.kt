// Telegram-inspired layout; see THIRD_PARTY_NOTICES.md for design attributions.
package org.example.qqbotchat.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.example.qqbotchat.R
import org.example.qqbotchat.core.Conversation
import org.example.qqbotchat.core.ConversationFilter
import org.example.qqbotchat.core.filterConversations
import org.example.qqbotchat.ui.components.*
import org.example.qqbotchat.ui.theme.TgMotion
import org.example.qqbotchat.ui.theme.TgTheme
import org.example.qqbotchat.vm.ConversationListUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    state: ConversationListUiState,
    onQueryChange: (String) -> Unit,
    onFilterChange: (ConversationFilter) -> Unit,
    onOpen: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    onMarkRead: (String) -> Unit,
    onToggleMute: (String) -> Unit,
    onToggleArchive: (String) -> Unit,
    onDelete: (String) -> Unit,
    onToggleArchivedView: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = TgTheme.colors
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<Conversation?>(null) }
    var deleteTarget by remember { mutableStateOf<Conversation?>(null) }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val all = state.conversations + state.archivedConversations
    val source = if (state.showingArchived) state.archivedConversations
        else if (state.query.isNotBlank()) all else state.conversations
    val shown = remember(source, state.query, state.filter, state.showingArchived) {
        filterConversations(source, state.query, if (state.showingArchived) ConversationFilter.ALL else state.filter)
    }
    BackHandler(searchOpen || state.showingArchived) {
        if (searchOpen) { searchOpen = false; onQueryChange("") } else onToggleArchivedView()
    }
    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = c.surface) {
                Column(Modifier.fillMaxWidth().background(c.primary).padding(24.dp)) {
                    TgAvatar(stringResource(R.string.bot_account), null, 64.dp)
                    Spacer(Modifier.height(18.dp))
                    Text(stringResource(if (state.demoMode) R.string.demo_mode else R.string.bot_account),
                        color = c.unreadText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(if (state.demoMode) R.string.demo_hint else R.string.bot_account_hint),
                        color = c.unreadText.copy(alpha = .8f), fontSize = 13.sp)
                }
                Spacer(Modifier.height(12.dp))
                NavigationDrawerItem(label = { Text(stringResource(R.string.chats)) }, selected = !state.showingArchived,
                    icon = { Icon(painterResource(R.drawable.ic_chat), null) }, onClick = {
                        scope.launch { drawer.close() }; if (state.showingArchived) onToggleArchivedView()
                    })
                NavigationDrawerItem(label = { Text(stringResource(R.string.archived_chats)) }, selected = state.showingArchived,
                    icon = { Icon(painterResource(R.drawable.ic_archive), null) }, onClick = {
                        scope.launch { drawer.close() }; if (!state.showingArchived) onToggleArchivedView()
                    })
                NavigationDrawerItem(label = { Text(stringResource(R.string.settings)) }, selected = false,
                    icon = { Icon(painterResource(R.drawable.ic_settings), null) }, onClick = {
                        scope.launch { drawer.close() }; onSettings()
                    })
            }
        },
    ) {
        Box(modifier.fillMaxSize().background(c.surface).statusBarsPadding().navigationBarsPadding()) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        when {
                            searchOpen -> { searchOpen = false; onQueryChange("") }
                            state.showingArchived -> onToggleArchivedView()
                            else -> scope.launch { drawer.open() }
                        }
                    }) {
                        Icon(painterResource(if (state.showingArchived || searchOpen) R.drawable.ic_back else R.drawable.ic_menu),
                            stringResource(if (state.showingArchived || searchOpen) R.string.back else R.string.menu), tint = c.icon)
                    }
                    if (searchOpen) {
                        ChatSearchField(state.query, onQueryChange, stringResource(R.string.search_chats),
                            onClose = { searchOpen = false; onQueryChange("") }, modifier = Modifier.weight(1f))
                    } else {
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            Text(stringResource(if (state.showingArchived) R.string.archived_chats else R.string.app_name),
                                fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = c.name)
                            Text(if (state.demoMode) stringResource(R.string.demo_hint) else state.connectionStatus,
                                fontSize = 12.sp, color = c.subtitle, maxLines = 1)
                        }
                        IconButton(onClick = { searchOpen = true }) {
                            Icon(painterResource(R.drawable.ic_search), stringResource(R.string.search), tint = c.icon)
                        }
                    }
                }
                if (!state.showingArchived) {
                    val filters = ConversationFilter.entries
                    val labels = listOf(R.string.all_chats, R.string.unread_chats, R.string.private_chats,
                        R.string.group_chats, R.string.channels)
                    ScrollableTabRow(selectedTabIndex = filters.indexOf(state.filter), edgePadding = 8.dp,
                        containerColor = c.surface, contentColor = c.primary,
                        divider = { HorizontalDivider(color = c.divider.copy(alpha = .5f), thickness = .5.dp) }) {
                        filters.forEachIndexed { index, filter ->
                            Tab(selected = state.filter == filter, onClick = { onFilterChange(filter) },
                                text = { Text(stringResource(labels[index]), fontWeight = FontWeight.SemiBold) },
                                selectedContentColor = c.primary, unselectedContentColor = c.subtitle)
                        }
                    }
                }
                if (shown.isEmpty() && (state.showingArchived || state.archivedConversations.isEmpty() ||
                            state.query.isNotBlank() || state.filter != ConversationFilter.ALL)) {
                    ChatEmptyState(
                        title = stringResource(if (all.isEmpty()) R.string.empty_chats_title else R.string.empty_filter_title),
                        body = stringResource(when {
                            state.showingArchived -> R.string.empty_archive_body
                            all.isEmpty() -> R.string.empty_chats_body
                            else -> R.string.empty_filter_body
                        }), modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
                        if (!state.showingArchived && state.query.isBlank() && state.filter == ConversationFilter.ALL &&
                            state.archivedConversations.isNotEmpty()) {
                            item(key = "archive", contentType = "archive") {
                                Row(Modifier.fillMaxWidth().clickable(onClick = onToggleArchivedView)
                                    .padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(52.dp).clip(CircleShape).background(c.unreadBadgeMuted), contentAlignment = Alignment.Center) {
                                        Icon(painterResource(R.drawable.ic_archive), null, tint = c.unreadText)
                                    }
                                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                        Text(stringResource(R.string.archived_chats), color = c.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                                        Text(stringResource(R.string.archived_count, state.archivedConversations.size), color = c.subtitle, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                        items(shown, key = { it.id }, contentType = { "conversation" }) { conversation ->
                            DialogCell(conversation, onClick = { onOpen(conversation.id) },
                                onTogglePin = { onTogglePin(conversation.id) }, onLongClick = { actionTarget = conversation },
                                modifier = Modifier.animateItem(fadeInSpec = TgMotion.content(),
                                    placementSpec = TgMotion.content(), fadeOutSpec = TgMotion.content()))
                        }
                    }
                }
            }
            FloatingActionButton(onClick = { pickerOpen = true }, containerColor = c.primary, contentColor = c.unreadText,
                shape = CircleShape, modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)) {
                Icon(painterResource(R.drawable.ic_edit), stringResource(R.string.new_message))
            }
        }
    }
    actionTarget?.let { conversation ->
        ConversationActionSheet(conversation, onDismiss = { actionTarget = null },
            onMarkRead = { onMarkRead(conversation.id); actionTarget = null },
            onTogglePin = { onTogglePin(conversation.id); actionTarget = null },
            onToggleMute = { onToggleMute(conversation.id); actionTarget = null },
            onToggleArchive = { onToggleArchive(conversation.id); actionTarget = null },
            onDelete = { deleteTarget = conversation; actionTarget = null })
    }
    deleteTarget?.let { conversation ->
        AlertDialog(onDismissRequest = { deleteTarget = null }, title = { Text(stringResource(R.string.delete_chat)) },
            text = { Text(stringResource(R.string.delete_chat_confirm, conversation.displayName)) },
            confirmButton = { TextButton(onClick = { onDelete(conversation.id); deleteTarget = null }) { Text(stringResource(R.string.delete), color = c.error) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) } })
    }
    if (pickerOpen) ConversationPicker(all, stringResource(R.string.new_message), stringResource(R.string.new_message_hint),
        onDismiss = { pickerOpen = false }, onSelect = { pickerOpen = false; onOpen(it.id) })
}
