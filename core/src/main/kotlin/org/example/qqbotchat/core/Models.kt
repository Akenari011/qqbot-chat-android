package org.example.qqbotchat.core

data class ChatMessage(
    val id: String,
    val text: String,
    val timestamp: String,
    val fromBot: Boolean,
    val authorName: String,
    val authorOpenId: String = "",
    val authorAvatar: String? = null,
    val imageUrl: String? = null,
    val videoUrl: String? = null,
    val audioUrl: String? = null,
    val fileName: String? = null,
    val quotedText: String? = null,
    val quotedId: String? = null,
    val duration: Double? = null,
    val buttons: List<String> = emptyList(),
    val reactions: Map<String, Int> = emptyMap(),
    val status: String = "sent"
) {
    val isImage: Boolean get() = imageUrl != null
    val isVideo: Boolean get() = videoUrl != null
    val isAudio: Boolean get() = audioUrl != null
    val isFile: Boolean get() = fileName != null
    val summary: String
        get() = when {
            isImage -> "[图片]"
            isVideo -> "[视频]"
            isAudio -> "[语音]"
            isFile -> "[文件] " + (fileName ?: "")
            else -> text
        }
}

data class Conversation(
    val id: String,
    val scene: String,              // c2c | group | channel
    var name: String,
    var lastText: String = "",
    var lastTimestamp: String = "",
    var unread: Int = 0,
    val messages: MutableList<ChatMessage> = mutableListOf(),
    var replyId: String = "",
    var replySeq: Int = 0,
    var pinned: Boolean = false,
    var archived: Boolean = false,
    var muted: Boolean = false,
    var remark: String? = null,
    var avatarPath: String? = null
) {
    val displayName: String get() = remark?.takeIf { it.isNotBlank() } ?: name
}

data class Credentials(val appId: String, val clientSecret: String)

/** Parsed QQ gateway dispatch. */
data class IncomingEvent(
    val kind: String,
    val target: String,
    val scene: String,
    val message: ChatMessage,
    val messageId: String
)
