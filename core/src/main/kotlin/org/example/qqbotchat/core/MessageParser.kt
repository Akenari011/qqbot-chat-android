package org.example.qqbotchat.core

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.util.Calendar

/**
 * Pure-Kotlin parsing of QQ Bot gateway payloads. No Android dependencies so it
 * can be unit-tested on a plain JVM (which is what the build server runs).
 */
object MessageParser {

    private val mentionPattern = Regex("<@!?([A-Za-z0-9_-]+)>")
    private val facePattern = Regex("<faceType=[0-9]+,faceId=\"?([0-9]+)\"?[^>]*>")

    private val faceMap: Map<Int, String> = mapOf(
        0 to "🙂", 1 to "😕", 2 to "😍", 3 to "😳",
        4 to "😎", 5 to "😢", 6 to "😊", 7 to "🤐",
        8 to "😴", 9 to "😭", 10 to "😅", 11 to "😡",
        12 to "😜", 13 to "😁", 14 to "😲", 15 to "😞",
        16 to "😎", 17 to "😰", 18 to "😫", 19 to "🤢",
        20 to "😏", 21 to "😊", 22 to "😒", 23 to "😤",
        24 to "😋", 25 to "😪", 26 to "😱", 27 to "😓",
        28 to "😄", 29 to "😌", 30 to "😤", 31 to "🤬",
        32 to "😕", 33 to "🤫", 34 to "😵", 35 to "😖",
        37 to "☠️", 38 to "🔨", 39 to "👋", 40 to "😅",
        41 to "😏", 42 to "👏", 43 to "😳", 44 to "😏",
        45 to "😤", 46 to "😤", 47 to "😪", 48 to "😒",
        49 to "🥺", 50 to "🥺", 51 to "😏", 52 to "😘",
        53 to "😱", 54 to "🥺", 55 to "🔪", 56 to "🍊",
        57 to "🍺", 58 to "🏀", 59 to "🏓", 60 to "☕",
        61 to "🍚", 62 to "🐷", 63 to "🌹", 64 to "🥀",
        65 to "❤️", 66 to "❤️", 67 to "💔", 68 to "🎂",
        69 to "⚡", 70 to "💣", 71 to "🔪", 72 to "⚽",
        73 to "🐞", 74 to "💩", 75 to "🌙", 76 to "☀️",
        77 to "🎁", 78 to "🤗", 79 to "👍", 80 to "👎",
        81 to "🤝", 82 to "✌️", 83 to "🙏", 84 to "😏",
        85 to "👊", 86 to "👎", 87 to "😘", 88 to "❌",
        89 to "👌", 90 to "❤️", 91 to "😘", 92 to "🕺",
        93 to "😨", 94 to "😤", 95 to "🔀", 96 to "🙇",
        97 to "😳", 98 to "🤸", 99 to "👋", 100 to "😆",
        101 to "🕺", 102 to "😘", 103 to "☯️", 104 to "☯️",
        105 to "🥺"
    )

    /** Replace QQ markup (<@id>, <faceType...>) with human-readable text. */
    fun cleanContent(raw: String, payload: JsonObject? = null): String {
        var result = raw
        val names = mutableMapOf<String, String>()
        payload?.getAsJsonArray("mentions")?.forEach { el ->
            if (el is JsonObject) {
                val id = el.get("id")?.asString ?: el.get("user_openid")?.asString ?: ""
                val name = el.get("username")?.asString
                if (id.isNotEmpty() && !name.isNullOrEmpty()) names[id] = name
            }
        }
        result = mentionPattern.replace(result) { m ->
            val id = m.groupValues[1]
            "@" + (names[id] ?: "成员 " + id.takeLast(6))
        }
        result = facePattern.replace(result) { m ->
            val id = m.groupValues[1].toIntOrNull() ?: -1
            faceMap[id] ?: "🙂"
        }
        return result
    }

    fun isSameGroup(a: ChatMessage, b: ChatMessage): Boolean =
        a.fromBot == b.fromBot &&
            a.authorName == b.authorName &&
            sameDay(a.timestamp, b.timestamp) &&
            kotlin.math.abs(epoch(a.timestamp) - epoch(b.timestamp)) < 600_000L

    private fun sameDay(a: String, b: String): Boolean {
        val ca = Calendar.getInstance().apply { timeInMillis = epoch(a) }
        val cb = Calendar.getInstance().apply { timeInMillis = epoch(b) }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
            ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }

    fun epoch(iso: String): Long = try {
        java.time.Instant.parse(iso).toEpochMilli()
    } catch (e: Exception) {
        System.currentTimeMillis()
    }

    /** Map a gateway event name to conversation scene. */
    fun sceneOf(kind: String): String = when (kind) {
        "AT_MESSAGE_CREATE", "MESSAGE_CREATE" -> "channel"
        "GROUP_AT_MESSAGE_CREATE", "GROUP_MESSAGE_CREATE" -> "group"
        else -> "c2c"
    }

    fun isMessageEvent(kind: String): Boolean = kind in setOf(
        "C2C_MESSAGE_CREATE", "GROUP_AT_MESSAGE_CREATE", "GROUP_MESSAGE_CREATE",
        "AT_MESSAGE_CREATE", "MESSAGE_CREATE"
    )

    /** Parse a MESSAGE_CREATE-style payload into a ChatMessage. */
    fun parseMessage(kind: String, payload: JsonObject): IncomingEvent? {
        val author = payload.getAsJsonObject("author") ?: JsonObject()
        val scene = sceneOf(kind)
        val target = when (scene) {
            "channel" -> payload.get("channel_id")?.asString ?: ""
            "group" -> payload.get("group_openid")?.asString ?: ""
            else -> author.get("user_openid")?.asString ?: ""
        }
        val id = payload.get("id")?.asString ?: return null
        if (target.isEmpty()) return null

        val username = author.get("username")?.asString ?: ""
        val memberId = author.get("member_openid")?.asString
            ?: author.get("id")?.asString ?: ""
        val conversationName = when (scene) {
            "channel" -> "频道 " + target.takeLast(6)
            "group" -> "群聊 " + target.takeLast(6)
            else -> username.ifEmpty { "QQ 用户" }
        }
        val authorName = when {
            scene == "c2c" -> conversationName
            username.isNotEmpty() -> username
            memberId.isNotEmpty() -> "成员 " + memberId.takeLast(6)
            else -> "成员"
        }

        val content = cleanContent(payload.get("content")?.asString ?: "", payload)
        val attachments: JsonArray? = payload.getAsJsonArray("attachments")
        val attachment = attachments?.firstOrNull() as? JsonObject
        val remote = attachment?.get("url")?.asString?.let { if (it.startsWith("//")) "https:$it" else it }
        val contentType = attachment?.get("content_type")?.asString?.lowercase() ?: ""
        val filename = attachment?.get("filename")?.asString?.lowercase() ?: ""
        val isVideo = contentType.startsWith("video") || filename.endsWith(".mp4") || filename.endsWith(".mov")
        val isAudio = contentType.startsWith("audio") ||
            filename.endsWith(".silk") || filename.endsWith(".amr") || filename.endsWith(".m4a")

        val quoteRef = payload.getAsJsonObject("message_reference")
        val buttons = mutableListOf<String>()
        payload.getAsJsonObject("keyboard")?.getAsJsonObject("content")
            ?.getAsJsonArray("rows")?.forEach { row ->
                (row as? JsonObject)?.getAsJsonArray("buttons")?.forEach { btn ->
                    (btn as? JsonObject)?.getAsJsonObject("render_data")
                        ?.get("label")?.asString?.let { buttons.add(it) }
                }
            }

        val message = ChatMessage(
            id = id,
            text = content,
            timestamp = payload.get("timestamp")?.asString ?: java.time.Instant.now().toString(),
            fromBot = false,
            authorName = authorName,
            authorOpenId = memberId,
            authorAvatar = author.get("avatar")?.asString,
            imageUrl = if (!isVideo && !isAudio) remote else null,
            videoUrl = if (isVideo) remote else null,
            audioUrl = if (isAudio) remote else null,
            quotedId = quoteRef?.get("message_id")?.asString,
            buttons = buttons
        )
        return IncomingEvent(kind, target, scene, message, id)
    }
}
