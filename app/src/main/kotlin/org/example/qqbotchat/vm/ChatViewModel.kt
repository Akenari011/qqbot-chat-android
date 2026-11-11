// 设计参考 Telegram for Android (GPL-2.0)
// 本文件是 UI 与 :core 之间的状态中枢，职责对应 TG 的 MessagesController + 各 Fragment presenter。
// 来自 TG 的常量：无（不含视觉常量；会话排序对照 TG DialogsActivity 的置顶优先 + 按时间倒序）。
//
// 直接持有 :core 的 ConversationStore / QQBotApi / GatewayClient（org.example.qqbotchat.core.*），
// 用 StateFlow 暴露登录、会话列表、聊天、设置四组状态；网关回调经 CallbackGatewayListener 接入。
package org.example.qqbotchat.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.example.qqbotchat.core.CallbackGatewayListener
import org.example.qqbotchat.core.ChatMessage
import org.example.qqbotchat.core.Conversation
import org.example.qqbotchat.core.ConversationStore
import org.example.qqbotchat.core.Credentials
import org.example.qqbotchat.core.GatewayClient
import org.example.qqbotchat.core.IncomingEvent
import org.example.qqbotchat.ui.components.ChannelAction
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.example.qqbotchat.core.MessageParser
import org.example.qqbotchat.core.QQBotApi
import org.example.qqbotchat.data.ConversationRepository
import org.example.qqbotchat.data.SettingsRepository
import org.example.qqbotchat.runtime.demoSeed
import org.example.qqbotchat.runtime.stateText
import org.example.qqbotchat.util.clockTime
import java.time.Instant
import java.util.Collections

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    // ---- :core 依赖（真实类，非占位）----
    private val store: ConversationStore = ConversationStore()
    // 本地持久化：App 重启后恢复会话与消息（对应 TG 的 MessagesStorage）。
    private val repository: ConversationRepository = ConversationRepository(app)
    /** 演示模式：演示数据只在内存，绝不写盘。 */
    private var isDemoMode: Boolean = false
    // 设置持久化（SharedPreferences）：主题 / 蓝色渐变 / 字体缩放，重启后保留。
    private val settingsRepo: SettingsRepository = SettingsRepository(app)
    private var api: QQBotApi? = null
    private var gateway: GatewayClient? = null
    private val logs: MutableList<String> = Collections.synchronizedList(mutableListOf())

    // ---- StateFlow ----
    private val _login = MutableStateFlow(LoginUiState())
    val login: StateFlow<LoginUiState> = _login.asStateFlow()

    private val _conversations = MutableStateFlow(ConversationListUiState())
    val conversations: StateFlow<ConversationListUiState> = _conversations.asStateFlow()

    private val _chat = MutableStateFlow(ChatUiState())
    val chat: StateFlow<ChatUiState> = _chat.asStateFlow()

    private val _settings = MutableStateFlow(SettingsUiState())
    val settings: StateFlow<SettingsUiState> = _settings.asStateFlow()

    init {
        // 启动顺序：先尝试恢复上次的会话数据；只有本地无数据时才注入演示会话。
        // 恢复上次的外观设置（TG 用 SharedConfig 持久化同类偏好）。
        _settings.update {
            it.copy(
                darkTheme = settingsRepo.loadDarkTheme(),
                blueGradient = settingsRepo.loadBlueGradient(),
                fontScale = settingsRepo.loadFontScale(),
            )
        }
        // 启动只恢复真实会话；演示数据不自动注入、也不写盘，等用户点「演示模式」。
        repository.load(store)
        refreshConversations()
    }

    /**
     * 删除所有演示会话（id 以 "demo-" 开头，见 BotRuntime.demoSeed）。
     *
     * 真实凭证登录成功后调用，保证列表里只出现真实 QQ 会话。
     */
    private fun clearDemoConversations() {
        val demoIds = store.allConversations(includeArchived = true)
            .filter { it.id.startsWith("demo-") }
            .map { it.id }
        if (demoIds.isEmpty()) return
        demoIds.forEach { store.deleteConversation(it) }
        if (_chat.value.conversationId.startsWith("demo-")) _chat.update { ChatUiState() }
        persist()
    }

    /**
     * 把当前会话状态写入磁盘（内部吞掉失败，不影响 UI）。
     *
     * 演示模式下**不写盘**：演示数据是 UI 预览用的假会话，
     * 一旦持久化，真实用户重启后会在列表里看到假数据。
     */
    private fun persist() {
        if (isDemoMode) return
        repository.save(store)
    }

    // ---- 登录 / 连接 ----
    fun onAppIdChange(value: String) {
        _login.update { it.copy(appId = value) }
    }

    fun onAppSecretChange(value: String) {
        _login.update { it.copy(appSecret = value) }
    }

    fun connect() = viewModelScope.launch {
        val s = _login.value
        if (s.appId.isBlank() || s.appSecret.isBlank()) {
            _login.update { it.copy(status = "请填写 AppID 与 AppSecret") }
            return@launch
        }
        _login.update { it.copy(connecting = true, status = "正在验证凭证…") }

        val newApi = QQBotApi(Credentials(s.appId, s.appSecret))
        // 真实校验：先向 QQ 换取 access_token，凭证错误时拦在登录页，不允许进入主界面。
        val tokenOk = withContext(Dispatchers.IO) {
            try {
                val result = newApi.getAppAccessToken()
                result.has("access_token") && !result.get("access_token").asString.isNullOrBlank()
            } catch (e: Exception) {
                appendLog("凭证校验失败：" + (e.message ?: "未知错误"))
                false
            }
        }
        if (!tokenOk) {
            _login.update {
                it.copy(connecting = false, connected = false, status = "AppID 或 AppSecret 无效，请检查后重试")
            }
            return@launch
        }

        val newGateway = GatewayClient(
            newApi,
            CallbackGatewayListener(
                eventHandler = { event -> handleIncoming(event) },
                stateHandler = { state -> _login.update { it.copy(status = stateText(state)) } },
                logHandler = { line -> appendLog(line) },
            ),
        )
        api = newApi
        gateway = newGateway

        // 真实凭证登录成功：退出演示模式，清掉内存里的演示会话。
        isDemoMode = false
        clearDemoConversations()

        // 凭证已验证通过，进入主界面；WebSocket 在后台建立，状态文案实时反馈。
        _login.update {
            it.copy(connecting = false, connected = true, status = "已验证 · 正在建立网关连接…")
        }
        withContext(Dispatchers.IO) {
            try {
                newGateway.connect()
            } catch (e: Exception) {
                appendLog("网关连接失败：" + (e.message ?: "未知错误"))
                _login.update { it.copy(status = "网关连接失败（可继续浏览本地数据）") }
            }
        }
    }

    /** 演示模式：不校验凭证，直接浏览本地演示数据（供 UI 预览 / 无凭证环境使用）。 */
    fun enterDemoMode() {
        _login.update {
            it.copy(connecting = false, connected = true, status = "演示模式 · 未连接网关")
        }
        // 演示模式：清空内存后注入演示数据，且整个模式期间不写盘。
        isDemoMode = true
        store.clear()
        demoSeed(store)
        refreshConversations()
    }

    fun logout() {
        gateway?.close()
        gateway = null
        api = null
        _login.update { LoginUiState(status = "已退出登录") }
        _chat.update { ChatUiState() }
        refreshConversations()
    }

    private fun appendLog(line: String) {
        logs.add(line)
    }

    private fun handleIncoming(event: IncomingEvent) {
        // 诊断：图片消息但未解析出 URL 时记录日志，便于定位 QQ 字段差异。
        val diagMsg = event.message
        if (diagMsg.text.contains("[图片]") && diagMsg.imageUrl == null) {
            appendLog("图片消息未解析出 URL: text=" + diagMsg.text.take(40))
        }
        store.onIncoming(event)
        if (_chat.value.conversationId == event.target) refreshChat(event.target)
        refreshConversations()
    }

    // ---- 会话列表 ----
    private fun refreshConversations() {
        val query = _conversations.value.query.trim()
        // 归档会话单独一组，对应 TG DialogsActivity 顶部的「已归档」入口。
        val archived = store.allConversations(includeArchived = true)
            .filter { it.archived }
            .sortedByDescending { MessageParser.epoch(it.lastTimestamp) }
        val list = store.allConversations(includeArchived = false)
            // TG DialogsActivity：置顶会话优先，其余按最后消息时间倒序。
            .sortedWith(
                compareByDescending<Conversation> { it.pinned }
                    .thenByDescending { MessageParser.epoch(it.lastTimestamp) },
            )
            .filter {
                query.isEmpty() ||
                    it.displayName.contains(query, ignoreCase = true) ||
                    it.lastText.contains(query, ignoreCase = true)
            }
        _conversations.update {
            it.copy(
                conversations = list,
                archivedConversations = if (query.isEmpty()) archived else emptyList(),
            )
        }
        // 会话状态变更后落盘，保证 App 重启后数据不丢。
        persist()
    }

    /** 切换「查看归档」模式。 */
    fun toggleArchivedView() {
        _conversations.update { it.copy(showingArchived = !it.showingArchived) }
    }

    fun onQueryChange(value: String) {
        _conversations.update { it.copy(query = value) }
        refreshConversations()
    }

    fun togglePin(id: String) {
        val c = store.getConversation(id) ?: return
        store.setPinned(id, !c.pinned)
        refreshConversations()
    }

    /** 归档 / 取消归档会话。 */
    fun toggleArchive(id: String) {
        val c = store.getConversation(id) ?: return
        store.setArchived(id, !c.archived)
        refreshConversations()
    }

    /** 静音 / 取消静音会话。 */
    fun toggleMute(id: String) {
        val c = store.getConversation(id) ?: return
        store.setMuted(id, !c.muted)
        refreshConversations()
    }

    /** 标记会话已读。 */
    fun markRead(id: String) {
        store.markRead(id)
        refreshConversations()
    }

    /** 删除会话及其本地消息记录。 */
    fun deleteConversation(id: String) {
        store.deleteConversation(id)
        if (_chat.value.conversationId == id) _chat.update { ChatUiState() }
        refreshConversations()
    }

    fun openConversation(id: String) {
        store.markRead(id)
        refreshConversations()
        val c = store.getConversation(id)
        _chat.update {
            it.copy(
                conversationId = id,
                scene = c?.scene ?: "c2c",
                title = c?.displayName ?: "聊天",
                messages = store.allMessages(id),
            )
        }
    }

    // ---- 聊天 / 收发 / 撤回 / 搜索 ----
    private fun refreshChat(id: String) {
        _chat.update { it.copy(messages = store.allMessages(id)) }
    }

    private fun setMessageStatus(conversationId: String, messageId: String, status: String) {
        store.update(conversationId) { c ->
            val index = c.messages.indexOfFirst { it.id == messageId }
            if (index >= 0) c.messages[index] = c.messages[index].copy(status = status)
        }
        persist()
    }

    fun onDraftChange(value: String) {
        _chat.update { it.copy(draft = value) }
    }

    fun setReplyingTo(message: ChatMessage) {
        _chat.update { it.copy(replyingTo = message) }
    }

    fun clearReply() {
        _chat.update { it.copy(replyingTo = null) }
    }

    fun send() {
        val text = _chat.value.draft.trim()
        if (text.isEmpty()) return
        val conversationId = _chat.value.conversationId
        val conversation = store.getConversation(conversationId) ?: return
        val reply = _chat.value.replyingTo
        val message = ChatMessage(
            id = "local-" + System.currentTimeMillis(),
            text = text,
            timestamp = Instant.now().toString(),
            fromBot = true,
            authorName = "我",
            status = "sending",
            quotedId = reply?.id,
            quotedText = reply?.text,
        )
        store.recordOutgoing(conversationId, conversation.scene, message)
        _chat.update { it.copy(draft = "", replyingTo = null) }
        refreshChat(conversationId)
        refreshConversations()

        viewModelScope.launch(Dispatchers.IO) {
            val ok = try {
                val currentApi = api
                if (currentApi == null) {
                    false
                } else {
                    currentApi.sendMessage(
                        scene = conversation.scene,
                        targetId = conversationId,
                        text = text,
                        replyId = conversation.replyId,
                        replySeq = conversation.replySeq,
                        quoteId = reply?.id,
                    )
                    store.noteReplySent(conversationId, conversation.replySeq + 1)
                    true
                }
            } catch (e: Exception) {
                appendLog("发送失败：" + (e.message ?: "未知错误"))
                false
            }
            setMessageStatus(conversationId, message.id, if (ok) "sent" else "failed")
            refreshChat(conversationId)
        }
    }

    fun recall(messageId: String) {
        val conversationId = _chat.value.conversationId
        val conversation = store.getConversation(conversationId) ?: return
        setMessageStatus(conversationId, messageId, "recalled")
        refreshChat(conversationId)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                api?.recallMessage(conversation.scene, conversationId, messageId)
            } catch (e: Exception) {
                appendLog("撤回失败：" + (e.message ?: "未知错误"))
            }
        }
    }

    fun searchInChat(query: String) {
        _chat.update {
            it.copy(
                searchQuery = query,
                searchResults = if (query.isBlank()) emptyList()
                else it.messages.filter { m -> m.text.contains(query.trim(), ignoreCase = true) },
            )
        }
    }

    // ---- 消息操作菜单 ----
    fun showActions(message: ChatMessage) {
        _chat.update { it.copy(actionTarget = message) }
    }

    fun dismissActions() {
        _chat.update { it.copy(actionTarget = null) }
    }

    /** 本地删除一条消息（QQ 无「删除他人消息」能力，仅删本地记录）。 */
    fun deleteMessage(messageId: String) {
        val id = _chat.value.conversationId
        store.update(id) { c -> c.messages.removeAll { it.id == messageId } }
        refreshChat(id)
        refreshConversations()
        dismissActions()
    }

    /** 转发消息到另一个会话：复制正文与媒体 URL 作为新的发出消息。 */
    fun forward(messageId: String, targetId: String) {
        val source = store.getConversation(_chat.value.conversationId)
            ?.messages?.firstOrNull { it.id == messageId } ?: return
        val target = store.getConversation(targetId) ?: return
        val copy = source.copy(
            id = "fwd-" + System.currentTimeMillis(),
            fromBot = true,
            authorName = "我",
            timestamp = Instant.now().toString(),
            status = "sending",
            quotedId = null,
            quotedText = null,
        )
        store.recordOutgoing(targetId, target.scene, copy)
        dismissActions()
        refreshConversations()
        viewModelScope.launch(Dispatchers.IO) {
            val ok = try {
                api?.sendMessage(
                    scene = target.scene,
                    targetId = targetId,
                    text = source.text,
                    replyId = target.replyId,
                    replySeq = target.replySeq,
                )
                true
            } catch (e: Exception) {
                appendLog("转发失败：" + (e.message ?: "未知错误"))
                false
            }
            setMessageStatus(targetId, copy.id, if (ok) "sent" else "failed")
        }
    }

    /** 频道消息表态：把 UI emoji 映射为 QQ 系统 emoji id（官方系统表情 emojiType=1）。 */
    fun react(messageId: String, emoji: String) {
        val conversationId = _chat.value.conversationId
        val scene = store.getConversation(conversationId)?.scene ?: return
        if (scene != "channel") {
            _settings.update { it.copy(status = "表情回应仅频道消息支持") }
            return
        }
        val emojiId = reactionEmojiId(emoji)
        // 本地即时显示徽章
        store.update(conversationId) { c ->
            val i = c.messages.indexOfFirst { it.id == messageId }
            if (i >= 0) {
                val m = c.messages[i]
                c.messages[i] = m.copy(
                    reactions = m.reactions + (emoji to ((m.reactions[emoji] ?: 0) + 1)),
                )
            }
        }
        refreshChat(conversationId)
        dismissActions()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                api?.addReaction(conversationId, messageId, emojiId, emojiType = 1)
            } catch (e: Exception) {
                appendLog("表态失败：" + (e.message ?: "未知错误"))
            }
        }
    }

    /** 点击 keyboard 按钮：QQ Bot 的按钮点击以 INTERACTION_CREATE 事件回传，
     *  此处把按钮文案作为一条普通发出消息，便于端到端演示。 */
    fun onButtonClick(label: String) {
        _chat.update { it.copy(draft = label) }
        send()
    }

    /** 发送富媒体：先上传拿 file_info，再以 msg_type 7 发出。 */
    fun sendMedia(bytes: ByteArray, fileName: String, fileType: Int, localPath: String? = null) {
        val conversationId = _chat.value.conversationId
        val conversation = store.getConversation(conversationId) ?: return
        val local = ChatMessage(
            id = "media-" + System.currentTimeMillis(),
            text = "",
            timestamp = Instant.now().toString(),
            fromBot = true,
            authorName = "我",
            status = "sending",
            fileName = if (fileType == 4) fileName else null,
            imageUrl = if (fileType == 1) "local" else null,
            localImage = if (fileType == 1) localPath else null,
            videoUrl = if (fileType == 2) "local" else null,
            audioUrl = if (fileType == 3) "local" else null,
        )
        store.recordOutgoing(conversationId, conversation.scene, local)
        refreshChat(conversationId)
        refreshConversations()
        viewModelScope.launch(Dispatchers.IO) {
            val ok = try {
                val currentApi = api
                if (currentApi == null) false else {
                    val info = currentApi.uploadMediaFileInfo(
                        conversation.scene, conversationId, fileType, fileName, bytes,
                    )
                    currentApi.sendMessage(
                        scene = conversation.scene,
                        targetId = conversationId,
                        text = "",
                        replyId = conversation.replyId,
                        replySeq = conversation.replySeq,
                        mediaFileInfo = info,
                    )
                    true
                }
            } catch (e: Exception) {
                appendLog("媒体发送失败：" + (e.message ?: "未知错误"))
                false
            }
            setMessageStatus(conversationId, local.id, if (ok) "sent" else "failed")
            refreshChat(conversationId)
        }
    }

    // ---- 频道能力工具菜单 ----
    /** 关闭工具结果对话框。 */
    fun dismissTools() {
        _chat.update { it.copy(toolsTitle = null, toolsItems = emptyList()) }
    }

    /** 在 IO 线程调用 :core 的频道 API，把 JsonArray 转成可读文本列表展示。 */
    fun runChannelAction(action: ChannelAction) {
        val conversationId = _chat.value.conversationId
        val title = when (action) {
            ChannelAction.MyGuilds -> "我的频道"
            ChannelAction.GuildChannels -> "子频道列表"
            ChannelAction.GuildMembers -> "频道成员"
            ChannelAction.ChannelPins -> "置顶消息"
            ChannelAction.ChannelSchedules -> "日程"
            ChannelAction.ChannelHistory -> "最近 50 条历史"
            ChannelAction.Pin -> "设为置顶"
            ChannelAction.Unpin -> "取消置顶"
            ChannelAction.PublishAnnouncement -> "发布公告"
            ChannelAction.CancelAnnouncement -> "取消公告"
            ChannelAction.React -> "表情回应"
            ChannelAction.ReactionUsers -> "表态用户"
            ChannelAction.ExportLog -> "导出日志"
        }
        _chat.update { it.copy(toolsTitle = title, toolsItems = listOf("加载中…")) }
        viewModelScope.launch(Dispatchers.IO) {
            val items = try {
                val a = api
                if (a == null) listOf("未连接网关，无法调用频道 API")
                else when (action) {
                    ChannelAction.MyGuilds -> renderArray(a.getMyGuilds(), "id", "name")
                    ChannelAction.GuildChannels -> renderArray(a.getGuildChannels(conversationId), "id", "name")
                    ChannelAction.GuildMembers -> listOf("成员列表需频道管理权限，官方 Bot API 未提供只读端点")
                    ChannelAction.ChannelPins -> renderArray(a.getChannelPins(conversationId), "message_id", "id")
                    ChannelAction.ChannelSchedules -> renderArray(a.getChannelSchedules(conversationId), "id", "name")
                    ChannelAction.ChannelHistory -> renderArray(a.getChannelMessages(conversationId, 50), "id", "content")
                    ChannelAction.Pin, ChannelAction.Unpin -> {
                        val mid = _chat.value.actionTarget?.id ?: ""
                        if (mid.isBlank()) listOf("请先长按一条消息再执行置顶操作")
                        else {
                            if (action == ChannelAction.Pin) a.addChannelPin(conversationId, mid)
                            else a.removeChannelPin(conversationId, mid)
                            listOf("已对消息 " + mid.take(12) + " 执行操作")
                        }
                    }
                    ChannelAction.PublishAnnouncement, ChannelAction.CancelAnnouncement ->
                        listOf("公告发布/取消需 guild_id 与论坛权限，当前 Bot API 场景不适用")
                    ChannelAction.React -> {
                        val mid = _chat.value.actionTarget?.id ?: ""
                        if (mid.isBlank()) listOf("请先长按一条消息再表态")
                        else {
                            a.addReaction(conversationId, mid, "128077", 1)
                            listOf("已对消息 " + mid.take(12) + " 添加 👍")
                        }
                    }
                    ChannelAction.ReactionUsers -> listOf("表态用户查询需 message_id 与 emoji，已省略")
                    ChannelAction.ExportLog -> {
                        exportLogs()
                        _settings.value.exportText?.lines() ?: listOf("（暂无日志）")
                    }
                }
            } catch (e: Exception) {
                listOf("调用失败：" + (e.message ?: "未知错误").take(160))
            }
            _chat.update { it.copy(toolsTitle = title, toolsItems = items) }
        }
    }

    private fun renderArray(arr: JsonArray, idKey: String, textKey: String): List<String> {
        if (arr.size() == 0) return listOf("（暂无数据）")
        return arr.mapIndexed { i, el ->
            val o = if (el.isJsonObject) el.asJsonObject else JsonObject()
            val id = if (o.has(idKey)) o.get(idKey).asString else ""
            val text = if (o.has(textKey)) o.get(textKey).asString else o.toString().take(80)
            "" + (i + 1) + ". " + (if (text.isNotBlank()) text else o.toString().take(80)) +
                (if (id.isNotBlank()) "  [" + id.take(10) + "]" else "")
        }
    }

    private fun reactionEmojiId(emoji: String): String = when (emoji) {
        "👍" -> "128077"
        "❤️" -> "10084"
        "😂" -> "128514"
        "😮" -> "128558"
        "😢" -> "128546"
        "🔥" -> "128293"
        else -> emoji.codePoints().toArray().firstOrNull()?.toString() ?: "128077"
    }

    // ---- 设置 ----
    fun setBlueGradient(value: Boolean) {
        _settings.update { it.copy(blueGradient = value) }
        settingsRepo.saveBlueGradient(value)
    }

    fun setDarkTheme(value: Boolean) {
        _settings.update { it.copy(darkTheme = value) }
        settingsRepo.saveDarkTheme(value)
    }

    fun setFontScale(value: Float) {
        val clamped = value.coerceIn(SettingsRepository.MIN_FONT_SCALE, SettingsRepository.MAX_FONT_SCALE)
        _settings.update { it.copy(fontScale = clamped) }
        settingsRepo.saveFontScale(clamped)
    }

    fun bumpFontScale(delta: Float) {
        setFontScale(_settings.value.fontScale + delta)
    }

    fun clearAll() {
        store.clear()
        // 清空语义：连本地持久化文件一起删除，重启后不应再出现旧会话。
        repository.clear()
        refreshConversations()
        _chat.update { ChatUiState() }
        _settings.update { it.copy(status = "已清空所有会话与本地记录") }
    }

    fun exportLogs() {
        val lines = mutableListOf<String>()
        store.allConversations().forEach { c ->
            lines += "=== " + c.displayName + " ==="
            c.messages.forEach { m ->
                lines += clockTime(m.timestamp) + " " +
                    (if (m.fromBot) "我" else m.authorName) + "：" + m.summary
            }
        }
        val recentLogs = logs.toList().takeLast(50)
        if (recentLogs.isNotEmpty()) {
            lines += "=== 网关日志 ==="
            lines.addAll(recentLogs)
        }
        _settings.update {
            it.copy(
                exportText = if (lines.isEmpty()) "（暂无日志）" else lines.joinToString("\n"),
                status = "日志已生成，共 " + lines.size + " 行",
            )
        }
    }

    fun dismissExport() {
        _settings.update { it.copy(exportText = null) }
    }

    fun dismissStatus() {
        _settings.update { it.copy(status = "") }
    }
}
