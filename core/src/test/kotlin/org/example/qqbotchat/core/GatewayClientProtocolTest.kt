package org.example.qqbotchat.core

/**
 * 纯 JVM 协议状态机测试，无 Android / 无真实网络。
 *
 * 通过 java.lang.reflect.Proxy 伪造 okhttp3.WebSocket 与 WebSocketFactory，
 * 捕获 WebSocketListener 后直接喂 JSON 帧，验证 GatewayClient 的协议行为，
 * 并用假的 send(String) 记录发出的帧。补充覆盖 GatewayClientTest 未涉及的：
 * Hello/Identify、Resume、服务端心跳、ACK 不重连、Reconnect、Invalid Session、
 * INTERACTION_CREATE、序号推进、未处理 dispatch 以及状态常量。
 */

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * 可记录发送文本的假 WebSocket。send(String) 把文本帧记入 [sent]；
 * send(ByteString) 直接返回 true；close/cancel/queueSize/request 给出合理返回值。
 */
private fun protocolFakeWs(sent: MutableList<String>): WebSocket = Proxy.newProxyInstance(
        WebSocket::class.java.classLoader, arrayOf(WebSocket::class.java)
    ) { _, method, args ->
        when (method.name) {
            "send" -> {
                // 仅记录文本帧，二进制帧直接返回成功
                val text = args?.firstOrNull() as? String
                if (text != null) {
                    val frameText: String = text
                    synchronized(sent) { sent.add(frameText) }
                }
                true
            }
            "close" -> true
            "cancel" -> null
            "queueSize" -> 0L
            "request" -> null
            "toString" -> "ProtocolFakeWebSocket"
            "hashCode" -> 0
            "equals" -> false
            else -> null
        }
    } as WebSocket

/** 构造 WebSocket onOpen 所需的 101 Response。 */
private fun protocolOpenResponse(): Response = Response.Builder()
    .request(Request.Builder().url("https://example.invalid").build())
    .protocol(Protocol.HTTP_1_1)
    .code(101)
    .message("Switching Protocols")
    .build()

/** 纯 JVM 协议状态机测试：假 WebSocket + 直接喂帧，不触网、不等真实心跳/退避。 */
class GatewayClientProtocolTest {

    /** 测试夹具：捕获 listener、记录回调与发出的帧。 */
    private class Harness {
        val listenerRef = AtomicReference<WebSocketListener>()
        val ready = CountDownLatch(1)
        // 重连线程再次调用 factory 时计数并放行，用来验证 sleeper 注入后不会真的 sleep
        val reconnected = CountDownLatch(1)
        val factoryCalls = AtomicInteger(0)
        val sent = mutableListOf<String>()
        val ws = protocolFakeWs(sent)
        val factory = object : WebSocketFactory {
            override fun newWebSocket(request: Request, listener: WebSocketListener): WebSocket {
                listenerRef.set(listener)
                ready.countDown()
                if (factoryCalls.incrementAndGet() >= 2) reconnected.countDown()
                return ws
            }
        }
        val events = mutableListOf<IncomingEvent>()
        val states = mutableListOf<String>()
        val logs = mutableListOf<String>()

        fun client(): GatewayClient = GatewayClient(
            tokenProvider = { "TOKEN" },
            gatewayUrlProvider = { "wss://example.invalid/gateway" },
            listener = object : GatewayListener {
                override fun onEvent(event: IncomingEvent) { synchronized(events) { events.add(event) } }
                override fun onState(state: String) { synchronized(states) { states.add(state) } }
                override fun onLog(line: String) { synchronized(logs) { logs.add(line) } }
            },
            webSocketFactory = factory,
            sleeper = { },            // 重连退避不真的 sleep
            nowMillis = { 1_000L }    // 固定时间，避免时序相关
        )

        fun await(): WebSocketListener {
            assertTrue("factory 从未被调用", ready.await(5, TimeUnit.SECONDS))
            return listenerRef.get()
        }

        /** 触发 onOpen：设置内部 webSocket 并上报 connected 状态。 */
        fun open() = await().onOpen(ws, protocolOpenResponse())

        /** 启动连接，等待 factory 调用并完成 onOpen。 */
        fun start(c: GatewayClient) {
            c.connect()
            await()
            open()
        }

        /** 直接喂一帧 JSON 文本给状态机。 */
        fun feed(json: String) = await().onMessage(ws, json)

        /** 已发出的文本帧解析成 JSON 对象列表。 */
        fun sentFrames(): List<JsonObject> = synchronized(sent) {
            sent.map { JsonParser.parseString(it).asJsonObject }
        }

        fun clearSent() = synchronized(sent) { sent.clear() }
    }

    /** 组装网关帧；d 为原始 JSON 文本。 */
    private fun protocolFrame(op: Int, d: String = "{}", t: String? = null, s: Int? = null): String {
        val parts = mutableListOf("\"op\":$op", "\"d\":$d")
        if (t != null) parts.add("\"t\":\"$t\"")
        if (s != null) parts.add("\"s\":$s")
        return "{" + parts.joinToString(",") + "}"
    }

    // 1. op 10 Hello：进入鉴权状态并发送 op 2 Identify（含 token / intents / shard）。
    @Test fun helloSendsIdentifyFrame() {
        val h = Harness()
        val c = h.client()
        try {
            h.start(c)
            h.feed(protocolFrame(10, """{"heartbeat_interval":30000}"""))

            synchronized(h.states) {
                assertTrue("应进入 connected 状态", h.states.contains(GatewayStates.CONNECTED))
                assertTrue("Hello 后应进入 identifying 状态", h.states.contains(GatewayStates.IDENTIFYING))
            }
            val identify = h.sentFrames().firstOrNull { it.get("op")?.asInt == 2 }
            assertNotNull("未发送 op 2 Identify 帧", identify)
            val d = identify!!.getAsJsonObject("d")
            assertEquals("QQBot TOKEN", d.get("token")?.asString)
            assertNotNull("Identify 缺少 intents", d.get("intents"))
            assertEquals(1174406144L, d.get("intents")!!.asLong)
            val shard = d.getAsJsonArray("shard")
            assertNotNull("Identify 缺少 shard", shard)
            assertEquals(2, shard!!.size())
            assertEquals(0, shard[0].asInt)
            assertEquals(1, shard[1].asInt)
            synchronized(h.logs) {
                assertTrue(h.logs.any { it.contains("Identify") || it.contains("Hello") })
            }
        } finally {
            c.close()
        }
    }

    // 2. 已有 session_id（READY 设置）时，Hello 改发 op 6 Resume 而非 Identify。
    @Test fun helloWithSessionSendsResumeFrame() {
        val h = Harness()
        val c = h.client()
        try {
            h.start(c)
            // 带 s 的 READY 同时设置 session_id 与 lastSeq，使 canResume() 为真
            h.feed(protocolFrame(0, """{"session_id":"S1"}""", "READY", 1))
            h.feed(protocolFrame(10, """{"heartbeat_interval":30000}"""))

            val resume = h.sentFrames().firstOrNull { it.get("op")?.asInt == 6 }
            assertNotNull("已有 session 时应改发 op 6 Resume", resume)
            val d = resume!!.getAsJsonObject("d")
            assertEquals("QQBot TOKEN", d.get("token")?.asString)
            assertEquals("S1", d.get("session_id")?.asString)
            assertEquals(1L, d.get("seq")?.asLong)
            assertTrue("存在 session 时不应发送 Identify",
                h.sentFrames().none { it.get("op")?.asInt == 2 })
            synchronized(h.states) {
                assertTrue(h.states.contains(GatewayStates.RESUMING))
            }
            synchronized(h.logs) {
                assertTrue(h.logs.any { it.contains("Resume") })
            }
        } finally {
            c.close()
        }
    }

    // 3. 服务端 op 1 要求立即心跳 → 发出 op 1 帧，d 为最近收到的 s。
    @Test fun serverHeartbeatRequestSendsOp1WithLastSeq() {
        val h = Harness()
        val c = h.client()
        try {
            h.start(c)
            // 一条带 s 的 dispatch 把 lastSeq 更新为 42
            h.feed(protocolFrame(0, "{}", "TYPING_EVENT", 42))
            h.feed(protocolFrame(1))

            val hb = h.sentFrames().firstOrNull { it.get("op")?.asInt == 1 }
            assertNotNull("未发出 op 1 心跳帧", hb)
            assertEquals(42L, hb!!.get("d")?.asLong)
            synchronized(h.logs) {
                assertTrue(h.logs.any { it.contains("心跳") || it.contains("op 1") })
            }
        } finally {
            c.close()
        }
    }

    // 4. op 11 ACK：记录 ACK/心跳日志且不触发重连。
    @Test fun heartbeatAckLogsAndDoesNotReconnect() {
        val h = Harness()
        val c = h.client()
        try {
            h.start(c)
            h.feed(protocolFrame(11))

            synchronized(h.logs) {
                assertTrue(h.logs.any { it.contains("ACK") || it.contains("心跳") })
                assertFalse("ACK 不应产生断线日志", h.logs.any { it.contains("断线(") })
            }
            synchronized(h.states) {
                assertFalse("ACK 不应触发重连", h.states.contains(GatewayStates.RECONNECTING))
            }
        } finally {
            c.close()
        }
    }

    // 5. op 7 Reconnect：进入 handleDisconnect，日志含「重连」，sleeper 注入后迅速重连。
    @Test fun op7ReconnectTriggersDisconnectWithoutRealSleep() {
        val h = Harness()
        val c = h.client()
        try {
            h.start(c)
            h.feed(protocolFrame(7))

            synchronized(h.logs) {
                assertTrue(h.logs.any { it.contains("重连") })
            }
            synchronized(h.states) {
                assertTrue(h.states.contains(GatewayStates.RECONNECTING))
            }
            // sleeper = {} 时后台重连线程应立即再次调用 factory（不真的 sleep 1s）
            assertTrue("注入 sleeper 后应迅速重连",
                h.reconnected.await(3, TimeUnit.SECONDS))
            assertTrue("factory 应被再次调用", h.factoryCalls.get() >= 2)
        } finally {
            c.close()
        }
    }

    // 6. op 9 Invalid Session：清空会话，后续 Hello 重新走 Identify（而非 Resume）。
    @Test fun op9InvalidSessionClearsSessionAndReidentifies() {
        val h = Harness()
        val c = h.client()
        try {
            h.start(c)
            // 先建立可续传会话（session_id + lastSeq）
            h.feed(protocolFrame(0, """{"session_id":"S1"}""", "READY", 5))
            h.feed(protocolFrame(9))

            synchronized(h.logs) {
                assertTrue(h.logs.any { it.contains("Invalid Session") })
            }
            // 断线会置空 webSocket，重新 onOpen 模拟新连接，再喂 Hello 应重新 Identify
            h.open()
            h.clearSent()
            h.feed(protocolFrame(10, """{"heartbeat_interval":30000}"""))

            val identify = h.sentFrames().firstOrNull { it.get("op")?.asInt == 2 }
            assertNotNull("会话清空后重新 Hello 应发送 op 2 Identify", identify)
            assertEquals("QQBot TOKEN", identify!!.getAsJsonObject("d").get("token")?.asString)
            // 会话已清空，绝不应再发 Resume
            assertTrue("会话清空后不应再发送 Resume",
                h.sentFrames().none { it.get("op")?.asInt == 6 })
            synchronized(h.states) {
                assertTrue(h.states.contains(GatewayStates.IDENTIFYING))
            }
        } finally {
            c.close()
        }
    }

    // 7. op 0 dispatch 的 INTERACTION_CREATE → 日志含「INTERACTION_CREATE」。
    @Test fun interactionCreateIsLogged() {
        val h = Harness()
        val c = h.client()
        try {
            h.start(c)
            val data = """{"id":"I1","author":{"user_openid":"U1"},"content":"hi"}"""
            h.feed(protocolFrame(0, data, "INTERACTION_CREATE", 9))
            synchronized(h.logs) {
                assertTrue(h.logs.any { it.contains("INTERACTION_CREATE") })
            }
        } finally {
            c.close()
        }
    }

    // 8. 连续两条消息事件带 s → lastSeq 推进（用后续 op 1 心跳的 d 间接验证）。
    @Test fun consecutiveMessageEventsAdvanceLastSeq() {
        val h = Harness()
        val c = h.client()
        try {
            h.start(c)
            val d1 = """{"id":"m1","content":"第一条","group_openid":"G1","author":{"username":"u","member_openid":"MEM"},"timestamp":"2026-11-11T02:00:00Z"}"""
            val d2 = """{"id":"m2","content":"第二条","group_openid":"G1","author":{"username":"u","member_openid":"MEM"},"timestamp":"2026-11-11T02:00:01Z"}"""
            h.feed(protocolFrame(0, d1, "GROUP_MESSAGE_CREATE", 10))
            h.feed(protocolFrame(0, d2, "GROUP_MESSAGE_CREATE", 20))
            synchronized(h.events) {
                assertEquals("两条消息都应解析为事件", 2, h.events.size)
            }
            h.feed(protocolFrame(1))
            val hb = h.sentFrames().firstOrNull { it.get("op")?.asInt == 1 }
            assertNotNull("未发出 op 1 心跳", hb)
            assertEquals("心跳 d 应为最近一次 s", 20L, hb!!.get("d")?.asLong)
        } finally {
            c.close()
        }
    }

    // 9. 未处理的 dispatch 类型（TYPING_EVENT）→ 日志含「忽略未处理的 dispatch」。
    @Test fun unhandledDispatchIsIgnored() {
        val h = Harness()
        val c = h.client()
        try {
            h.start(c)
            h.feed(protocolFrame(0, "{}", "TYPING_EVENT", 3))
            synchronized(h.logs) {
                assertTrue(h.logs.any { it.contains("忽略未处理的 dispatch") })
            }
        } finally {
            c.close()
        }
    }

    // 10. GatewayStates 常量齐备且非空、互不相同。
    @Test fun gatewayStatesConstantsAreNonEmpty() {
        val all = listOf(
            GatewayStates.READY, GatewayStates.RESUMED, GatewayStates.CONNECTING,
            GatewayStates.CONNECTED, GatewayStates.IDENTIFYING, GatewayStates.RESUMING,
            GatewayStates.RECONNECTING, GatewayStates.CLOSED
        )
        all.forEach { assertFalse("状态常量不应为空", it.isBlank()) }
        assertEquals("状态常量应互不相同", all.size, all.toSet().size)
    }
}
