package org.example.qqbotchat.core

enum class ConversationFilter { ALL, UNREAD, PRIVATE, GROUPS, CHANNELS }

/** Shared by the main list and target picker. Archived chats remain searchable. */
fun filterConversations(
    conversations: List<Conversation>,
    query: String = "",
    filter: ConversationFilter = ConversationFilter.ALL,
): List<Conversation> {
    val keyword = query.trim()
    return conversations.filter { conversation ->
        val categoryMatches = when (filter) {
            ConversationFilter.ALL -> true
            ConversationFilter.UNREAD -> conversation.unread > 0
            ConversationFilter.PRIVATE -> conversation.scene == "c2c"
            ConversationFilter.GROUPS -> conversation.scene == "group"
            ConversationFilter.CHANNELS -> conversation.scene == "channel"
        }
        categoryMatches && (keyword.isEmpty() ||
            conversation.displayName.contains(keyword, ignoreCase = true) ||
            conversation.draft.contains(keyword, ignoreCase = true) ||
            conversation.messages.any { it.matchesQuery(keyword) })
    }
}

fun ChatMessage.matchesQuery(query: String): Boolean =
    listOf(text, fileName.orEmpty(), quotedText.orEmpty(), authorName)
        .any { it.contains(query.trim(), ignoreCase = true) }
