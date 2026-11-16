package org.example.qqbotchat.core

import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.Executors

class MessageDeliveryTest {
    private val store = ConversationStore()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val executor = Executors.newSingleThreadExecutor()
    private val requests = mutableListOf<Pair<String, String>>()
    private var failSend = false
    private var failRecall = false
    private lateinit var delivery: MessageDelivery

    @Before fun setUp() {
        server.executor = executor
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            val body = exchange.requestBody.bufferedReader().readText()
            synchronized(requests) { requests += path to body }
            val code = if ((exchange.requestMethod == "DELETE" && failRecall) ||
                (path.endsWith("/messages") && failSend)) 403 else 200
            val response = if (path.endsWith("getAppAccessToken"))
                """{"access_token":"test","expires_in":7200}""" else """{"id":"server-message","message":"denied"}"""
            val bytes = response.toByteArray()
            exchange.sendResponseHeaders(code, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        delivery = MessageDelivery(store, QQBotApi(Credentials("test", "test"),
            baseUrl = "http://127.0.0.1:" + server.address.port))
        store.append("target", "group", ChatMessage("incoming", "hello", "", false, "Alice"))
        store.recordOutgoing("target", "group", ChatMessage("local", "reply", "", true, "Me", status = "sending"))
    }

    @After fun tearDown() { server.stop(0); executor.shutdownNow() }

    @Test fun sentMessageRetainsLocalKeyAndStoresRemoteIdForRecall() {
        delivery.send("target", "local")
        val message = store.allMessages("target").last()
        assertEquals("local", message.id)
        assertEquals("server-message", message.serverId)
        assertEquals("sent", message.status)
        delivery.recall("target", "local")
        assertTrue(requests.last().first.endsWith("/messages/server-message"))
        assertEquals("recalled", store.allMessages("target").last().status)
    }

    @Test fun failedRecallKeepsOriginalMessageVisible() {
        delivery.send("target", "local")
        failRecall = true
        assertThrows(QQBotApiException::class.java) { delivery.recall("target", "local") }
        assertEquals("sent", store.allMessages("target").last().status)
        assertEquals("reply", store.allMessages("target").last().text)
    }

    @Test fun failedSendCanRetryWithoutDuplicatingLocalMessage() {
        failSend = true
        assertThrows(QQBotApiException::class.java) { delivery.send("target", "local") }
        assertEquals("failed", store.allMessages("target").last().status)
        failSend = false
        delivery.send("target", "local")
        assertEquals(2, store.allMessages("target").size)
        val sentBodies = requests.filter { it.first.endsWith("/messages") }.map { JsonParser.parseString(it.second).asJsonObject }
        assertEquals(sentBodies[0].get("msg_seq").asInt + 1, sentBodies[1].get("msg_seq").asInt)
    }

    @Test fun replyUsesRemoteIdWithoutLeakingLocalUuid() {
        delivery.send("target", "local")
        store.recordOutgoing("target", "group", ChatMessage("second", "quoted", "", true, "Me", quotedId = "local"))
        delivery.send("target", "second")
        val sent = JsonParser.parseString(requests.last().second).asJsonObject
        assertEquals("server-message", sent.getAsJsonObject("message_reference").get("message_id").asString)
    }

    @Test fun passiveSendWithoutIncomingWindowFailsBeforeNetwork() {
        store.recordOutgoing("new", "c2c", ChatMessage("new-local", "hello", "", true, "Me"))
        assertThrows(IllegalArgumentException::class.java) { delivery.send("new", "new-local") }
        assertEquals("failed", store.allMessages("new").single().status)
        assertTrue(requests.isEmpty())
    }
}
