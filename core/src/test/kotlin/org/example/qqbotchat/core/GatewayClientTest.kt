package org.example.qqbotchat.core

import com.google.gson.JsonParser
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** JVM-only protocol tests: no real network, no Android. */
private fun fakeWs(): WebSocket = Proxy.newProxyInstance(
        WebSocket::class.java.classLoader, arrayOf(WebSocket::class.java)
    ) { _, method, _ ->
        when (method.name) {
            "send" -> true
            "close" -> true
            "cancel" -> null
            "queueSize" -> 0L
            "request" -> null
            "toString" -> "FakeWebSocket"
            "hashCode" -> 0
            "equals" -> false
            else -> null
        }
    } as WebSocket

class GatewayClientTest {
    private class Harness {
        val listenerRef = AtomicReference<WebSocketListener>()
        val ready = CountDownLatch(1)
        val ws = fakeWs()
        val factory = object : WebSocketFactory {
            override fun newWebSocket(request: okhttp3.Request, listener: WebSocketListener): WebSocket {
                listenerRef.set(listener)
                ready.countDown()
                return ws
            }
        }
        val events = mutableListOf<IncomingEvent>()
        val states = mutableListOf<String>()
        val logs = mutableListOf<String>()
        val reactions = mutableListOf<String>()

        fun client(): GatewayClient = GatewayClient(
            tokenProvider = { "TOKEN" },
            gatewayUrlProvider = { "wss://example.invalid/gateway" },
            listener = object : GatewayListener {
                override fun onEvent(event: IncomingEvent) { synchronized(events) { events.add(event) } }
                override fun onState(state: String) { synchronized(states) { states.add(state) } }
                override fun onLog(line: String) { synchronized(logs) { logs.add(line) } }
                override fun onReaction(kind: String, channelId: String, messageId: String, emojiId: String) {
                    synchronized(reactions) { reactions.add("$kind|$channelId|$messageId|$emojiId") }
                }
            },
            webSocketFactory = factory,
            sleeper = { },
            nowMillis = { 1_000L }
        )

        fun await(): WebSocketListener {
            assertTrue("factory was never called", ready.await(5, TimeUnit.SECONDS))
            return listenerRef.get()
        }

        fun feed(json: String) = await().onMessage(ws, json)
    }

    private fun frame(op: Int, d: String = "{}", t: String? = null, s: Int? = null): String {
        val parts = mutableListOf("\"op\":$op", "\"d\":$d")
        if (t != null) parts.add("\"t\":\"$t\"")
        if (s != null) parts.add("\"s\":$s")
        return "{" + parts.joinToString(",") + "}"
    }

    @Test fun connectEmitsConnectingAndOpens() {
        val h = Harness()
        val c = h.client()
        c.connect()
        val l = h.await()
        val response = Response.Builder()
            .request(okhttp3.Request.Builder().url("https://example.invalid").build())
            .protocol(okhttp3.Protocol.HTTP_1_1).code(101).message("Switching Protocols").build()
        l.onOpen(h.ws, response)
        synchronized(h.states) {
            assertTrue(h.states.contains(GatewayStates.CONNECTING))
            assertTrue(h.states.contains(GatewayStates.CONNECTED))
        }
        c.close()
    }

    @Test fun malformedFrameIsIgnored() {
        val h = Harness()
        val c = h.client()
        c.connect(); h.await()
        h.feed("not-json")
        synchronized(h.events) { assertTrue(h.events.isEmpty()) }
        synchronized(h.logs) { assertTrue(h.logs.any { it.contains("无法解析") || it.contains("忽略") }) }
        c.close()
    }

    @Test fun dispatchMessageEventIsParsed() {
        val h = Harness()
        val c = h.client()
        c.connect(); h.await()
        val data = """{"id":"m1","content":"你好","group_openid":"G1","author":{"username":"阿明","member_openid":"MEM"},"timestamp":"2026-11-11T02:00:00Z"}"""
        h.feed(frame(0, data, "GROUP_MESSAGE_CREATE", 7))
        synchronized(h.events) {
            assertEquals(1, h.events.size)
            assertEquals("m1", h.events[0].messageId)
            assertEquals("group", h.events[0].scene)
            assertEquals("你好", h.events[0].message.text)
        }
        c.close()
    }

    @Test fun nonMessageDispatchIsNotEmittedAsEvent() {
        val h = Harness()
        val c = h.client()
        c.connect(); h.await()
        h.feed(frame(0, "{\"session_id\":\"S1\"}", "READY", 1))
        synchronized(h.events) { assertTrue(h.events.isEmpty()) }
        c.close()
    }

    @Test fun reactionEventsAreForwarded() {
        val h = Harness()
        val c = h.client()
        c.connect(); h.await()
        val data = """{"channel_id":"C1","target":{"id":"m1"},"emoji":{"id":"128077"},"user_id":"U1"}"""
        h.feed(frame(0, data, "MESSAGE_REACTION_ADD", 3))
        synchronized(h.reactions) {
            assertEquals(1, h.reactions.size)
            assertTrue(h.reactions[0].startsWith("ADD|C1|m1|128077"))
        }
        c.close()
    }

    @Test fun heartbeatAckIsRecognised() {
        val h = Harness()
        val c = h.client()
        c.connect(); h.await()
        h.feed(frame(11))
        synchronized(h.logs) { assertTrue(h.logs.any { it.contains("ACK") || it.contains("心跳") }) }
        c.close()
    }

    @Test fun unknownOpLogsWarning() {
        val h = Harness()
        val c = h.client()
        c.connect(); h.await()
        h.feed(frame(99))
        synchronized(h.logs) { assertTrue(h.logs.any { it.contains("未处理的 op") }) }
        c.close()
    }

    @Test fun closeIsTerminal() {
        val h = Harness()
        val c = h.client()
        c.connect(); h.await()
        c.close()
        c.connect()
        synchronized(h.logs) { assertTrue(h.logs.any { it.contains("已关闭") }) }
        synchronized(h.states) { assertTrue(h.states.contains(GatewayStates.CLOSED)) }
    }
}
