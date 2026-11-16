package org.example.qqbotchat.core

import org.junit.Assert.*
import org.junit.Test

class ChatExperienceTest {
    private fun message(id: String, text: String = id) =
        ChatMessage(id, text, "2026-11-16T02:00:00Z", false, "Alice")

    @Test fun emittedConversationsAreDetachedFromStoreMutations() {
        val store = ConversationStore()
        store.append("one", "c2c", message("first"))
        val before = store.allConversations()
        store.setPinned("one", true)
        store.markRead("one")
        store.append("one", "c2c", message("second"))
        assertFalse(before.single().pinned)
        assertEquals(1, before.single().unread)
        assertEquals(1, before.single().messages.size)
        assertNotEquals(before, store.allConversations())
    }

    @Test fun modifyingSnapshotCannotDeleteStoredMessages() {
        val store = ConversationStore()
        store.append("one", "c2c", message("first"))
        store.snapshot("one")!!.messages.clear()
        assertEquals(1, store.allMessages("one").size)
    }

    @Test fun independentDraftsAndReferencesSurviveRestart() {
        val store = ConversationStore()
        store.append("one", "c2c", message("reply"))
        store.getOrCreate("two", "group")
        store.setDraft("one", "第一份草稿\n✨", "reply")
        store.setDraft("two", "第二份草稿", null)
        val restored = ConversationStore().apply { loadJson(store.toJson()) }
        assertEquals("第一份草稿\n✨", restored.snapshot("one")!!.draft)
        assertEquals("reply", restored.snapshot("one")!!.draftReplyId)
        assertEquals("第二份草稿", restored.snapshot("two")!!.draft)
    }

    @Test fun oldJsonLoadsWithEmptyDraftAndNoRemoteIdentity() {
        val store = ConversationStore()
        store.loadJson("""{"version":1,"conversations":[{"id":"one","messages":[{"id":"old"}]}]}""")
        assertEquals("", store.snapshot("one")!!.draft)
        assertNull(store.allMessages("one").single().serverId)
    }

    @Test fun localMediaAndServerIdentitySurviveRestart() {
        val store = ConversationStore()
        store.recordOutgoing("one", "c2c", message("local").copy(fromBot = true,
            serverId = "remote", imageUrl = "local", localImage = "/data/media/image"))
        val restored = ConversationStore().apply { loadJson(store.toJson()) }
        assertEquals("remote", restored.allMessages("one").single().remoteId)
        assertEquals("/data/media/image", restored.allMessages("one").single().localImage)
    }

    @Test fun deletingLastMessageRepairsPreviewAndReplyDraft() {
        val store = ConversationStore()
        store.append("one", "c2c", message("first"))
        store.append("one", "c2c", message("last"))
        store.setDraft("one", "draft", "last")
        store.deleteMessage("one", "last")
        assertEquals("first", store.snapshot("one")!!.lastText)
        assertNull(store.snapshot("one")!!.draftReplyId)
        store.deleteMessage("one", "first")
        assertEquals("", store.snapshot("one")!!.lastTimestamp)
    }

    @Test fun archivedHistoryCanBeSearchedWhenInboxIsEmpty() {
        val store = ConversationStore()
        store.append("one", "group", message("first", "Compose history"))
        store.append("one", "group", message("last", "something else"))
        store.setArchived("one", true)
        assertTrue(store.allConversations(false).isEmpty())
        assertEquals("one", filterConversations(store.allConversations(), "compose").single().id)
    }

    @Test fun categoryFiltersCombineWithSearch() {
        val conversations = listOf(Conversation("one", "c2c", "Alice", unread = 1),
            Conversation("two", "group", "Team", unread = 0), Conversation("three", "channel", "News", unread = 3))
        assertEquals(listOf("one", "three"), filterConversations(conversations, filter = ConversationFilter.UNREAD).map { it.id })
        assertEquals("two", filterConversations(conversations, "team", ConversationFilter.GROUPS).single().id)
        assertTrue(filterConversations(conversations, "Alice", ConversationFilter.CHANNELS).isEmpty())
    }

    @Test fun replyReservationsDoNotOverwriteNewIncomingWindow() {
        val store = ConversationStore()
        store.append("one", "group", message("old"))
        val first = store.reserveReply("one")!!
        val second = store.reserveReply("one")!!
        assertEquals(first.replySeq + 1, second.replySeq)
        store.append("one", "group", message("new"))
        assertEquals("old", first.replyId)
        assertEquals("new", store.reserveReply("one")!!.replyId)
    }

    @Test fun gatewayEchoDoesNotDuplicateConfirmedOutgoingMessage() {
        val store = ConversationStore()
        store.recordOutgoing("one", "c2c", message("local").copy(fromBot = true, serverId = "remote"))
        assertFalse(store.append("one", "c2c", message("remote")))
        assertEquals(1, store.allMessages("one").size)
    }

    @Test fun fileAttachmentsRetainDownloadUrlAndOriginalFilename() {
        val payload = com.google.gson.JsonParser.parseString("""{
            "id":"file","author":{"user_openid":"one"},
            "attachments":[{"url":"//cdn.example/guide.pdf","content_type":"application/pdf","filename":"Guide.PDF"}]
        }""").asJsonObject
        val message = MessageParser.parseMessage("C2C_MESSAGE_CREATE", payload)!!.message
        assertFalse(message.isImage)
        assertTrue(message.isFile)
        assertEquals("Guide.PDF", message.fileName)
        val store = ConversationStore()
        store.append("one", "c2c", message)
        val restored = ConversationStore().apply { loadJson(store.toJson()) }
        assertEquals("https://cdn.example/guide.pdf", restored.allMessages("one").single().fileUrl)
    }

    @Test fun incomingQuoteResolvesPreviouslySentRemoteId() {
        val store = ConversationStore()
        store.recordOutgoing("one", "c2c", message("local", "quoted text").copy(fromBot = true, serverId = "remote"))
        val incoming = message("reply").copy(quotedId = "remote")
        store.onIncoming(IncomingEvent("C2C_MESSAGE_CREATE", "one", "c2c", incoming, incoming.id))
        assertEquals("quoted text", store.allMessages("one").last().quotedText)
    }
}
