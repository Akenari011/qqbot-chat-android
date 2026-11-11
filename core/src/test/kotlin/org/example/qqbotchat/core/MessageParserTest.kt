package org.example.qqbotchat.core

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class MessageParserTest {

    private fun obj(json: String) = JsonParser.parseString(json).asJsonObject

    @Test fun mentionMarkupIsReplaced() {
        val payload = obj("""{"mentions":[{"id":"ABCDEF123456","username":"小明"}]}""")
        assertEquals("你好 @小明", MessageParser.cleanContent("你好 <@ABCDEF123456>", payload))
    }

    @Test fun mentionFallsBackToSuffix() {
        assertEquals("hi @成员 123456 !", MessageParser.cleanContent("hi <@!ABCDEF123456> !"))
    }

    @Test fun faceMarkupIsReplaced() {
        assertEquals("\uD83D\uDE0D\uD83D\uDE0A", MessageParser.cleanContent("<faceType=1,faceId=\"2\"><faceType=1,faceId=\"6\">"))
    }

    @Test fun plainTextUnchanged() {
        val s = "普通消息 https://example.com"
        assertEquals(s, MessageParser.cleanContent(s))
    }

    @Test fun sceneDerivedFromKind() {
        assertEquals("c2c", MessageParser.sceneOf("C2C_MESSAGE_CREATE"))
        assertEquals("group", MessageParser.sceneOf("GROUP_MESSAGE_CREATE"))
        assertEquals("channel", MessageParser.sceneOf("MESSAGE_CREATE"))
        assertEquals("channel", MessageParser.sceneOf("AT_MESSAGE_CREATE"))
    }

    @Test fun onlyMessageEventsRecognised() {
        assertTrue(MessageParser.isMessageEvent("C2C_MESSAGE_CREATE"))
        assertTrue(MessageParser.isMessageEvent("GROUP_AT_MESSAGE_CREATE"))
        assertFalse(MessageParser.isMessageEvent("READY"))
        assertFalse(MessageParser.isMessageEvent("INTERACTION_CREATE"))
    }

    @Test fun c2cEventParses() {
        val p = obj("""{"id":"msg-1","content":"你好 <@ABC>","timestamp":"2026-11-11T02:00:00+08:00","author":{"user_openid":"UOPENID","username":"阿明","id":"UOPENID"},"mentions":[{"id":"ABC","username":"阿明"}]}""")
        val ev = MessageParser.parseMessage("C2C_MESSAGE_CREATE", p)!!
        assertEquals("c2c", ev.scene)
        assertEquals("UOPENID", ev.target)
        assertEquals("阿明", ev.message.authorName)
        assertEquals("你好 @阿明", ev.message.text)
        assertFalse(ev.message.fromBot)
    }

    @Test fun groupEventUsesGroupOpenId() {
        val p = obj("""{"id":"m2","content":"hi","group_openid":"GOPENID123456","author":{"username":"群友","member_openid":"MEM1"},"timestamp":"2026-11-11T02:00:00Z"}""")
        val ev = MessageParser.parseMessage("GROUP_MESSAGE_CREATE", p)!!
        assertEquals("group", ev.scene)
        assertEquals("GOPENID123456", ev.target)
        assertEquals("群友", ev.message.authorName)
        assertEquals("MEM1", ev.message.authorOpenId)
    }

    @Test fun channelEventUsesChannelId() {
        val p = obj("""{"id":"m3","content":"c","channel_id":"CH1","author":{"username":"频道友"}}""")
        val ev = MessageParser.parseMessage("MESSAGE_CREATE", p)!!
        assertEquals("channel", ev.scene)
        assertEquals("CH1", ev.target)
    }

    @Test fun attachmentUrlNormalised() {
        val p = obj("""{"id":"m4","content":"","group_openid":"G1","attachments":[{"url":"//multimedia.nt.qq.com.cn/x.jpg","content_type":"image/jpeg","filename":"a.jpg"}]}""")
        val m = MessageParser.parseMessage("GROUP_MESSAGE_CREATE", p)!!.message
        assertEquals("https://multimedia.nt.qq.com.cn/x.jpg", m.imageUrl)
        assertNull(m.videoUrl)
        assertTrue(m.isImage)
    }

    @Test fun videoAttachmentTyped() {
        val p = obj("""{"id":"m5","content":"","group_openid":"G1","attachments":[{"url":"https://x/v.mp4","content_type":"video/mp4","filename":"v.mp4"}]}""")
        val m = MessageParser.parseMessage("GROUP_MESSAGE_CREATE", p)!!.message
        assertTrue(m.isVideo)
        assertNull(m.imageUrl)
    }

    @Test fun missingTargetReturnsNull() {
        val p = obj("""{"id":"m6","content":"x","author":{}}""")
        assertNull(MessageParser.parseMessage("C2C_MESSAGE_CREATE", p))
    }

    @Test fun keyboardButtonsExtracted() {
        val p = obj("""{"id":"m7","content":"x","group_openid":"G1","keyboard":{"content":{"rows":[{"buttons":[{"render_data":{"label":"确定"}}]}]}}}""")
        val m = MessageParser.parseMessage("GROUP_MESSAGE_CREATE", p)!!.message
        assertEquals(listOf("确定"), m.buttons)
    }

    @Test fun messageReferenceBecomesQuotedId() {
        val p = obj("""{"id":"m8","content":"x","group_openid":"G1","message_reference":{"message_id":"old-1"}}""")
        val m = MessageParser.parseMessage("GROUP_MESSAGE_CREATE", p)!!.message
        assertEquals("old-1", m.quotedId)
    }

    @Test fun sameGroupRequiresSameAuthorAndDirection() {
        val a = ChatMessage("1", "a", "2026-11-11T02:00:00Z", false, "阿明")
        val b = ChatMessage("2", "b", "2026-11-11T02:01:00Z", false, "阿明")
        val c = ChatMessage("3", "c", "2026-11-11T02:01:00Z", true, "QQ 机器人")
        assertTrue(MessageParser.isSameGroup(a, b))
        assertFalse(MessageParser.isSameGroup(a, c))
    }

    @Test fun summaryReflectsMediaType() {
        val m = ChatMessage("1", "", "t", false, "x", imageUrl = "http://i")
        assertEquals("[图片]", m.summary)
        val f = ChatMessage("2", "", "t", false, "x", fileName = "a.pdf")
        assertEquals("[文件] a.pdf", f.summary)
    }
}
