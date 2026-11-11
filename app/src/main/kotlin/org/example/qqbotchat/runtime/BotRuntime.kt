// 设计参考 Telegram for Android (GPL-2.0)
// 本文件是 :app 与 :core 之间的桥接辅助：演示数据播种 + 网关状态中文映射。
// 来自 TG 的常量：无（不含视觉常量；色值/尺寸见 ui/theme/Color.kt 与 Dimens.kt）。
//
// 接线说明：:core 的 QQBotApi / GatewayClient / ConversationStore 已落地，
// ChatViewModel 直接持有 org.example.qqbotchat.core.{ConversationStore, GatewayClient, QQBotApi}，
// 本文件不再定义接口占位。若之后需把 app 与 core 隔离，可按下列「同名契约」切回占位接口，
// 只需把 ChatViewModel / 本文件的 import 从 org.example.qqbotchat.core 改回 runtime 即可：
//   interface GatewayClient { fun connect(); fun close(); var listener: GatewayListener }
//   interface QQBotApi { fun sendMessage(...); fun recallMessage(...); fun getGatewayUrl() }
//   interface ConversationStore { fun allConversations(...); fun allMessages(...); fun onIncoming(...) }
package org.example.qqbotchat.runtime

import org.example.qqbotchat.core.ChatMessage
import org.example.qqbotchat.core.ConversationStore
import org.example.qqbotchat.core.GatewayStates
import java.time.Instant

/** 把 :core 的 GatewayStates 常量映射成中文 UI 状态文案。 */
fun stateText(state: String): String = when (state) {
    GatewayStates.CONNECTING -> "正在连接网关…"
    GatewayStates.CONNECTED -> "WebSocket 已连接"
    GatewayStates.IDENTIFYING -> "正在鉴权…"
    GatewayStates.RESUMING -> "正在恢复会话…"
    GatewayStates.READY -> "已连接 · 网关就绪"
    GatewayStates.RESUMED -> "已连接 · 会话已恢复"
    GatewayStates.RECONNECTING -> "正在重连…"
    GatewayStates.CLOSED -> "连接已关闭"
    else -> state
}

/**
 * 预置演示会话，保证未接真实网关时列表页 / 聊天页也能完整呈现 TG 风格布局。
 * 全部通过 :core 的 ConversationStore 真实方法写入（append / setPinned / setUnread / setMuted）。
 */
fun demoSeed(store: ConversationStore) {
    val now = Instant.now()
    fun iso(secondsAgo: Long): String = now.minusSeconds(secondsAgo).toString()

    store.append(
        target = "demo-c2c", scene = "c2c", name = "QQ 官方助手", markUnread = false,
        message = ChatMessage("m1", "你好，欢迎使用 QQBot Chat 👋", iso(7200), false, "QQ 官方助手"),
    )
    store.append(
        target = "demo-c2c", scene = "c2c", name = "QQ 官方助手", markUnread = false,
        message = ChatMessage("m2", "界面参照 Telegram for Android 的视觉规范实现。", iso(7100), false, "QQ 官方助手"),
    )
    store.append(
        target = "demo-c2c", scene = "c2c", name = "QQ 官方助手", markUnread = false,
        message = ChatMessage("m3", "气泡、时间和双勾都对齐了 👌", iso(3600), true, "我", status = "read"),
    )
    store.append(
        target = "demo-c2c", scene = "c2c", name = "QQ 官方助手", markUnread = false,
        message = ChatMessage("m4", "有任何问题随时告诉我～", iso(180), false, "QQ 官方助手"),
    )
    // 各类媒体 / 按钮 / 表态 / 引用，用于验证渲染与交互（图片用公开静态资源，便于 Coil 加载）。
    store.append(
        target = "demo-c2c", scene = "c2c", name = "QQ 官方助手", markUnread = false,
        message = ChatMessage(
            "m5", "", iso(160), false, "QQ 官方助手",
            imageUrl = "https://avatars.githubusercontent.com/u/17485625?s=200&v=4",
            reactions = mapOf("👍" to 3, "❤️" to 1),
        ),
    )
    store.append(
        target = "demo-c2c", scene = "c2c", name = "QQ 官方助手", markUnread = false,
        message = ChatMessage(
            "m6", "", iso(150), true, "我", status = "read",
            videoUrl = "https://example.invalid/demo.mp4",
        ),
    )
    store.append(
        target = "demo-c2c", scene = "c2c", name = "QQ 官方助手", markUnread = false,
        message = ChatMessage(
            "m7", "", iso(140), false, "QQ 官方助手",
            audioUrl = "https://example.invalid/voice.m4a", duration = 12.0,
        ),
    )
    store.append(
        target = "demo-c2c", scene = "c2c", name = "QQ 官方助手", markUnread = false,
        message = ChatMessage(
            "m8", "", iso(130), false, "QQ 官方助手",
            fileName = "QQBot-Chat-说明.pdf",
        ),
    )
    store.append(
        target = "demo-c2c", scene = "c2c", name = "QQ 官方助手", markUnread = false,
        message = ChatMessage(
            "m9", "需要哪方面的帮助？", iso(120), false, "QQ 官方助手",
            buttons = listOf("功能说明", "连接问题", "反馈 bug"),
        ),
    )
    store.append(
        target = "demo-c2c", scene = "c2c", name = "QQ 官方助手", markUnread = false,
        message = ChatMessage(
            "m10", "这是引用回复的示例", iso(110), true, "我", status = "read",
            quotedText = "有任何问题随时告诉我～", quotedId = "m4",
        ),
    )
    store.append(
        target = "demo-c2c", scene = "c2c", name = "QQ 官方助手", markUnread = false,
        message = ChatMessage(
            "m11", "文档见 https://bot.q.qq.com/wiki 或 www.qq.com", iso(100), false, "QQ 官方助手",
        ),
    )
    store.setPinned("demo-c2c", true)
    store.setUnread("demo-c2c", 2)

    store.append(
        target = "demo-group", scene = "group", name = "Android 开发交流群", markUnread = false,
        message = ChatMessage("g1", "今天聊点 Compose 性能～", iso(5400), false, "小林"),
    )
    store.append(
        target = "demo-group", scene = "group", name = "Android 开发交流群", markUnread = false,
        message = ChatMessage("g2", "重组范围要尽量收窄", iso(5200), false, "阿杰"),
    )
    store.append(
        target = "demo-group", scene = "group", name = "Android 开发交流群", markUnread = false,
        message = ChatMessage("g3", "收到，我回去改一下 👍", iso(1800), true, "我", status = "read"),
    )
    store.append(
        target = "demo-group", scene = "group", name = "Android 开发交流群", markUnread = false,
        message = ChatMessage("g4", "LazyColumn 的 key 一定要加", iso(900), false, "小林"),
    )
    store.setUnread("demo-group", 5)

    store.append(
        target = "demo-channel", scene = "channel", name = "频道 · 公告", markUnread = false,
        message = ChatMessage("c1", "版本 1.0 已发布，欢迎体验 🎉", iso(86400), false, "频道 · 公告"),
    )

    store.append(
        target = "demo-files", scene = "c2c", name = "文件传输助手", markUnread = false,
        message = ChatMessage(
            "f1", "设计说明.pdf", iso(172800), true, "我",
            fileName = "设计说明.pdf", status = "read",
        ),
    )
    store.setMuted("demo-files", true)
}
