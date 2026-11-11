package org.example.qqbotchat.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ConversationStore JSON 持久化边界测试，纯 JVM，无 Android。
 *
 * 覆盖 ConversationStore.toJson() / loadJson() 的边界行为：
 * 空数据往返、全字段往返、特殊字符、媒体消息、buttons/reactions、
 * 空白输入、非法 JSON、根节点非对象、版本过新、装载前清空、大数据量、还原后继续追加。
 *
 * 说明：loadJson 恢复出的字符串字段一律为具体值（缺失时回落默认值），
 * 因此断言全部使用具体值比对而非仅判非空。
 */
class ConversationStoreJsonTest {

    // ---------- 构造辅助 ----------

    private fun store(): ConversationStore = ConversationStore()

    @Suppress("LongParameterList")
    private fun msg(
        id: String,
        text: String = "",
        timestamp: String = "2026-11-11T02:00:00Z",
        fromBot: Boolean = false,
        authorName: String = "阿明",
        authorOpenId: String = "openid-default",
        authorAvatar: String? = null,
        imageUrl: String? = null,
        videoUrl: String? = null,
        audioUrl: String? = null,
        fileName: String? = null,
        quotedText: String? = null,
        quotedId: String? = null,
        duration: Double? = null,
        buttons: List<String> = emptyList(),
        reactions: Map<String, Int> = emptyMap(),
        status: String = "sent"
    ): ChatMessage = ChatMessage(
        id = id,
        text = text,
        timestamp = timestamp,
        fromBot = fromBot,
        authorName = authorName,
        authorOpenId = authorOpenId,
        authorAvatar = authorAvatar,
        imageUrl = imageUrl,
        videoUrl = videoUrl,
        audioUrl = audioUrl,
        fileName = fileName,
        quotedText = quotedText,
        quotedId = quotedId,
        duration = duration,
        buttons = buttons,
        reactions = reactions,
        status = status
    )

    /** 逐字段比对一条消息，所有断言均为具体值比对。 */
    private fun assertMessageEquals(where: String, expected: ChatMessage, actual: ChatMessage) {
        assertEquals("$where.id", expected.id, actual.id)
        assertEquals("$where.text", expected.text, actual.text)
        assertEquals("$where.timestamp", expected.timestamp, actual.timestamp)
        assertEquals("$where.fromBot", expected.fromBot, actual.fromBot)
        assertEquals("$where.authorName", expected.authorName, actual.authorName)
        assertEquals("$where.authorOpenId", expected.authorOpenId, actual.authorOpenId)
        assertEquals("$where.authorAvatar", expected.authorAvatar, actual.authorAvatar)
        assertEquals("$where.imageUrl", expected.imageUrl, actual.imageUrl)
        assertEquals("$where.videoUrl", expected.videoUrl, actual.videoUrl)
        assertEquals("$where.audioUrl", expected.audioUrl, actual.audioUrl)
        assertEquals("$where.fileName", expected.fileName, actual.fileName)
        assertEquals("$where.quotedText", expected.quotedText, actual.quotedText)
        assertEquals("$where.quotedId", expected.quotedId, actual.quotedId)
        assertEquals("$where.duration", expected.duration, actual.duration)
        assertEquals("$where.buttons", expected.buttons, actual.buttons)
        assertEquals("$where.reactions", expected.reactions, actual.reactions)
        assertEquals("$where.status", expected.status, actual.status)
    }

    // ---------- 1 ----------

    /** 1. 空 store 的 toJson 输出可被 loadJson 还原（0 个会话）。 */
    @Test
    fun emptyStoreRoundTrip() {
        val source = store()
        val json = source.toJson()
        // 结构固定为 {"version":1,"conversations":[]}
        assertTrue("空 store 的 JSON 应带版本号", json.contains("\"version\":1"))
        assertTrue("空 store 的 JSON 应为空会话数组", json.contains("\"conversations\":[]"))

        val restored = store()
        assertEquals("空 JSON 恢复出的会话数", 0, restored.loadJson(json))
        assertEquals("空 JSON 恢复后 size", 0, restored.size())
        assertTrue("空 JSON 恢复后无任何会话", restored.allConversations().isEmpty())
        assertNull("不存在的会话应为 null", restored.getConversation("U1"))
        assertEquals("空数据再次序列化应稳定", json, restored.toJson())
    }

    // ---------- 2 ----------

    /** 2. 完整往返：多个会话 + 每个会话多条消息，还原后逐字段比对。 */
    @Test
    fun fullRoundTripPreservesEveryField() {
        val source = store()

        val c1 = source.getOrCreate("U1", "c2c", "小明")
        c1.lastText = "收到"
        c1.lastTimestamp = "2026-11-11T03:00:00Z"
        c1.unread = 3
        c1.replyId = "m2"
        c1.replySeq = 7
        c1.pinned = true
        c1.archived = false
        c1.muted = true
        c1.remark = "备注甲"
        c1.avatarPath = "/data/avatars/u1.png"
        val m1 = msg(
            id = "m1",
            text = "你好，在吗",
            timestamp = "2026-11-11T02:59:00Z",
            fromBot = false,
            authorName = "小明",
            authorOpenId = "openid-u1",
            authorAvatar = "https://cdn.example.com/u1.png",
            quotedText = "上一条引用",
            quotedId = "m0",
            status = "sent"
        )
        val m2 = msg(
            id = "m2",
            text = "收到",
            timestamp = "2026-11-11T03:00:00Z",
            fromBot = true,
            authorName = "机器人",
            authorOpenId = "bot-openid",
            status = "sent"
        )
        c1.messages.add(m1)
        c1.messages.add(m2)

        val c2 = source.getOrCreate("G2", "group", "群聊 123456")
        c2.lastText = "群消息"
        c2.lastTimestamp = "2026-11-11T04:00:00Z"
        c2.unread = 0
        c2.replyId = ""
        c2.replySeq = 0
        c2.pinned = false
        c2.archived = true
        c2.muted = false
        c2.remark = null
        c2.avatarPath = null
        val m3 = msg(
            id = "m3",
            text = "群消息",
            timestamp = "2026-11-11T04:00:00Z",
            fromBot = false,
            authorName = "群友",
            authorOpenId = "openid-g2",
            status = "sent"
        )
        c2.messages.add(m3)

        val json = source.toJson()
        val restored = store()
        assertEquals("恢复出的会话数", 2, restored.loadJson(json))
        assertEquals("恢复后 size", 2, restored.size())

        val r1 = restored.getConversation("U1")!!
        assertEquals("U1.id", "U1", r1.id)
        assertEquals("U1.scene", "c2c", r1.scene)
        assertEquals("U1.name", "小明", r1.name)
        assertEquals("U1.lastText", "收到", r1.lastText)
        assertEquals("U1.lastTimestamp", "2026-11-11T03:00:00Z", r1.lastTimestamp)
        assertEquals("U1.unread", 3, r1.unread)
        assertEquals("U1.pinned", true, r1.pinned)
        assertEquals("U1.archived", false, r1.archived)
        assertEquals("U1.muted", true, r1.muted)
        assertEquals("U1.remark", "备注甲", r1.remark)
        assertEquals("U1.avatarPath", "/data/avatars/u1.png", r1.avatarPath)
        assertEquals("U1.replyId", "m2", r1.replyId)
        assertEquals("U1.replySeq", 7, r1.replySeq)
        assertEquals("U1.displayName（备注优先）", "备注甲", r1.displayName)
        assertEquals("U1 消息条数", 2, r1.messages.size)
        assertMessageEquals("U1.m1", m1, r1.messages[0])
        assertMessageEquals("U1.m2", m2, r1.messages[1])

        val r2 = restored.getConversation("G2")!!
        assertEquals("G2.id", "G2", r2.id)
        assertEquals("G2.scene", "group", r2.scene)
        assertEquals("G2.name", "群聊 123456", r2.name)
        assertEquals("G2.lastText", "群消息", r2.lastText)
        assertEquals("G2.lastTimestamp", "2026-11-11T04:00:00Z", r2.lastTimestamp)
        assertEquals("G2.unread", 0, r2.unread)
        assertEquals("G2.pinned", false, r2.pinned)
        assertEquals("G2.archived", true, r2.archived)
        assertEquals("G2.muted", false, r2.muted)
        assertNull("G2.remark 应为 null", r2.remark)
        assertNull("G2.avatarPath 应为 null", r2.avatarPath)
        assertEquals("G2.replyId", "", r2.replyId)
        assertEquals("G2.replySeq", 0, r2.replySeq)
        assertEquals("G2.displayName（无备注回落到 name）", "群聊 123456", r2.displayName)
        assertEquals("G2 消息条数", 1, r2.messages.size)
        assertMessageEquals("G2.m3", m3, r2.messages[0])
    }

    // ---------- 3 ----------

    /** 3. 中文 + emoji + 换行 + 引号 + 反斜杠等特殊字符的文本往返后不损坏。 */
    @Test
    fun specialCharactersSurviveRoundTrip() {
        val trickyText = "中文测试🙂🎉\n第二行\t制表符 \"双引号\" 反斜杠\\ 斜杠/ 花括号{} 单引号' 结尾"
        val trickyName = "群聊【😀】\"名称\""
        val trickyRemark = "备注\\带反斜杠\n和换行"

        val source = store()
        val c = source.getOrCreate("U9", "group", trickyName)
        c.remark = trickyRemark
        c.lastText = trickyText
        c.messages.add(msg(id = "s1", text = trickyText, authorName = trickyName))

        val json = source.toJson()
        val restored = store()
        assertEquals("恢复出的会话数", 1, restored.loadJson(json))

        val r = restored.getConversation("U9")!!
        assertEquals("特殊字符 text", trickyText, r.messages[0].text)
        assertEquals("特殊字符 name", trickyName, r.name)
        assertEquals("特殊字符 remark", trickyRemark, r.remark)
        assertEquals("特殊字符 authorName", trickyName, r.messages[0].authorName)
        assertEquals("特殊字符 lastText", trickyText, r.lastText)
        assertTrue("换行应保留", r.messages[0].text.contains("\n"))
        assertTrue("emoji 应保留", r.messages[0].text.contains("🙂"))
        assertTrue("双引号应保留", r.messages[0].text.contains("\"双引号\""))
        assertTrue("反斜杠应保留", r.messages[0].text.contains("\\"))
    }

    // ---------- 4 ----------

    /** 4. 媒体消息（同时有 imageUrl / videoUrl / audioUrl / fileName / duration）往返保真。 */
    @Test
    fun mediaMessageRoundTrip() {
        val source = store()
        source.getOrCreate("U4", "c2c", "媒体用户")
        val media = msg(
            id = "media-1",
            text = "",
            fromBot = true,
            authorName = "机器人",
            imageUrl = "https://cdn.example.com/a.png",
            videoUrl = "https://cdn.example.com/b.mp4",
            audioUrl = "https://cdn.example.com/c.silk",
            fileName = "报告.pdf",
            duration = 12.5
        )
        source.getConversation("U4")!!.messages.add(media)

        val restored = store()
        assertEquals("恢复出的会话数", 1, restored.loadJson(source.toJson()))

        val m = restored.allMessages("U4")[0]
        assertEquals("imageUrl", "https://cdn.example.com/a.png", m.imageUrl)
        assertEquals("videoUrl", "https://cdn.example.com/b.mp4", m.videoUrl)
        assertEquals("audioUrl", "https://cdn.example.com/c.silk", m.audioUrl)
        assertEquals("fileName", "报告.pdf", m.fileName)
        assertEquals("duration", 12.5, m.duration!!, 0.0)
        assertTrue("isImage", m.isImage)
        assertTrue("isVideo", m.isVideo)
        assertTrue("isAudio", m.isAudio)
        assertTrue("isFile", m.isFile)
        assertEquals("图片优先的 summary", "[图片]", m.summary)
        // 五种媒体相关字段同时非空时一个都不丢
        assertMessageEquals("media-1", media, m)
    }

    // ---------- 5 ----------

    /** 5. buttons 列表与 reactions map（非空）往返保真。 */
    @Test
    fun buttonsAndReactionsRoundTrip() {
        val source = store()
        source.getOrCreate("U5", "group", "按钮群")
        val rich = msg(
            id = "btn-1",
            text = "请选择",
            fromBot = true,
            buttons = listOf("选项 A", "选项 B", "\"确认\"按钮", "emoji✅"),
            reactions = linkedMapOf("👍" to 3, "❤️" to 1, "😂" to 0)
        )
        source.getConversation("U5")!!.messages.add(rich)

        val restored = store()
        assertEquals("恢复出的会话数", 1, restored.loadJson(source.toJson()))

        val m = restored.allMessages("U5")[0]
        assertEquals("buttons 条数", 4, m.buttons.size)
        assertEquals("buttons 顺序与内容", listOf("选项 A", "选项 B", "\"确认\"按钮", "emoji✅"), m.buttons)
        assertEquals("reactions 条数", 3, m.reactions.size)
        assertEquals("reactions 内容", mapOf("👍" to 3, "❤️" to 1, "😂" to 0), m.reactions)
        assertEquals("reactions 👍 计数", 3, m.reactions["👍"])
        assertEquals("reactions 😂 计数", 0, m.reactions["😂"])
        assertEquals("buttons 检索仍可命中", 1, restored.searchHistory("确认").size)
    }

    // ---------- 6 ----------

    /** 6. loadJson("") 等价于清空且返回 0。 */
    @Test
    fun blankJsonClearsAndReturnsZero() {
        val s = store()
        s.getOrCreate("U1", "c2c", "小明")
        s.getOrCreate("G2", "group", "群聊")
        assertEquals("清空前 size", 2, s.size())

        assertEquals("空字符串返回 0", 0, s.loadJson(""))
        assertEquals("空字符串清空后 size", 0, s.size())
        assertNull("清空后 U1 不存在", s.getConversation("U1"))

        s.getOrCreate("U3", "c2c", "小红")
        assertEquals("纯空白串同样返回 0", 0, s.loadJson("   \n\t  "))
        assertEquals("纯空白串清空后 size", 0, s.size())
    }

    // ---------- 7 ----------

    /** 7. loadJson 传入非法 JSON（如 "not json" 或 "{"）抛 IllegalArgumentException。 */
    @Test
    fun malformedJsonThrows() {
        val s = store()
        s.getOrCreate("U1", "c2c", "小明")

        val e1 = assertThrows(IllegalArgumentException::class.java) { s.loadJson("not json") }
        assertTrue("非法 JSON 的异常信息应定位 JSON", e1.message!!.contains("JSON"))

        val e2 = assertThrows(IllegalArgumentException::class.java) { s.loadJson("{") }
        assertTrue("截断 JSON 的异常信息应定位 JSON", e2.message!!.contains("JSON"))

        val e3 = assertThrows(IllegalArgumentException::class.java) { s.loadJson("{\"version\":1,") }
        assertTrue("截断对象的异常信息应定位 JSON", e3.message!!.contains("JSON"))

        // 非法输入失败后原有内存数据仍在（loadJson 只在解析成功后清空）
        assertEquals("非法 JSON 不破坏已有数据", 1, s.size())
        assertEquals("非法 JSON 后会话仍在", "小明", s.getConversation("U1")!!.name)
    }

    // ---------- 8 ----------

    /** 8. loadJson 根节点不是对象（如 "[1,2]"）抛 IllegalArgumentException。 */
    @Test
    fun nonObjectRootThrows() {
        val s = store()
        val e = assertThrows(IllegalArgumentException::class.java) { s.loadJson("[1,2]") }
        assertTrue("应提示根节点不是对象", e.message!!.contains("根节点不是对象"))
        assertEquals("失败后未写入任何会话", 0, s.size())

        val e2 = assertThrows(IllegalArgumentException::class.java) { s.loadJson("42") }
        assertTrue("数字根节点同样报错", e2.message!!.contains("根节点不是对象"))

        val e3 = assertThrows(IllegalArgumentException::class.java) { s.loadJson("\"str\"") }
        assertTrue("字符串根节点同样报错", e3.message!!.contains("根节点不是对象"))
    }

    // ---------- 9 ----------

    /** 9. loadJson 版本号大于当前支持版本（如 {"version":999,"conversations":[]}）抛 IllegalArgumentException。 */
    @Test
    fun newerFormatVersionThrows() {
        val s = store()
        s.getOrCreate("U1", "c2c", "小明")

        val e = assertThrows(IllegalArgumentException::class.java) {
            s.loadJson("{\"version\":999,\"conversations\":[]}")
        }
        assertTrue("异常信息应包含版本号", e.message!!.contains("999"))
        assertTrue("异常信息应说明版本不支持", e.message!!.contains("版本"))

        // 版本过新时不清空已有数据
        assertEquals("版本过新不破坏已有数据", 1, s.size())

        // 当前版本（1）与更旧版本（0）均可正常载入
        assertEquals("version=1 可载入", 0, store().loadJson("{\"version\":1,\"conversations\":[]}"))
        assertEquals("version=0 可载入", 0, store().loadJson("{\"version\":0,\"conversations\":[]}"))
    }

    // ---------- 10 ----------

    /** 10. loadJson 会先清空已有数据（先建会话，再 loadJson 一个只有 1 个会话的 JSON，结果 size 应为 1）。 */
    @Test
    fun loadJsonClearsExistingDataFirst() {
        val s = store()
        s.getOrCreate("U1", "c2c", "旧会话一")
        s.getOrCreate("U2", "c2c", "旧会话二")
        s.getOrCreate("G3", "group", "旧会话三")
        assertEquals("装载前 size", 3, s.size())

        val incoming = store()
        val only = incoming.getOrCreate("NEW1", "c2c", "新会话")
        only.messages.add(msg(id = "n1", text = "新消息"))
        val json = incoming.toJson()

        assertEquals("装载返回的会话数", 1, s.loadJson(json))
        assertEquals("装载后 size", 1, s.size())
        assertNull("旧会话 U1 已被清空", s.getConversation("U1"))
        assertNull("旧会话 U2 已被清空", s.getConversation("U2"))
        assertNull("旧会话 G3 已被清空", s.getConversation("G3"))
        assertEquals("新会话 name", "新会话", s.getConversation("NEW1")!!.name)
        assertEquals("新会话消息数", 1, s.allMessages("NEW1").size)
        assertEquals("新会话消息文本", "新消息", s.allMessages("NEW1")[0].text)
    }

    // ---------- 11 ----------

    /** 11. 大量数据：200 个会话 × 每个 20 条消息往返后数量与首尾内容正确。 */
    @Test
    fun largeDatasetRoundTrip() {
        val source = store()
        for (i in 1..200) {
            val target = "U$i"
            for (j in 1..20) {
                val timestamp = "2026-11-11T02:00:" + (if (j - 1 < 10) "0" else "") + (j - 1) + "Z"
                assertTrue(
                    "追加 U$i-m$j 应成功",
                    source.append(
                        target = target,
                        scene = "c2c",
                        message = msg(id = "U$i-m$j", text = "文本 $i-$j", timestamp = timestamp),
                        name = "会话$i",
                        markUnread = false
                    )
                )
            }
        }
        assertEquals("源端会话数", 200, source.size())

        val json = source.toJson()
        assertTrue("大 JSON 长度应完整（>100000 字符），实际 " + json.length, json.length > 100000)

        val restored = store()
        assertEquals("恢复出的会话数", 200, restored.loadJson(json))
        assertEquals("恢复后 size", 200, restored.size())

        for (i in 1..200) {
            val target = "U$i"
            val conv = restored.getConversation(target)!!
            assertEquals("$target 消息条数", 20, conv.messages.size)
            assertEquals("$target name", "会话$i", conv.name)
            assertEquals("$target 首条 id", "$target-m1", conv.messages.first().id)
            assertEquals("$target 首条文本", "文本 $i-1", conv.messages.first().text)
            assertEquals("$target 末条 id", "$target-m20", conv.messages.last().id)
            assertEquals("$target 末条文本", "文本 $i-20", conv.messages.last().text)
            assertEquals("$target lastText", "文本 $i-20", conv.lastText)
            assertEquals("$target lastTimestamp", "2026-11-11T02:00:19Z", conv.lastTimestamp)
        }
    }

    // ---------- 12 ----------

    /** 12. 还原后的会话仍可正常追加消息（append 去重仍生效）。 */
    @Test
    fun appendStillWorksAndDeduplicatesAfterRestore() {
        val source = store()
        assertTrue("首次追加", source.append("U1", "c2c", msg(id = "m1", text = "第一条"), name = "小明"))
        assertEquals("源端未读", 1, source.getConversation("U1")!!.unread)
        assertEquals("源端 replySeq", 1, source.getConversation("U1")!!.replySeq)

        val restored = store()
        assertEquals("恢复出的会话数", 1, restored.loadJson(source.toJson()))
        assertEquals("恢复后消息数", 1, restored.allMessages("U1").size)
        assertEquals("恢复后未读", 1, restored.getConversation("U1")!!.unread)
        assertEquals("恢复后 replyId", "m1", restored.getConversation("U1")!!.replyId)
        assertEquals("恢复后 replySeq", 1, restored.getConversation("U1")!!.replySeq)

        // 同 id 追加应被去重，且不改变 lastText / unread
        assertFalse("重复 id 应被去重", restored.append("U1", "c2c", msg(id = "m1", text = "重复内容")))
        assertEquals("去重后消息数仍为 1", 1, restored.allMessages("U1").size)
        assertEquals("去重后 lastText 不变", "第一条", restored.getConversation("U1")!!.lastText)
        assertEquals("去重后未读不变", 1, restored.getConversation("U1")!!.unread)

        // 新 id 可正常追加并维护摘要字段
        assertTrue("新 id 追加成功", restored.append("U1", "c2c", msg(id = "m2", text = "第二条", timestamp = "2026-11-11T05:00:00Z")))
        assertEquals("追加后消息数", 2, restored.allMessages("U1").size)
        assertEquals("追加后 lastText", "第二条", restored.getConversation("U1")!!.lastText)
        assertEquals("追加后 lastTimestamp", "2026-11-11T05:00:00Z", restored.getConversation("U1")!!.lastTimestamp)
        assertEquals("追加后未读自增", 2, restored.getConversation("U1")!!.unread)
        assertEquals("追加后 replyId", "m2", restored.getConversation("U1")!!.replyId)
        assertEquals("追加后 replySeq 自增", 2, restored.getConversation("U1")!!.replySeq)

        // 二次往返后仍保持一致（可反复持久化）
        val again = store()
        assertEquals("二次恢复会话数", 1, again.loadJson(restored.toJson()))
        assertEquals("二次恢复消息数", 2, again.allMessages("U1").size)
        assertEquals("二次恢复首条", "m1", again.allMessages("U1").first().id)
        assertEquals("二次恢复末条", "m2", again.allMessages("U1").last().id)
        assertEquals("二次恢复 lastText", "第二条", again.getConversation("U1")!!.lastText)
        assertEquals("二次恢复未读", 2, again.getConversation("U1")!!.unread)
    }
}
