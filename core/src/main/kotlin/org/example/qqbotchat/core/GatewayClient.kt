package org.example.qqbotchat.core

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * 网关状态字符串常量，供 [GatewayListener.onState] 的上报值使用。
 *
 * 取值：connecting / connected / identifying / resuming / ready / resumed /
 * reconnecting / closed。
 */
object GatewayStates {
    const val CONNECTING = "connecting"
    const val CONNECTED = "connected"
    const val IDENTIFYING = "identifying"
    const val RESUMING = "resuming"
    const val READY = "ready"
    const val RESUMED = "resumed"
    const val RECONNECTING = "reconnecting"
    const val CLOSED = "closed"
}

/**
 * 网关事件回调接口。所有方法都有空默认实现，调用方可只覆盖关心的回调。
 */
interface GatewayListener {
    /** 收到一条可展示的消息事件（由 MessageParser.parseMessage 解析得到）。 */
    fun onEvent(event: IncomingEvent) {}

    /** 连接状态变化，值为 [GatewayStates] 中的常量。 */
    fun onState(state: String) {}

    /** 人类可读的运行日志，便于 UI 展示与排查。 */
    fun onLog(line: String) {}

    /**
     * 表情表态事件。
     *
     * @param kind "ADD" 或 "REMOVE"。
     * @param channelId 子频道 id。
     * @param messageId 被表态的消息 id。
     * @param emojiId 表情 id。
     */
    fun onReaction(kind: String, channelId: String, messageId: String, emojiId: String) {}
}

/**
 * 把 lambda 适配成 [GatewayListener] 的便捷实现，避免外部写 object 表达式。
 */
class CallbackGatewayListener(
    private val eventHandler: (IncomingEvent) -> Unit = {},
    private val stateHandler: (String) -> Unit = {},
    private val logHandler: (String) -> Unit = {},
    private val reactionHandler: (String, String, String, String) -> Unit = { _, _, _, _ -> }
) : GatewayListener {
    /** 转发消息事件。 */
    override fun onEvent(event: IncomingEvent) = eventHandler(event)

    /** 转发状态变化。 */
    override fun onState(state: String) = stateHandler(state)

    /** 转发日志。 */
    override fun onLog(line: String) = logHandler(line)

    /** 转发表态事件。 */
    override fun onReaction(kind: String, channelId: String, messageId: String, emojiId: String) =
        reactionHandler(kind, channelId, messageId, emojiId)
}

/**
 * 可注入的 WebSocket 工厂，便于在 JVM 单元测试中替换成假实现，
 * 而无需真实的网络连接（OkHttp 的 WebSocket 本身是接口）。
 */
interface WebSocketFactory {
    /** 用给定 Request 与监听器创建一个 WebSocket。 */
    fun newWebSocket(request: Request, listener: WebSocketListener): WebSocket
}

/**
 * 基于 OkHttpClient 的默认 [WebSocketFactory] 实现。
 */
class OkHttpWebSocketFactory(private val client: OkHttpClient = OkHttpClient()) : WebSocketFactory {
    /** 调用 OkHttpClient.newWebSocket 创建连接。 */
    override fun newWebSocket(request: Request, listener: WebSocketListener): WebSocket =
        client.newWebSocket(request, listener)
}

/**
 * QQ Bot WebSocket 网关客户端（纯 Kotlin / JVM，无 Android 依赖）。
 *
 * 协议要点：
 * - 连接地址由 [gatewayUrlProvider] 提供（通常来自 QQBotApi.getGatewayUrl）；
 * - 收到 op 10 Hello 后按 heartbeat_interval 启动心跳，并发送 op 2 Identify 或 op 6 Resume；
 * - 心跳为 op 1，d 为最近收到的 s 序号或 null；收到 op 11 表示 ACK；
 *   若心跳发出后 interval*2.5 毫秒内未收到 ACK，判定断线并重连；
 * - op 0 Dispatch 按 t 分发：READY 记录 session_id、RESUMED 恢复完成、
 *   消息类事件用 MessageParser.parseMessage 解析后回调 onEvent、
 *   MESSAGE_REACTION_ADD/REMOVE 回调 onReaction、INTERACTION_CREATE 记日志并尝试解析；
 * - op 7 表示服务端要求重连，op 9 表示会话失效（清空 session 后重新 Identify）；
 * - 断线按 min(30, 2^attempt) 秒指数退避重连。
 *
 * 可注入项：[listener]、[webSocketFactory]、[scheduler]、[sleeper]、[nowMillis]，
 * 使得心跳、退避、网络层都能在纯 JVM 单测中被替换，无需 Android 与 gradle。
 * [scheduler] 使用守护线程，不会阻塞 JVM 退出。
 */
class GatewayClient(
    private val tokenProvider: () -> String,
    private val gatewayUrlProvider: () -> String,
    var listener: GatewayListener = object : GatewayListener {},
    private val webSocketFactory: WebSocketFactory = OkHttpWebSocketFactory(),
    private val scheduler: ScheduledExecutorService = newDaemonScheduler(),
    private val sleeper: (Long) -> Unit = { millis -> Thread.sleep(millis) },
    private val nowMillis: () -> Long = { System.currentTimeMillis() }
) {
    companion object {
        /** 官方未给出 heartbeat_interval 时的兜底周期（毫秒）。 */
        private const val DEFAULT_HEARTBEAT_INTERVAL_MS = 41250L

        /** ACK 超时系数：interval * 2.5。 */
        private const val ACK_TIMEOUT_FACTOR = 2.5

        /** 重连退避上限（秒）。 */
        private const val MAX_BACKOFF_SECONDS = 30L

        /** Identify 的 intents：(1&lt;&lt;10)|(1&lt;&lt;25)|(1&lt;&lt;26)|(1&lt;&lt;30)。 */
        private val INTENTS: Int = (1 shl 10) or (1 shl 25) or (1 shl 26) or (1 shl 30)

        /** 计算第 [attempt] 次重连的等待秒数：min(30, 2^attempt)。 */
        private fun backoffSeconds(attempt: Int): Long {
            val shift = attempt.coerceAtMost(30)
            val seconds = 1L shl shift
            return minOf(MAX_BACKOFF_SECONDS, seconds)
        }
    }

    /**
     * 便捷构造：直接复用 [QQBotApi] 取 token 与网关地址。
     *
     * 等价于 tokenProvider = { api.accessToken() }、gatewayUrlProvider = { api.getGatewayUrl() }。
     */
    constructor(
        api: QQBotApi,
        listener: GatewayListener = object : GatewayListener {}
    ) : this(
        tokenProvider = { api.accessToken() },
        gatewayUrlProvider = { api.getGatewayUrl() },
        listener = listener
    )

    private val lock = Any()

    @Volatile private var webSocket: WebSocket? = null
    @Volatile private var sessionId: String = ""
    @Volatile private var lastSeq: Long? = null
    @Volatile private var heartbeatIntervalMs: Long = DEFAULT_HEARTBEAT_INTERVAL_MS
    @Volatile private var closed: Boolean = false
    @Volatile private var reconnecting: Boolean = false
    @Volatile private var reconnectAttempt: Int = 0
    @Volatile private var lastHeartbeatSentAt: Long = 0L
    @Volatile private var lastHeartbeatAckAt: Long = 0L

    private var heartbeatFuture: ScheduledFuture<*>? = null
    private var ackTimeoutFuture: ScheduledFuture<*>? = null

    /** WebSocket 事件监听器：连接、文本帧、关闭与失败都转给内部状态机。 */
    private val wsListener = object : WebSocketListener() {
        /** 连接建立，等待服务端 op 10 Hello 后再发 Identify。 */
        override fun onOpen(webSocket: WebSocket, response: Response) {
            this@GatewayClient.webSocket = webSocket
            emitState(GatewayStates.CONNECTED)
            emitLog("WebSocket 已连接，等待 Hello(op 10)")
        }

        /** 收到文本帧，交给协议分发器。 */
        override fun onMessage(webSocket: WebSocket, text: String) {
            handleFrame(text)
        }

        /** 服务端发起关闭握手，按标准回应 close。 */
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            emitLog("服务端发起关闭: " + code + " " + reason)
            try {
                webSocket.close(code, reason)
            } catch (e: Exception) {
                emitLog("回应关闭失败: " + e.message)
            }
        }

        /** 连接已关闭，进入重连流程。 */
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            emitLog("WebSocket 关闭: " + code + " " + reason)
            handleDisconnect("closed_" + code)
        }

        /** 连接异常，进入重连流程。 */
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            emitLog("WebSocket 异常: " + t.message)
            handleDisconnect("failure")
        }
    }

    /**
     * 启动网关连接（异步，立即返回）。
     *
     * 已处于连接中或重连中时忽略重复调用；[close] 之后不再允许连接。
     */
    fun connect() {
        if (closed) {
            emitLog("GatewayClient 已关闭，忽略 connect()")
            return
        }
        if (webSocket != null || reconnecting) {
            emitLog("已有连接或正在重连，忽略 connect()")
            return
        }
        emitState(GatewayStates.CONNECTING)
        Thread(Runnable { connectOnce() }, "qqbot-gateway-connect").apply {
            isDaemon = true
            start()
        }
    }

    /**
     * 主动关闭网关：停止心跳与重连、关闭连接、释放调度器。
     *
     * 调用后本实例进入终态，不会再自动重连。
     */
    fun close() {
        if (closed) return
        closed = true
        cancelTimers()
        val current = webSocket
        webSocket = null
        if (current != null) {
            try {
                current.close(1000, "client close")
            } catch (e: Exception) {
                emitLog("关闭连接异常: " + e.message)
            }
        }
        sessionId = ""
        lastSeq = null
        emitState(GatewayStates.CLOSED)
        emitLog("GatewayClient 已关闭，不再自动重连")
        scheduler.shutdownNow()
    }

    /** 执行一次连接：取网关 url 并创建 WebSocket；失败则进入重连流程。 */
    private fun connectOnce() {
        if (closed) return
        val url = try {
            gatewayUrlProvider()
        } catch (e: Exception) {
            emitLog("获取网关地址失败: " + e.message)
            handleDisconnect("gateway_url_failed")
            return
        }
        if (url.isBlank()) {
            emitLog("网关地址为空")
            handleDisconnect("empty_gateway_url")
            return
        }
        emitState(GatewayStates.CONNECTING)
        try {
            val request = Request.Builder().url(url).build()
            webSocket = webSocketFactory.newWebSocket(request, wsListener)
        } catch (e: Exception) {
            emitLog("建立 WebSocket 失败: " + e.message)
            handleDisconnect("connect_failed")
        }
    }

    /** 解析并按 op 分发一帧网关消息。 */
    private fun handleFrame(text: String) {
        val payload = (try {
            JsonParser.parseString(text)?.takeIf { it.isJsonObject }?.asJsonObject
        } catch (e: Exception) {
            emitLog("无法解析网关帧: " + e.message)
            null
        }) ?: run {
            emitLog("网关帧不是 JSON 对象，已忽略")
            return
        }
        val op = longOf(payload, "op")?.toInt() ?: return
        when (op) {
            10 -> onHello(payload)
            0 -> onDispatch(payload)
            1 -> {
                emitLog("收到 op 1，服务端要求立即心跳")
                heartbeatTick()
            }
            11 -> onHeartbeatAck()
            7 -> {
                emitLog("收到 op 7 Reconnect，准备重连")
                handleDisconnect("op7_reconnect")
            }
            9 -> onInvalidSession()
            else -> emitLog("未处理的 op: " + op)
        }
    }

    /** 处理 op 10 Hello：记录心跳周期，发送 Identify / Resume 并启动心跳。 */
    private fun onHello(payload: JsonObject) {
        val data = objectOf(payload, "d")
        val interval = longOf(data, "heartbeat_interval") ?: DEFAULT_HEARTBEAT_INTERVAL_MS
        heartbeatIntervalMs = interval.coerceAtLeast(1000L)
        emitLog("收到 Hello，heartbeat_interval=" + heartbeatIntervalMs + "ms")
        if (canResume()) sendResume() else sendIdentify()
        startHeartbeat()
    }

    /** 发送 op 2 Identify：token / intents / shard / properties。 */
    private fun sendIdentify() {
        val shard = JsonArray().apply {
            add(0)
            add(1)
        }
        val data = JsonObject().apply {
            addProperty("token", fullToken())
            addProperty("intents", INTENTS)
            add("shard", shard)
            add("properties", JsonObject())
        }
        send(JsonObject().apply {
            addProperty("op", 2)
            add("d", data)
        })
        emitState(GatewayStates.IDENTIFYING)
        emitLog("已发送 Identify(op 2)")
    }

    /** 发送 op 6 Resume：token / session_id / seq，用于断线续传。 */
    private fun sendResume() {
        val data = JsonObject().apply {
            addProperty("token", fullToken())
            addProperty("session_id", sessionId)
            add("seq", lastSeq?.let { JsonPrimitive(it) } ?: JsonNull.INSTANCE)
        }
        send(JsonObject().apply {
            addProperty("op", 6)
            add("d", data)
        })
        emitState(GatewayStates.RESUMING)
        emitLog("已发送 Resume(op 6), session_id=" + sessionId + ", seq=" + lastSeq)
    }

    /** 组合带 "QQBot " 前缀的 token；若 provider 已带前缀则不重复添加。 */
    private fun fullToken(): String {
        val raw = try {
            tokenProvider().trim()
        } catch (e: Exception) {
            emitLog("获取 token 失败: " + e.message)
            ""
        }
        return if (raw.startsWith("QQBot ")) raw else "QQBot " + raw
    }

    /** 是否具备续传条件：已有 session_id 且收到过序列号 s。 */
    private fun canResume(): Boolean = sessionId.isNotEmpty() && lastSeq != null

    /** 启动固定周期心跳任务。 */
    private fun startHeartbeat() {
        cancelTimers()
        synchronized(lock) {
            heartbeatFuture = scheduler.scheduleAtFixedRate(
                Runnable { heartbeatTick() },
                heartbeatIntervalMs,
                heartbeatIntervalMs,
                TimeUnit.MILLISECONDS
            )
        }
        emitLog("心跳已启动，周期 " + heartbeatIntervalMs + "ms")
    }

    /** 发送一次 op 1 心跳（d 为最近 seq 或 null），并安排 ACK 超时检查。 */
    private fun heartbeatTick() {
        if (closed || webSocket == null) return
        val frame = JsonObject().apply {
            addProperty("op", 1)
            add("d", lastSeq?.let { JsonPrimitive(it) } ?: JsonNull.INSTANCE)
        }
        lastHeartbeatSentAt = nowMillis()
        send(frame)
        scheduleAckTimeout()
        emitLog("发送心跳(op 1), d=" + (lastSeq?.toString() ?: "null"))
    }

    /** 安排 interval*2.5 毫秒的 ACK 超时任务；超时则判定断线。 */
    private fun scheduleAckTimeout() {
        val delayMs = (heartbeatIntervalMs * ACK_TIMEOUT_FACTOR).toLong()
        synchronized(lock) {
            ackTimeoutFuture?.cancel(false)
            ackTimeoutFuture = scheduler.schedule(
                Runnable {
                    emitLog("心跳 ACK 超时（" + delayMs + "ms 未收到 op 11），判定断线")
                    handleDisconnect("heartbeat_ack_timeout")
                },
                delayMs,
                TimeUnit.MILLISECONDS
            )
        }
    }

    /** 收到 op 11：取消 ACK 超时任务并记录时间。 */
    private fun onHeartbeatAck() {
        lastHeartbeatAckAt = nowMillis()
        synchronized(lock) {
            ackTimeoutFuture?.cancel(false)
            ackTimeoutFuture = null
        }
        emitLog("收到 Heartbeat ACK(op 11)，上次心跳耗时 " +
            (lastHeartbeatAckAt - lastHeartbeatSentAt) + "ms")
    }

    /** 处理 op 0 Dispatch：更新 seq，并按 t 分发到具体事件处理器。 */
    private fun onDispatch(payload: JsonObject) {
        val seq = longOf(payload, "s")
        if (seq != null) lastSeq = seq
        val type = stringOf(payload, "t") ?: return
        val data = objectOf(payload, "d")
        when (type) {
            "READY" -> {
                sessionId = stringOf(data, "session_id") ?: ""
                reconnectAttempt = 0
                emitState(GatewayStates.READY)
                emitLog("READY, session_id=" + sessionId)
            }
            "RESUMED" -> {
                reconnectAttempt = 0
                emitState(GatewayStates.RESUMED)
                emitLog("RESUMED, seq=" + lastSeq)
            }
            "MESSAGE_REACTION_ADD" -> onReaction("ADD", data)
            "MESSAGE_REACTION_REMOVE" -> onReaction("REMOVE", data)
            "INTERACTION_CREATE" -> {
                emitLog("收到 INTERACTION_CREATE")
                dispatchMessageEvent(type, data)
            }
            else -> {
                if (MessageParser.isMessageEvent(type)) {
                    dispatchMessageEvent(type, data)
                } else {
                    emitLog("忽略未处理的 dispatch: " + type)
                }
            }
        }
    }

    /** 用 MessageParser.parseMessage 解析消息类 dispatch 并回调 onEvent。 */
    private fun dispatchMessageEvent(type: String, data: JsonObject) {
        val event = try {
            MessageParser.parseMessage(type, data)
        } catch (e: Exception) {
            emitLog("解析消息异常: " + e.message)
            null
        }
        if (event == null) {
            emitLog("无法解析 dispatch: " + type)
            return
        }
        emitEvent(event)
        emitLog("收到消息事件 " + type + " target=" + event.target + " id=" + event.messageId)
    }

    /** 解析表态事件并回调 onReaction（兼容 target.id 与 message_id 两种字段）。 */
    private fun onReaction(kind: String, data: JsonObject) {
        val channelId = stringOf(data, "channel_id") ?: ""
        val target = objectOf(data, "target")
        val messageId = stringOf(target, "id") ?: stringOf(data, "message_id") ?: ""
        val emoji = objectOf(data, "emoji")
        val emojiId = stringOf(emoji, "id") ?: stringOf(data, "emoji_id") ?: ""
        emitReaction(kind, channelId, messageId, emojiId)
        emitLog("表情事件 " + kind + " channel=" + channelId +
            " message=" + messageId + " emoji=" + emojiId)
    }

    /** 处理 op 9 Invalid Session：清空会话信息后重连（下次将走 Identify）。 */
    private fun onInvalidSession() {
        emitLog("收到 op 9 Invalid Session，清空会话并重新 Identify")
        sessionId = ""
        lastSeq = null
        handleDisconnect("invalid_session")
    }

    /**
     * 统一断线处理：取消定时器、关闭旧连接、按指数退避在后台线程重连。
     *
     * [reason] 仅用于日志；通过 reconnecting 标志避免同一轮断线被多次触发。
     */
    private fun handleDisconnect(reason: String) {
        if (closed) return
        var already = false
        synchronized(lock) {
            if (reconnecting) already = true else reconnecting = true
        }
        if (already) {
            emitLog("已在重连流程中，忽略断线事件: " + reason)
            return
        }
        cancelTimers()
        val current = webSocket
        webSocket = null
        if (current != null) {
            try {
                current.cancel()
            } catch (e: Exception) {
                emitLog("关闭旧连接异常: " + e.message)
            }
        }
        val attempt = reconnectAttempt
        reconnectAttempt = attempt + 1
        val delayMs = backoffSeconds(attempt) * 1000L
        emitState(GatewayStates.RECONNECTING)
        emitLog("断线(" + reason + ")，" + (delayMs / 1000L) +
            "s 后第 " + (attempt + 1) + " 次重连")
        Thread(Runnable {
            try {
                sleeper(delayMs)
            } catch (e: Exception) {
                emitLog("重连等待被中断: " + e.message)
            }
            synchronized(lock) { reconnecting = false }
            if (!closed) connectOnce()
        }, "qqbot-gateway-reconnect").apply {
            isDaemon = true
            start()
        }
    }

    /** 取消心跳与 ACK 超时两个定时任务。 */
    private fun cancelTimers() {
        synchronized(lock) {
            heartbeatFuture?.cancel(false)
            heartbeatFuture = null
            ackTimeoutFuture?.cancel(false)
            ackTimeoutFuture = null
        }
    }

    /** 通过当前 WebSocket 发送一个 JSON 帧；发送失败只记日志不抛出。 */
    private fun send(frame: JsonObject) {
        val current = webSocket
        if (current == null) {
            emitLog("WebSocket 未就绪，丢弃帧 op=" + (longOf(frame, "op") ?: -1))
            return
        }
        try {
            if (!current.send(frame.toString())) emitLog("发送队列已满，帧可能延迟")
        } catch (e: Exception) {
            emitLog("发送帧失败: " + e.message)
        }
    }

    /** 包一层异常隔离，避免回调实现抛异常打断网关线程。 */
    private fun safeNotify(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            System.err.println("[GatewayClient] 回调异常: " + e.message)
        }
    }

    /** 上报消息事件。 */
    private fun emitEvent(event: IncomingEvent) = safeNotify { listener.onEvent(event) }

    /** 上报状态变化。 */
    private fun emitState(state: String) = safeNotify { listener.onState(state) }

    /** 上报日志。 */
    private fun emitLog(line: String) = safeNotify { listener.onLog(line) }

    /** 上报表态事件。 */
    private fun emitReaction(kind: String, channelId: String, messageId: String, emojiId: String) =
        safeNotify { listener.onReaction(kind, channelId, messageId, emojiId) }

    /** 安全读取字符串字段；JsonNull 或非原始值返回 null。 */
    private fun stringOf(obj: JsonObject?, key: String): String? {
        val element = obj?.get(key) ?: return null
        return if (element.isJsonPrimitive) element.asString else null
    }

    /** 安全读取长整型字段；解析失败返回 null。 */
    private fun longOf(obj: JsonObject?, key: String): Long? {
        val element = obj?.get(key) ?: return null
        if (!element.isJsonPrimitive) return null
        return try {
            element.asLong
        } catch (e: Exception) {
            null
        }
    }

    /** 安全读取对象字段；缺失或不是对象时返回空 JsonObject。 */
    private fun objectOf(obj: JsonObject?, key: String): JsonObject {
        val element = obj?.get(key) ?: return JsonObject()
        return if (element.isJsonObject) element.asJsonObject else JsonObject()
    }
}

/** 创建单线程守护调度器，避免阻塞 JVM 退出，便于在单元测试中使用。 */
private fun newDaemonScheduler(): ScheduledExecutorService =
    Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "qqbot-gateway-timer").apply { isDaemon = true }
    }
