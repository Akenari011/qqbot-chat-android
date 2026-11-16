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
import java.util.UUID
import kotlinx.coroutines.channels.Channel
import org.example.qqbotchat.core.ConversationFilter
import org.example.qqbotchat.core.MessageDelivery
import org.example.qqbotchat.core.matchesQuery

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    // ---- :core 依赖（真实类，非占位）----
    private var store = ConversationStore()
    private var sessionVersion = 0
    private var visibleConversationId: String? = null
    private val persistence = Channel<String>(Channel.CONFLATED)
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
        viewModelScope.launch(Dispatchers.IO) {
            for (json in persistence) {
                if (!repository.saveJson(json)) withContext(Dispatchers.Main) {
                    val message = getApplication<Application>().getString(org.example.qqbotchat.R.string.storage_write_failed)
                    _chat.update { it.copy(error = message) }
                    _settings.update { it.copy(status = message) }
                }
            }
        }
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

    private fun persist() {
        if (!isDemoMode) persistence.trySend(store.toJson())
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
        if (s.connecting) return@launch
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

        gateway?.close()
        val version = ++sessionVersion
        val newGateway = GatewayClient(
            newApi,
            CallbackGatewayListener(
                eventHandler = { event -> viewModelScope.launch {
                    if (version == sessionVersion) handleIncoming(event)
                } },
                stateHandler = { state -> viewModelScope.launch {
                    if (version == sessionVersion) {
                        _login.update { it.copy(status = stateText(state)) }
                        _conversations.update { it.copy(connectionStatus = stateText(state)) }
                    }
                } },
                logHandler = { line -> appendLog(line) },
            ),
        )
        api = newApi
        gateway = newGateway

        // 真实凭证登录成功：退出演示模式，清掉内存里的演示会话。
        if (isDemoMode) {
            store = ConversationStore()
            repository.load(store)
        }
        isDemoMode = false
        _conversations.value = ConversationListUiState()
        _chat.value = ChatUiState()
        refreshConversations()

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
        gateway?.close()
        gateway = null
        api = null
        sessionVersion++
        isDemoMode = true
        store = ConversationStore()
        _chat.value = ChatUiState()
        _conversations.value = ConversationListUiState()
        demoSeed(store)
        refreshConversations()
    }

    fun logout() {
        sessionVersion++
        visibleConversationId = null
        gateway?.close()
        gateway = null
        api = null
        if (isDemoMode) {
            store = ConversationStore()
            repository.load(store)
        }
        isDemoMode = false
        _conversations.value = ConversationListUiState()
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
        store.onIncoming(event, markUnread = visibleConversationId != event.target)
        if (_chat.value.conversationId == event.target) refreshChat(event.target)
        refreshConversations()
    }

    // ---- 会话列表 ----
    private fun refreshConversations(save: Boolean = true) {
        val all = store.allConversations()
        _conversations.update {
            it.copy(
                conversations = all.filterNot { conversation -> conversation.archived },
                archivedConversations = all.filter { conversation -> conversation.archived },
                connectionStatus = _login.value.status,
                demoMode = isDemoMode,
            )
        }
        if (save) persist()
    }

    fun setFilter(filter: ConversationFilter) {
        _conversations.update { it.copy(filter = filter) }
    }

    /** 切换「查看归档」模式。 */
    fun toggleArchivedView() {
        _conversations.update { it.copy(showingArchived = !it.showingArchived) }
    }

    fun onQueryChange(value: String) {
        _conversations.update { it.copy(query = value) }
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
        visibleConversationId = id
        store.markRead(id)
        val conversation = store.snapshot(id) ?: return
        if (_chat.value.conversationId != id) {
            _chat.value = ChatUiState(
                conversationId = id,
                scene = conversation.scene,
                title = conversation.displayName,
                messages = conversation.messages.toList(),
                draft = conversation.draft,
                replyingTo = conversation.messages.firstOrNull { it.id == conversation.draftReplyId },
                demoMode = isDemoMode,
            )
        } else refreshChat(id)
        refreshConversations()
    }

    fun closeConversation(id: String) {
        if (visibleConversationId == id) visibleConversationId = null
    }

    private fun refreshChat(id: String) {
        val messages = store.allMessages(id)
        _chat.update { state ->
            if (state.conversationId != id) state else state.copy(
                messages = messages,
                searchResults = if (state.searchQuery.isBlank()) emptyList()
                    else messages.filter { it.matchesQuery(state.searchQuery) },
            )
        }
    }

    fun onDraftChange(value: String) {
        _chat.update { it.copy(draft = value) }
        saveDraft()
    }

    private fun saveDraft() {
        val chat = _chat.value
        store.setDraft(chat.conversationId, chat.draft, chat.replyingTo?.id)
        refreshConversations()
    }

    fun setReplyingTo(message: ChatMessage) {
        _chat.update { it.copy(replyingTo = message) }
        saveDraft()
    }

    fun clearReply() {
        _chat.update { it.copy(replyingTo = null) }
        saveDraft()
    }

    fun send() {
        val chat = _chat.value
        val text = chat.draft.trim()
        if (text.isEmpty()) return
        val conversation = store.snapshot(chat.conversationId) ?: return
        val message = ChatMessage(
            id = UUID.randomUUID().toString(), text = text,
            timestamp = Instant.now().toString(), fromBot = true, authorName = "我",
            status = "sending", quotedId = chat.replyingTo?.id, quotedText = chat.replyingTo?.summary,
        )
        store.recordOutgoing(conversation.id, conversation.scene, message)
        _chat.update { it.copy(draft = "", replyingTo = null) }
        saveDraft()
        refreshChat(conversation.id)
        deliver(conversation.id, message.id)
    }

    private fun deliver(targetId: String, messageId: String) {
        val targetStore = store
        val currentApi = api
        val version = sessionVersion
        val demo = isDemoMode
        viewModelScope.launch {
            val error = withContext(Dispatchers.IO) {
                runCatching {
                    if (demo) targetStore.updateMessage(targetId, messageId) { it.copy(status = "sent") }
                    else MessageDelivery(targetStore, requireNotNull(currentApi) { "尚未连接网关" })
                        .send(targetId, messageId)
                }.exceptionOrNull()
            }
            if (version != sessionVersion) return@launch
            if (error != null) {
                targetStore.updateMessage(targetId, messageId) { it.copy(status = "failed") }
                reportError(targetId, error)
            }
            refreshChat(targetId)
            refreshConversations()
        }
    }

    fun retry(messageId: String) {
        val targetId = _chat.value.conversationId
        val message = store.allMessages(targetId).firstOrNull { it.id == messageId } ?: return
        if (!message.fromBot || message.status != "failed") return
        if (!message.isText) {
            _chat.update { it.copy(notice = ChatNotice.MEDIA_RESELECT) }
            return
        }
        store.updateMessage(targetId, messageId) { it.copy(status = "sending") }
        refreshChat(targetId)
        deliver(targetId, messageId)
    }

    fun recall(messageId: String) {
        val targetId = _chat.value.conversationId
        val targetStore = store
        val currentApi = api
        val version = sessionVersion
        val demo = isDemoMode
        viewModelScope.launch {
            val error = withContext(Dispatchers.IO) {
                runCatching {
                    if (demo) targetStore.updateMessage(targetId, messageId) { it.copy(status = "recalled") }
                    else MessageDelivery(targetStore, requireNotNull(currentApi) { "尚未连接网关" })
                        .recall(targetId, messageId)
                }.exceptionOrNull()
            }
            if (version != sessionVersion) return@launch
            if (error != null) reportError(targetId, error)
            refreshChat(targetId)
            refreshConversations()
        }
    }

    private fun reportError(targetId: String, error: Throwable) {
        appendLog(error.message ?: "操作失败")
        _chat.update { if (it.conversationId == targetId) it.copy(error = error.message ?: "操作失败") else it }
    }

    fun dismissChatNotice() { _chat.update { it.copy(error = null, notice = null) } }

    fun searchInChat(query: String) {
        _chat.update {
            it.copy(searchQuery = query, searchResults = if (query.isBlank()) emptyList()
                else it.messages.filter { message -> message.matchesQuery(query) })
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
        store.deleteMessage(id, messageId)
        refreshChat(id)
        refreshConversations()
        dismissActions()
    }

    fun showForwardPicker(messageId: String) {
        val message = store.allMessages(_chat.value.conversationId).firstOrNull { it.id == messageId } ?: return
        if (!message.isText || message.text.isBlank()) {
            _chat.update { it.copy(notice = ChatNotice.TEXT_FORWARD_ONLY) }
            return
        }
        _chat.update { it.copy(forwardMessage = message,
            forwardTargets = store.allConversations().filter { target -> target.id != it.conversationId }) }
    }

    fun dismissForwardPicker() { _chat.update { it.copy(forwardMessage = null) } }

    fun forward(targetId: String) {
        val source = _chat.value.forwardMessage ?: return
        val target = store.snapshot(targetId) ?: return
        val copy = ChatMessage(
            id = UUID.randomUUID().toString(), text = source.text,
            timestamp = Instant.now().toString(), fromBot = true, authorName = "我", status = "sending",
        )
        store.recordOutgoing(targetId, target.scene, copy)
        _chat.update { it.copy(forwardMessage = null, notice = ChatNotice.FORWARDED) }
        refreshConversations()
        deliver(targetId, copy.id)
    }

    fun react(messageId: String, emoji: String) {
        val targetId = _chat.value.conversationId
        if (_chat.value.scene != "channel") return
        val message = store.allMessages(targetId).firstOrNull { it.id == messageId } ?: return
        val currentApi = api
        val targetStore = store
        val demo = isDemoMode
        val version = sessionVersion
        viewModelScope.launch {
            val error = withContext(Dispatchers.IO) {
                runCatching {
                    if (!demo) requireNotNull(currentApi).addReaction(targetId,
                        requireNotNull(message.remoteId), reactionEmojiId(emoji), emojiType = 1)
                }.exceptionOrNull()
            }
            if (version != sessionVersion) return@launch
            if (error == null) targetStore.updateMessage(targetId, messageId) {
                it.copy(reactions = it.reactions + (emoji to ((it.reactions[emoji] ?: 0) + 1)))
            } else reportError(targetId, error)
            refreshChat(targetId)
            refreshConversations()
        }
    }

    /** 点击 keyboard 按钮：QQ Bot 的按钮点击以 INTERACTION_CREATE 事件回传，
     *  此处把按钮文案作为一条普通发出消息，便于端到端演示。 */
    fun onButtonClick(label: String) {
        _chat.update { it.copy(draft = label) }
        send()
    }

    /** Uploads are bounded by the attachment loader and run outside the UI thread. */
    fun sendMedia(bytes: ByteArray, fileName: String, fileType: Int, localPath: String? = null) {
        val targetId = _chat.value.conversationId
        val conversation = store.snapshot(targetId) ?: return
        val local = ChatMessage(
            id = UUID.randomUUID().toString(), text = "", timestamp = Instant.now().toString(),
            fromBot = true, authorName = "我", status = "sending",
            fileName = fileName.takeIf { fileType == 4 },
            imageUrl = "local".takeIf { fileType == 1 }, localImage = localPath.takeIf { fileType == 1 },
            videoUrl = "local".takeIf { fileType == 2 }, audioUrl = "local".takeIf { fileType == 3 },
        )
        store.recordOutgoing(targetId, conversation.scene, local)
        refreshChat(targetId)
        refreshConversations()
        val targetStore = store
        val currentApi = api
        val demo = isDemoMode
        val version = sessionVersion
        viewModelScope.launch {
            val error = withContext(Dispatchers.IO) {
                runCatching {
                    if (demo) targetStore.updateMessage(targetId, local.id) { it.copy(status = "sent") }
                    else {
                        val connectedApi = requireNotNull(currentApi) { "尚未连接网关" }
                        val info = connectedApi.uploadMediaFileInfo(conversation.scene, targetId, fileType, fileName, bytes)
                        MessageDelivery(targetStore, connectedApi).send(targetId, local.id, info)
                    }
                }.exceptionOrNull()
            }
            if (version != sessionVersion) return@launch
            if (error != null) {
                targetStore.updateMessage(targetId, local.id) { it.copy(status = "failed") }
                reportError(targetId, error)
            }
            refreshChat(targetId)
            refreshConversations()
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
                    ChannelAction.GuildChannels -> {
                        val guildId = a.getChannel(conversationId).get("guild_id")?.asString
                        require(!guildId.isNullOrBlank()) { "无法获取当前频道所属的服务器" }
                        renderArray(a.getGuildChannels(guildId), "id", "name")
                    }
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
        val recentLogs = synchronized(logs) { logs.toList().takeLast(50) }
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

    override fun onCleared() {
        gateway?.close()
        super.onCleared()
    }

    fun dismissStatus() {
        _settings.update { it.copy(status = "") }
    }
}
