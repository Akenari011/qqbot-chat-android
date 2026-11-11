package org.example.qqbotchat.core

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject

/** 会话搜索范围。 */
enum class SearchScope {
    /** 仅匹配会话显示名（含备注与原始群名）。 */
    NAME,

    /** 仅匹配会话最后一条消息摘要。 */
    LAST_MESSAGE,

    /** 同时匹配会话名、最后一条消息与全部历史消息。 */
    ALL_HISTORY
}

/**
 * 一条搜索命中。
 *
 * @property conversationId 会话 id（即 target）。
 * @property scene 会话场景 c2c / group / channel。
 * @property displayName 会话显示名（备注优先）。
 * @property matchedText 触发命中的文本片段。
 * @property messages 命中的历史消息列表（仅 [SearchScope.ALL_HISTORY] 可能非空）。
 */
data class SearchHit(
    val conversationId: String,
    val scene: String,
    val displayName: String,
    val matchedText: String,
    val messages: List<ChatMessage> = emptyList()
)

/**
 * 会话与消息的内存存储，支持 Gson JSON 字符串持久化。
 *
 * 纯 Kotlin / JVM，不引用任何 Android 文件 API：调用方负责把 [toJson] 的结果写入文件，
 * 并把读出的字符串传给 [loadJson]，因此可在服务器 JVM 上直接单元测试。
 *
 * 约定：内部以 target 为 key，且 Conversation.id == target（可直接作为发送消息的 targetId）；
 * 场景保存在 Conversation.scene 中。所有公开的读改写方法均加 [Synchronized]，
 * 保证网关线程与 UI 线程并发访问安全。
 */
class ConversationStore(private val gson: Gson = Gson()) {
    companion object {
        /** 当前 JSON 格式版本，用于向后兼容校验。 */
        private const val FORMAT_VERSION = 1
    }

    /** 以 target 为键的会话表，LinkedHashMap 保持插入顺序。 */
    private val conversations = LinkedHashMap<String, Conversation>()

    /**
     * 按 target 取得会话，不存在则创建。
     *
     * @param target 用户 openid / 群 openid / 子频道 id，同时作为 Conversation.id。
     * @param scene c2c / group / channel。
     * @param name 新建会话时使用的名称；为空时按场景生成默认名。
     */
    @Synchronized
    fun getOrCreate(target: String, scene: String, name: String = ""): Conversation {
        val existing = conversations[target]
        if (existing != null) return existing
        val display = name.ifEmpty { defaultName(scene, target, "") }
        val created = Conversation(id = target, scene = scene, name = display)
        conversations[target] = created
        return created
    }

    /** 按 target 查询会话，不存在返回 null。返回的是内部对象引用，批量修改请用 [update]。 */
    @Synchronized
    fun getConversation(target: String): Conversation? = conversations[target]

    /**
     * 追加一条消息，并同步维护会话的摘要字段。
     *
     * - 按 message.id 去重，重复消息直接返回 false；
     * - 更新 lastText / lastTimestamp；
     * - markUnread 为 true 且是非机器人消息时 unread 自增；
     * - 更新 replyId 为最新消息 id，replySeq 为 [incomingSeq] 或自增（保证
     *   QQBotApi.sendMessage 的 msg_seq = replySeq + 1 单调递增）。
     *
     * @return 是否为新追加的消息。
     */
    @Synchronized
    fun append(
        target: String,
        scene: String,
        message: ChatMessage,
        name: String = "",
        markUnread: Boolean = true,
        incomingSeq: Int? = null
    ): Boolean {
        val conversation = getOrCreate(target, scene, name)
        if (conversation.messages.any { it.id == message.id }) return false
        conversation.messages.add(message)
        conversation.lastText = message.summary
        conversation.lastTimestamp = message.timestamp
        if (markUnread && !message.fromBot) conversation.unread += 1
        conversation.replyId = message.id
        conversation.replySeq = incomingSeq ?: (conversation.replySeq + 1)
        return true
    }

    /**
     * 直接把网关解析出的 [IncomingEvent] 落库，自动推导会话显示名。
     *
     * @param markUnread 是否计入未读数，默认 true。
     * @return 是否为新消息。
     */
    @Synchronized
    fun onIncoming(event: IncomingEvent, markUnread: Boolean = true): Boolean {
        val name = defaultName(event.scene, event.target, event.message.authorName)
        return append(event.target, event.scene, event.message, name, markUnread)
    }

    /**
     * 记录一条自己发出的消息（fromBot=true），不计未读、不改动 replyId/replySeq。
     *
     * 同样按 id 去重，并清空该会话的未读数。
     */
    @Synchronized
    fun recordOutgoing(target: String, scene: String, message: ChatMessage): Boolean {
        val conversation = getOrCreate(target, scene)
        if (conversation.messages.any { it.id == message.id }) return false
        conversation.messages.add(message)
        conversation.lastText = message.summary
        conversation.lastTimestamp = message.timestamp
        conversation.unread = 0
        return true
    }

    /** 显式设置被动回复的 msg_id 与 msg_seq，失败返回 false。 */
    @Synchronized
    fun setReply(target: String, replyId: String, replySeq: Int): Boolean {
        val conversation = conversations[target] ?: return false
        conversation.replyId = replyId
        conversation.replySeq = replySeq
        return true
    }

    /**
     * 发送成功后回写已使用的 msg_seq，使下次 sendMessage 得到 replySeq+1。
     *
     * @param msgSeq 本次发送实际使用的 msg_seq 值。
     */
    @Synchronized
    fun noteReplySent(target: String, msgSeq: Int): Boolean {
        val conversation = conversations[target] ?: return false
        conversation.replySeq = msgSeq
        return true
    }

    /** 标记会话已读（unread 归零）。 */
    @Synchronized
    fun markRead(target: String): Boolean {
        val conversation = conversations[target] ?: return false
        conversation.unread = 0
        return true
    }

    /** 直接设置未读数（负数按 0 处理）。 */
    @Synchronized
    fun setUnread(target: String, count: Int): Boolean {
        val conversation = conversations[target] ?: return false
        conversation.unread = count.coerceAtLeast(0)
        return true
    }

    /** 增加未读数，delta 默认为 1。 */
    @Synchronized
    fun incrementUnread(target: String, delta: Int = 1): Boolean {
        val conversation = conversations[target] ?: return false
        conversation.unread = (conversation.unread + delta).coerceAtLeast(0)
        return true
    }

    /** 设置 / 取消置顶。 */
    @Synchronized
    fun setPinned(target: String, value: Boolean): Boolean {
        val conversation = conversations[target] ?: return false
        conversation.pinned = value
        return true
    }

    /** 设置 / 取消归档。 */
    @Synchronized
    fun setArchived(target: String, value: Boolean): Boolean {
        val conversation = conversations[target] ?: return false
        conversation.archived = value
        return true
    }

    /** 设置 / 取消静音。 */
    @Synchronized
    fun setMuted(target: String, value: Boolean): Boolean {
        val conversation = conversations[target] ?: return false
        conversation.muted = value
        return true
    }

    /** 设置备注（null 或空串表示清除），影响 Conversation.displayName。 */
    @Synchronized
    fun setRemark(target: String, value: String?): Boolean {
        val conversation = conversations[target] ?: return false
        conversation.remark = value
        return true
    }

    /** 设置本地头像文件路径（null 表示清除）。 */
    @Synchronized
    fun setAvatarPath(target: String, value: String?): Boolean {
        val conversation = conversations[target] ?: return false
        conversation.avatarPath = value
        return true
    }

    /** 重命名会话（不影响备注）。 */
    @Synchronized
    fun rename(target: String, name: String): Boolean {
        val conversation = conversations[target] ?: return false
        conversation.name = name
        return true
    }

    /** 在同步保护下批量修改会话字段，返回是否找到该会话。 */
    @Synchronized
    fun update(target: String, block: (Conversation) -> Unit): Boolean {
        val conversation = conversations[target] ?: return false
        block(conversation)
        return true
    }

    /** 删除会话，返回是否原本存在。 */
    @Synchronized
    fun deleteConversation(target: String): Boolean = conversations.remove(target) != null

    /** 清空所有会话。 */
    @Synchronized
    fun clear() = conversations.clear()

    /** 当前会话数量。 */
    @Synchronized
    fun size(): Int = conversations.size

    /**
     * 返回会话列表快照：置顶优先，其次按 lastTimestamp 倒序。
     *
     * @param includeArchived 是否包含已归档会话，默认 true。
     */
    @Synchronized
    fun allConversations(includeArchived: Boolean = true): List<Conversation> {
        val list = conversations.values.filter { includeArchived || !it.archived }
        return list.sortedWith(
            compareByDescending<Conversation> { it.pinned }
                .thenByDescending { MessageParser.epoch(it.lastTimestamp) }
        )
    }

    /** 返回某个会话的全部消息快照（按插入顺序，即时间正序）。 */
    @Synchronized
    fun allMessages(target: String): List<ChatMessage> =
        conversations[target]?.messages?.toList() ?: emptyList()

    /**
     * 分页取最近 N 条消息，返回按时间正序（旧 -> 新）的一段。
     *
     * page=0 取最后 limit 条，page=1 再往前取 limit 条，依此类推。
     * 越界返回空列表；limit &lt;= 0 或 page &lt; 0 也返回空列表。
     */
    @Synchronized
    fun recentMessages(target: String, limit: Int, page: Int = 0): List<ChatMessage> {
        if (limit <= 0 || page < 0) return emptyList()
        val messages = conversations[target]?.messages ?: return emptyList()
        val end = (messages.size - page * limit).coerceAtLeast(0)
        val start = (end - limit).coerceAtLeast(0)
        if (start >= end) return emptyList()
        return messages.subList(start, end).toList()
    }

    /**
     * 搜索会话与消息。
     *
     * - [SearchScope.NAME]：匹配 displayName / name / remark；
     * - [SearchScope.LAST_MESSAGE]：匹配 lastText；
     * - [SearchScope.ALL_HISTORY]：上述都匹配，并额外扫描全部历史消息的
     *   text / summary / fileName / quotedText / authorName / buttons。
     *
     * 忽略大小写，空 query 返回空列表；结果按置顶与最后消息时间排序。
     */
    @Synchronized
    fun search(query: String, scope: SearchScope = SearchScope.ALL_HISTORY): List<SearchHit> {
        val keyword = query.trim()
        if (keyword.isEmpty()) return emptyList()
        val hits = mutableListOf<SearchHit>()
        for (conversation in conversations.values) {
            if (scope == SearchScope.NAME || scope == SearchScope.ALL_HISTORY) {
                if (contains(conversation.displayName, keyword) ||
                    contains(conversation.name, keyword) ||
                    contains(conversation.remark, keyword)
                ) {
                    hits.add(
                        SearchHit(
                            conversation.id,
                            conversation.scene,
                            conversation.displayName,
                            conversation.lastText.ifEmpty { conversation.displayName }
                        )
                    )
                    continue
                }
            }
            if (scope == SearchScope.LAST_MESSAGE || scope == SearchScope.ALL_HISTORY) {
                if (contains(conversation.lastText, keyword)) {
                    hits.add(
                        SearchHit(
                            conversation.id,
                            conversation.scene,
                            conversation.displayName,
                            conversation.lastText
                        )
                    )
                    continue
                }
            }
            if (scope == SearchScope.ALL_HISTORY) {
                val matched = conversation.messages.filter { messageMatches(it, keyword) }
                if (matched.isNotEmpty()) {
                    hits.add(
                        SearchHit(
                            conversation.id,
                            conversation.scene,
                            conversation.displayName,
                            matched.last().summary,
                            matched
                        )
                    )
                }
            }
        }
        return hits.sortedWith(
            compareByDescending<SearchHit> { conversations[it.conversationId]?.pinned == true }
                .thenByDescending {
                    MessageParser.epoch(conversations[it.conversationId]?.lastTimestamp ?: "")
                }
        )
    }

    /** 按会话名搜索的便捷入口。 */
    fun searchNames(query: String): List<SearchHit> = search(query, SearchScope.NAME)

    /** 按最后一条消息搜索的便捷入口。 */
    fun searchLastMessages(query: String): List<SearchHit> = search(query, SearchScope.LAST_MESSAGE)

    /** 扫描全部历史消息的便捷入口。 */
    fun searchHistory(query: String): List<SearchHit> = search(query, SearchScope.ALL_HISTORY)

    /**
     * 把全部会话序列化为 JSON 字符串，由调用方负责写入文件。
     *
     * 结构：{"version":1,"conversations":[...]}。手写字段映射而非反射序列化
     * Kotlin data class，避免 Gson 的 Unsafe 分配导致 val 默认值为 null 的问题。
     */
    @Synchronized
    fun toJson(): String {
        val root = JsonObject().apply {
            addProperty("version", FORMAT_VERSION)
            add("conversations", JsonArray().apply {
                conversations.values.forEach { add(conversationToJson(it)) }
            })
        }
        return gson.toJson(root)
    }

    /**
     * 从 JSON 字符串恢复全部会话（先清空内存数据）。
     *
     * @param json [toJson] 的输出；空白字符串等价于清空。
     * @return 恢复出的会话数量。
     * @throws IllegalArgumentException JSON 非法、根节点不是对象或版本过新。
     */
    @Synchronized
    fun loadJson(json: String): Int {
        if (json.isBlank()) {
            conversations.clear()
            return 0
        }
        val root = (try {
            gson.fromJson(json, JsonElement::class.java)
                ?.takeIf { it.isJsonObject }?.asJsonObject
        } catch (e: Exception) {
            throw IllegalArgumentException("会话 JSON 解析失败: " + e.message, e)
        }) ?: throw IllegalArgumentException("会话 JSON 根节点不是对象")
        val version = intOf(root, "version") ?: 0
        if (version > FORMAT_VERSION) {
            throw IllegalArgumentException("不支持的会话数据版本: " + version)
        }
        val array = arrOf(root, "conversations") ?: JsonArray()
        conversations.clear()
        for (element in array) {
            if (!element.isJsonObject) continue
            val conversation = conversationFromJson(element.asJsonObject) ?: continue
            conversations[conversation.id] = conversation
        }
        return conversations.size
    }

    /** 把一个 Conversation 序列化为 JsonObject。 */
    private fun conversationToJson(conversation: Conversation): JsonObject = JsonObject().apply {
        addProperty("id", conversation.id)
        addProperty("scene", conversation.scene)
        addProperty("name", conversation.name)
        addProperty("lastText", conversation.lastText)
        addProperty("lastTimestamp", conversation.lastTimestamp)
        addProperty("unread", conversation.unread)
        addProperty("replyId", conversation.replyId)
        addProperty("replySeq", conversation.replySeq)
        addProperty("pinned", conversation.pinned)
        addProperty("archived", conversation.archived)
        addProperty("muted", conversation.muted)
        addNullable("remark", conversation.remark)
        addNullable("avatarPath", conversation.avatarPath)
        add("messages", JsonArray().apply {
            conversation.messages.forEach { add(messageToJson(it)) }
        })
    }

    /** 从 JsonObject 恢复一个 Conversation；缺少 id 时返回 null，消息按 id 去重。 */
    private fun conversationFromJson(obj: JsonObject): Conversation? {
        val id = str(obj, "id") ?: return null
        val scene = str(obj, "scene") ?: "c2c"
        val conversation = Conversation(
            id = id,
            scene = scene,
            name = str(obj, "name") ?: defaultName(scene, id, "")
        )
        conversation.lastText = str(obj, "lastText") ?: ""
        conversation.lastTimestamp = str(obj, "lastTimestamp") ?: ""
        conversation.unread = intOf(obj, "unread") ?: 0
        conversation.replyId = str(obj, "replyId") ?: ""
        conversation.replySeq = intOf(obj, "replySeq") ?: 0
        conversation.pinned = boolOf(obj, "pinned") ?: false
        conversation.archived = boolOf(obj, "archived") ?: false
        conversation.muted = boolOf(obj, "muted") ?: false
        conversation.remark = str(obj, "remark")
        conversation.avatarPath = str(obj, "avatarPath")
        val array = arrOf(obj, "messages") ?: JsonArray()
        val seen = HashSet<String>()
        for (element in array) {
            if (!element.isJsonObject) continue
            val message = messageFromJson(element.asJsonObject) ?: continue
            if (seen.add(message.id)) conversation.messages.add(message)
        }
        return conversation
    }

    /** 把一个 ChatMessage 序列化为 JsonObject，覆盖 Models.kt 中的全部字段。 */
    private fun messageToJson(message: ChatMessage): JsonObject = JsonObject().apply {
        addProperty("id", message.id)
        addProperty("text", message.text)
        addProperty("timestamp", message.timestamp)
        addProperty("fromBot", message.fromBot)
        addProperty("authorName", message.authorName)
        addProperty("authorOpenId", message.authorOpenId)
        addNullable("authorAvatar", message.authorAvatar)
        addNullable("imageUrl", message.imageUrl)
        addNullable("videoUrl", message.videoUrl)
        addNullable("audioUrl", message.audioUrl)
        addNullable("fileName", message.fileName)
        addNullable("quotedText", message.quotedText)
        addNullable("quotedId", message.quotedId)
        val duration = message.duration
        if (duration == null) add("duration", JsonNull.INSTANCE) else addProperty("duration", duration)
        add("buttons", JsonArray().apply { message.buttons.forEach { add(it) } })
        add("reactions", JsonObject().apply {
            message.reactions.forEach { (key, value) -> addProperty(key, value) }
        })
        addProperty("status", message.status)
    }

    /** 从 JsonObject 恢复一个 ChatMessage；缺少 id 时返回 null。 */
    private fun messageFromJson(obj: JsonObject): ChatMessage? {
        val id = str(obj, "id") ?: return null
        val buttons = mutableListOf<String>()
        arrOf(obj, "buttons")?.forEach { element ->
            if (element.isJsonPrimitive) buttons.add(element.asString)
        }
        val reactions = linkedMapOf<String, Int>()
        val reactionObj = objOf(obj, "reactions")
        if (reactionObj != null) {
            for (entry in reactionObj.entrySet()) {
                val value = entry.value
                if (value.isJsonPrimitive) {
                    try {
                        reactions[entry.key] = value.asInt
                    } catch (e: Exception) {
                        // 忽略非法计数值
                    }
                }
            }
        }
        return ChatMessage(
            id = id,
            text = str(obj, "text") ?: "",
            timestamp = str(obj, "timestamp") ?: "",
            fromBot = boolOf(obj, "fromBot") ?: false,
            authorName = str(obj, "authorName") ?: "",
            authorOpenId = str(obj, "authorOpenId") ?: "",
            authorAvatar = str(obj, "authorAvatar"),
            imageUrl = str(obj, "imageUrl"),
            videoUrl = str(obj, "videoUrl"),
            audioUrl = str(obj, "audioUrl"),
            fileName = str(obj, "fileName"),
            quotedText = str(obj, "quotedText"),
            quotedId = str(obj, "quotedId"),
            duration = doubleOf(obj, "duration"),
            buttons = buttons,
            reactions = reactions,
            status = str(obj, "status") ?: "sent"
        )
    }

    /** 按场景生成默认会话名。 */
    private fun defaultName(scene: String, target: String, authorName: String): String = when (scene) {
        "channel" -> "频道 " + target.takeLast(6)
        "group" -> "群聊 " + target.takeLast(6)
        else -> authorName.ifEmpty { "QQ 用户" }
    }

    /** 判断单条消息是否命中关键词。 */
    private fun messageMatches(message: ChatMessage, keyword: String): Boolean =
        contains(message.text, keyword) ||
            contains(message.summary, keyword) ||
            contains(message.fileName, keyword) ||
            contains(message.quotedText, keyword) ||
            contains(message.authorName, keyword) ||
            message.buttons.any { contains(it, keyword) }

    /** 忽略大小写的包含判断，null 视为未命中。 */
    private fun contains(text: String?, keyword: String): Boolean =
        text?.contains(keyword, ignoreCase = true) == true

    /** 安全读取字符串字段；JsonNull 或非原始值返回 null。 */
    private fun str(obj: JsonObject, key: String): String? {
        val element = obj.get(key) ?: return null
        return if (element.isJsonPrimitive) element.asString else null
    }

    /** 安全读取数组字段；缺失或不是数组返回 null。 */
    private fun arrOf(obj: JsonObject, key: String): JsonArray? {
        val element = obj.get(key) ?: return null
        return if (element.isJsonArray) element.asJsonArray else null
    }

    /** 安全读取对象字段；缺失或不是对象返回 null。 */
    private fun objOf(obj: JsonObject, key: String): JsonObject? {
        val element = obj.get(key) ?: return null
        return if (element.isJsonObject) element.asJsonObject else null
    }

    /** 安全读取数字字段；解析失败返回 null。 */
    private fun numberOf(obj: JsonObject, key: String): Number? {
        val element = obj.get(key) ?: return null
        if (!element.isJsonPrimitive) return null
        return try {
            element.asNumber
        } catch (e: Exception) {
            null
        }
    }

    /** 安全读取 Int 字段。 */
    private fun intOf(obj: JsonObject, key: String): Int? = numberOf(obj, key)?.toInt()

    /** 安全读取 Double 字段。 */
    private fun doubleOf(obj: JsonObject, key: String): Double? = numberOf(obj, key)?.toDouble()

    /** 安全读取 Boolean 字段。 */
    private fun boolOf(obj: JsonObject, key: String): Boolean? {
        val element = obj.get(key) ?: return null
        if (!element.isJsonPrimitive) return null
        return try {
            element.asBoolean
        } catch (e: Exception) {
            null
        }
    }
}

/** 把可空字符串写入 JsonObject：null 写成 JsonNull，非 null 写成普通属性。 */
private fun JsonObject.addNullable(key: String, value: String?) {
    if (value == null) add(key, JsonNull.INSTANCE) else addProperty(key, value)
}
