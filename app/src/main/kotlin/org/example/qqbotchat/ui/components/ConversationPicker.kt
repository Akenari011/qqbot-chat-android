package org.example.qqbotchat.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.example.qqbotchat.R
import org.example.qqbotchat.core.Conversation
import org.example.qqbotchat.core.filterConversations
import org.example.qqbotchat.ui.theme.TgTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationPicker(conversations: List<Conversation>, title: String, subtitle: String,
                       onDismiss: () -> Unit, onSelect: (Conversation) -> Unit) {
    var query by remember { mutableStateOf("") }
    val shown = remember(conversations, query) { filterConversations(conversations, query) }
    TgActionSheet(onDismiss) { close ->
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
        Text(subtitle, color = TgTheme.colors.subtitle, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        OutlinedTextField(query, { query = it }, placeholder = { Text(stringResource(R.string.search_chats)) },
            singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp))
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp).navigationBarsPadding()) {
            if (shown.isEmpty()) item(key = "empty", contentType = "empty") {
                Text(stringResource(R.string.forward_no_targets), modifier = Modifier.padding(24.dp), color = TgTheme.colors.subtitle)
            }
            items(shown, key = { it.id }, contentType = { "target" }) { conversation ->
                DialogCell(conversation, onClick = { close { onSelect(conversation) } }, onTogglePin = {})
            }
        }
    }
}
