package org.example.qqbotchat.core

import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

/**
 * HTTP contract tests for QQBotApi: a real local HttpServer records every
 * request (method, path, headers, body) and returns canned JSON. No real QQ
 * API, no network egress, no Android. Runs entirely on the build server JVM.
 */
class QQBotApiHttpTest {

    private data class Req(val method: String, val path: String, val body: String, val auth: String?)

    private class Fake(private val responder: (Req) -> Pair<Int, String>) {
        val seen = mutableListOf<Req>()
        val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var status = 200
        init {
            server.createContext("/") { ex -> handle(ex) }
            server.executor = Executors.newSingleThreadExecutor()
            server.start()
        }
        val url: String get() = "http://127.0.0.1:" + server.address.port
        private fun handle(ex: HttpExchange) {
            val body = String(ex.requestBody.readBytes(), StandardCharsets.UTF_8)
            val req = Req(ex.requestMethod, ex.requestURI.toString(), body,
                ex.requestHeaders.getFirst("Authorization"))
            synchronized(seen) { seen.add(req) }
            val (code, response) = try { responder(req) } catch (e: Exception) { 500 to "{}" }
            val bytes = response.toByteArray(StandardCharsets.UTF_8)
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(code, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        fun stop() = server.stop(0)
    }

    private fun api(fake: Fake, now: () -> Long = { 1_000L }) =
        QQBotApi(Credentials("APPID", "SECRET"), baseUrl = fake.url, nowMillis = now)

    private fun body(req: Req) = JsonParser.parseString(req.body).asJsonObject

    @Test fun tokenRequestUsesNoAuthAndCaches() {
        val fake = Fake { req ->
            if (req.path == "/app/getAppAccessToken")
                200 to """{"access_token":"T1","expires_in":"7200"}"""
            else 200 to "{}"
        }
        try {
            val a = api(fake)
            val r = a.getAppAccessToken()
            assertEquals("T1", r.get("access_token").asString)
            assertEquals("T1", a.accessToken())
            val tokenReq = fake.seen.first { it.path == "/app/getAppAccessToken" }
            assertEquals("POST", tokenReq.method)
            assertNull(tokenReq.auth)
            assertEquals("APPID", body(tokenReq).get("appId").asString)
            assertEquals("SECRET", body(tokenReq).get("clientSecret").asString)
            // cached: second call must not re-request token
            a.accessToken()
            assertEquals(1, fake.seen.count { it.path == "/app/getAppAccessToken" })
        } finally { fake.stop() }
    }

    @Test fun tokenRefreshWhenExpired() {
        var now = 1_000L
        val fake = Fake { req ->
            if (req.path == "/app/getAppAccessToken")
                200 to """{"access_token":"T","expires_in":"7200"}"""
            else 200 to "{}"
        }
        try {
            val a = api(fake) { now }
            a.accessToken()
            assertEquals(1, fake.seen.count { it.path == "/app/getAppAccessToken" })
            // advance past the cached expiry (7200s) minus the 60s skew
            now += 7_300_000L
            a.accessToken()
            assertEquals(2, fake.seen.count { it.path == "/app/getAppAccessToken" })
        } finally { fake.stop() }
    }

    @Test fun gatewayUrlIsReadFromResponse() {
        val fake = Fake { req ->
            when {
                req.path == "/app/getAppAccessToken" -> 200 to """{"access_token":"T","expires_in":"7200"}"""
                req.path == "/gateway/bot" -> 200 to """{"url":"wss://gw.example/ws"}"""
                else -> 200 to "{}"
            }
        }
        try {
            assertEquals("wss://gw.example/ws", api(fake).getGatewayUrl())
            val g = fake.seen.first { it.path == "/gateway/bot" }
            assertEquals("GET", g.method)
            assertEquals("QQBot T", g.auth)
        } finally { fake.stop() }
    }

    @Test fun sendC2cIncludesPassiveReplyFields() {
        val fake = Fake { req ->
            if (req.path == "/app/getAppAccessToken") 200 to """{"access_token":"T","expires_in":"7200"}"""
            else 200 to """{"id":"sent-1"}"""
        }
        try {
            api(fake).sendMessage("c2c", "U1", "你好", replyId = "m-in", replySeq = 3)
            val r = fake.seen.last()
            assertEquals("POST", r.method)
            assertEquals("/v2/users/U1/messages", r.path)
            val b = body(r)
            assertEquals("m-in", b.get("msg_id").asString)
            assertEquals(4, b.get("msg_seq").asInt)
            assertEquals(0, b.get("msg_type").asInt)
            assertEquals("你好", b.get("content").asString)
            assertEquals("QQBot T", r.auth)
        } finally { fake.stop() }
    }

    @Test fun sendGroupUsesGroupPath() {
        val fake = Fake { req ->
            if (req.path == "/app/getAppAccessToken") 200 to """{"access_token":"T","expires_in":"7200"}"""
            else 200 to "{}"
        }
        try {
            api(fake).sendMessage("group", "G1", "hi", replyId = "x", replySeq = 1)
            assertEquals("/v2/groups/G1/messages", fake.seen.last().path)
        } finally { fake.stop() }
    }

    @Test fun sendChannelOmitsPassiveReplyFields() {
        val fake = Fake { req ->
            if (req.path == "/app/getAppAccessToken") 200 to """{"access_token":"T","expires_in":"7200"}"""
            else 200 to "{}"
        }
        try {
            api(fake).sendMessage("channel", "C1", "hi", replyId = "x", replySeq = 9)
            val r = fake.seen.last()
            assertEquals("/channels/C1/messages", r.path)
            val b = body(r)
            assertFalse(b.has("msg_id"))
            assertFalse(b.has("msg_seq"))
        } finally { fake.stop() }
    }

    @Test fun quoteAddsMessageReference() {
        val fake = Fake { req ->
            if (req.path == "/app/getAppAccessToken") 200 to """{"access_token":"T","expires_in":"7200"}"""
            else 200 to "{}"
        }
        try {
            api(fake).sendMessage("group", "G1", "hi", replyId = "x", replySeq = 1, quoteId = "old-9")
            val b = body(fake.seen.last())
            assertEquals("old-9", b.getAsJsonObject("message_reference").get("message_id").asString)
        } finally { fake.stop() }
    }

    @Test fun mediaSendUsesMsgType7AndFileInfo() {
        val fake = Fake { req ->
            if (req.path == "/app/getAppAccessToken") 200 to """{"access_token":"T","expires_in":"7200"}"""
            else 200 to "{}"
        }
        try {
            api(fake).sendMessage("c2c", "U1", "", replyId = "x", replySeq = 1,
                mediaFileInfo = "FILE-UUID")
            val b = body(fake.seen.last())
            assertEquals(7, b.get("msg_type").asInt)
            assertEquals("FILE-UUID", b.getAsJsonObject("media").get("file_info").asString)
        } finally { fake.stop() }
    }

    @Test fun recallPathsCoverFourScenes() {
        val fake = Fake { req ->
            if (req.path == "/app/getAppAccessToken") 200 to """{"access_token":"T","expires_in":"7200"}"""
            else 200 to "{}"
        }
        try {
            val a = api(fake)
            a.recallMessage("c2c", "U1", "M1")
            a.recallMessage("group", "G1", "M2")
            a.recallMessage("channel", "C1", "M3")
            val paths = fake.seen.filter { it.method == "DELETE" }.map { it.path }
            assertTrue(paths.any { it == "/v2/users/U1/messages/M1" })
            assertTrue(paths.any { it == "/v2/groups/G1/messages/M2" })
            assertTrue(paths.any { it.startsWith("/channels/C1/messages/M3") && it.contains("hidetip=false") })
        } finally { fake.stop() }
    }

    @Test fun groupAndChannelNamesAreParsed() {
        val fake = Fake { req ->
            when {
                req.path == "/app/getAppAccessToken" -> 200 to """{"access_token":"T","expires_in":"7200"}"""
                req.path == "/v2/groups/G1/info" -> 200 to """{"group_name":"我的群"}"""
                req.path == "/channels/C1" -> 200 to """{"name":"公告频道"}"""
                else -> 200 to "{}"
            }
        }
        try {
            val a = api(fake)
            assertEquals("我的群", a.getGroupName("G1"))
            assertEquals("公告频道", a.getChannelName("C1"))
        } finally { fake.stop() }
    }

    @Test fun non2xxThrowsApiException() {
        val fake = Fake { req ->
            if (req.path == "/app/getAppAccessToken") 200 to """{"access_token":"T","expires_in":"7200"}"""
            else 403 to """{"message":"forbidden"}"""
        }
        try {
            assertThrows(QQBotApiException::class.java) { api(fake).sendMessage("c2c", "U1", "hi", replyId = "x", replySeq = 1) }
        } finally { fake.stop() }
    }

    @Test fun uploadMediaSendsBase64AndFileType() {
        val fake = Fake { req ->
            if (req.path == "/app/getAppAccessToken") 200 to """{"access_token":"T","expires_in":"7200"}"""
            else 200 to """{"file_uuid":"F1","file_info":"F1","ttl":100}"""
        }
        try {
            val a = api(fake)
            val info = a.uploadMedia("c2c", "U1", 1, "a.jpg", "hello".toByteArray())
            assertNotNull(info)
            val r = fake.seen.last()
            assertTrue(r.path.endsWith("/files"))
            val b = body(r)
            assertEquals(1, b.get("file_type").asInt)
            assertTrue(b.get("file_data").asString.isNotEmpty())
        } finally { fake.stop() }
    }
}
