package org.example.qqbotchat.core

/** Synchronous network work; the Android caller runs this on Dispatchers.IO. */
class MessageDelivery(private val store: ConversationStore, private val api: QQBotApi) {
    fun send(targetId: String, localId: String, mediaFileInfo: String? = null) {
        val conversation = store.reserveReply(targetId) ?: return
        val message = conversation.messages.firstOrNull { it.id == localId } ?: return
        try {
            require(conversation.scene == "channel" || conversation.replyId.isNotBlank()) {
                "请先让用户向机器人发送消息，再回复该会话"
            }
            val quote = conversation.messages.firstOrNull { it.id == message.quotedId }?.remoteId
            val result = api.sendMessage(
                scene = conversation.scene,
                targetId = targetId,
                text = message.text,
                replyId = conversation.replyId,
                replySeq = conversation.replySeq,
                quoteId = quote,
                mediaFileInfo = mediaFileInfo,
            )
            val remoteId = listOf("id", "message_id").firstNotNullOfOrNull { key ->
                result.get(key)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }
            }
            store.updateMessage(targetId, localId) { it.copy(status = "sent", serverId = remoteId) }
        } catch (error: Exception) {
            store.updateMessage(targetId, localId) { it.copy(status = "failed") }
            throw error
        }
    }

    fun recall(targetId: String, localId: String) {
        val conversation = store.snapshot(targetId) ?: return
        val message = conversation.messages.firstOrNull { it.id == localId } ?: return
        require(message.fromBot && message.status == "sent") { "这条消息不能撤回" }
        val remoteId = requireNotNull(message.remoteId) { "缺少服务器消息编号，无法撤回" }
        api.recallMessage(conversation.scene, targetId, remoteId)
        // Only hide the content after the server confirms the operation.
        store.updateMessage(targetId, localId) { it.copy(status = "recalled") }
    }
}
