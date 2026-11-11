package org.example.qqbotchat.core

import org.junit.Assert.*
import org.junit.Test

class ConversationStoreTest {
    private fun msg(id: String, text: String, fromBot: Boolean = false) =
        ChatMessage(id, text, "2026-11-11T02:00:00Z", fromBot, "阿明")

    private fun ConversationStore.add(target: String, m: ChatMessage): Boolean =
        append(target, "c2c", m, markUnread = false)

    @Test fun createAndRetrieve() {
        val s = ConversationStore()
        val c = s.getOrCreate("U1", "c2c", "小明")
        assertEquals("U1", c.id)
        assertEquals("小明", c.displayName)
        assertEquals(1, s.size())
        assertSame(c, s.getConversation("U1"))
    }

    @Test fun appendDeduplicates() {
        val s = ConversationStore()
        s.getOrCreate("U1", "c2c")
        assertTrue(s.add("U1", msg("a", "hi")))
        assertFalse(s.add("U1", msg("a", "hi")))
        assertEquals(1, s.allMessages("U1").size)
    }

    @Test fun appendUpdatesLastTextAndTimestamp() {
        val s = ConversationStore()
        s.getOrCreate("U1", "c2c")
        s.add("U1", ChatMessage("a", "hello", "2026-11-11T03:00:00Z", false, "阿明"))
        val c = s.getConversation("U1")!!
        assertEquals("hello", c.lastText)
        assertEquals("2026-11-11T03:00:00Z", c.lastTimestamp)
    }

    @Test fun incomingMarksUnreadAndSetsReply() {
        val s = ConversationStore()
        val m = msg("m1", "hi")
        val ev = IncomingEvent("C2C_MESSAGE_CREATE", "U1", "c2c", m, "m1")
        s.onIncoming(ev)
        val c = s.getConversation("U1")!!
        assertEquals(1, c.unread)
        assertEquals("m1", c.replyId)
        assertEquals(1, s.allMessages("U1").size)
    }

    @Test fun markReadClearsUnread() {
        val s = ConversationStore()
        s.onIncoming(IncomingEvent("C2C_MESSAGE_CREATE", "U1", "c2c", msg("m", "x"), "m"))
        s.markRead("U1")
        assertEquals(0, s.getConversation("U1")!!.unread)
    }

    @Test fun togglesAndRemark() {
        val s = ConversationStore()
        s.getOrCreate("U1", "c2c", "原名")
        s.setPinned("U1", true); s.setArchived("U1", true); s.setMuted("U1", true)
        s.setRemark("U1", "备注名")
        val c = s.getConversation("U1")!!
        assertTrue(c.pinned && c.archived && c.muted)
        assertEquals("备注名", c.displayName)
        s.setRemark("U1", null)
        assertEquals("原名", c.displayName)
    }

    @Test fun sortingPinnedFirst() {
        val s = ConversationStore()
        s.getOrCreate("A", "c2c", "A")
        s.getOrCreate("B", "c2c", "B")
        s.setPinned("B", true)
        val list = s.allConversations()
        assertEquals("B", list.first().id)
    }

    @Test fun searchByNameLastAndHistory() {
        val s = ConversationStore()
        s.getOrCreate("U1", "c2c", "小明")
        s.add("U1", msg("a", "苹果"))
        s.add("U1", msg("b", "香蕉"))
        assertEquals(1, s.searchNames("小明").size)
        assertEquals(1, s.searchLastMessages("香蕉").size)
        assertEquals(2, s.searchHistory("苹果").size + s.searchHistory("香蕉").size)
    }

    @Test fun recentMessagesPagination() {
        val s = ConversationStore()
        s.getOrCreate("U1", "c2c")
        (1..10).forEach { s.add("U1", msg("m$it", "t$it")) }
        assertEquals(3, s.recentMessages("U1", 3).size)
        assertEquals("m10", s.recentMessages("U1", 3).last().id)
        assertEquals("m7", s.recentMessages("U1", 3, 1).last().id)
    }

    @Test fun jsonRoundTrip() {
        val s = ConversationStore()
        s.getOrCreate("U1", "c2c", "小明")
        s.add("U1", msg("a", "hi"))
        s.setPinned("U1", true); s.setRemark("U1", "备注")
        val json = s.toJson()
        val restored = ConversationStore()
        assertEquals(1, restored.loadJson(json))
        val c = restored.getConversation("U1")!!
        assertEquals("备注", c.displayName)
        assertTrue(c.pinned)
        assertEquals(1, c.messages.size)
    }

    @Test fun deleteAndClear() {
        val s = ConversationStore()
        s.getOrCreate("U1", "c2c"); s.getOrCreate("U2", "c2c")
        assertTrue(s.deleteConversation("U1"))
        assertEquals(1, s.size())
        s.clear()
        assertEquals(0, s.size())
    }
}
