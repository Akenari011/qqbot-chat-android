// 设计参考 Telegram for Android (GPL-2.0)
// 本文件定义各屏幕的不可变 UI 状态（由 ChatViewModel 用 StateFlow 暴露）。
// 来自 TG 的常量：无（状态模型不含视觉常量）；列表项 / 气泡的字号色值见 ui/theme/Dimens.kt 与 Color.kt，
// 字段语义对照 TG DialogCell（会话）与 ChatMessageCell（消息）。
package org.example.qqbotchat.vm

import org.example.qqbotchat.core.ChatMessage
import org.example.qqbotchat.core.Conversation

/** 登录页：AppID / AppSecret / 连接状态。 */
data class LoginUiState(
    val appId: String = "",
    val appSecret: String = "",
    val status: String = "未连接",
    val connecting: Boolean = false,
    val connected: Boolean = false,
)

/** 会话列表页：直接持有 :core 的 Conversation，便于 DialogCell 原样渲染。 */
data class ConversationListUiState(
    val conversations: List<Conversation> = emptyList(),
    val query: String = "",
)

/** 聊天页：当前会话、消息、草稿、引用、搜索状态。 */
data class ChatUiState(
    val conversationId: String = "",
    val scene: String = "c2c",
    val title: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val draft: String = "",
    val replyingTo: ChatMessage? = null,
    val searchQuery: String = "",
    val searchResults: List<ChatMessage> = emptyList(),
    val actionTarget: ChatMessage? = null,
    val toolsTitle: String? = null,
    val toolsItems: List<String> = emptyList(),
)

/** 设置页：主题、字体缩放、操作提示与导出日志预览。 */
data class SettingsUiState(
    val darkTheme: Boolean = false,
    val blueGradient: Boolean = false,   // 可选「蓝色渐变气泡」主题（iOS 版规格），默认关闭=TG #FFEFFFDE
    val fontScale: Float = 1f,
    val status: String = "",
    val exportText: String? = null,
)
