
package org.example.qqbotchat.core

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

/** 频道 / 表态 / 历史相关 REST 端点的纯 JVM 契约测试（本地 HttpServer，无真实网络）。 */
class ChannelApiHttpTest {

    private data class Req(val method: String, val path: String, val body: String, val auth: String?)

    private class Fake(private val responder: (Req) -> Pair<Int, String>) {
        val seen = mutableListOf<Req>()
        val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
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

    private val token = """{"access_token":"T","expires_in":"7200"}"""
    private fun api(fake: Fake) = QQBotApi(Credentials("APPID", "SECRET"), baseUrl = fake.url, nowMillis = { 1_000L })
    private fun fake(responder: (Req) -> Pair<Int, String>) = Fake { req ->
        if (req.path == "/app/getAppAccessToken") 200 to token else responder(req)
    }

    @Test fun myGuildsPath() {
        val f = fake { 200 to """[{"id":"G1","name":"我的频道"}]""" }
        try {
            val arr = api(f).getMyGuilds()
            assertEquals(1, arr.size())
            assertEquals("GET", f.seen.last().method)
            assertEquals("/users/@me/guilds", f.seen.last().path)
        } finally { f.stop() }
    }

    @Test fun guildChannelsPath() {
        val f = fake { 200 to """[{"id":"C1"}]""" }
        try {
            assertEquals(1, api(f).getGuildChannels("G1").size())
            assertEquals("/guilds/G1/channels", f.seen.last().path)
        } finally { f.stop() }
    }

    @Test fun pinsListAndAddRemove() {
        val f = fake { 200 to """{"message_ids":[]}""" }
        try {
            val a = api(f)
            a.getChannelPins("C1")
            a.addChannelPin("C1", "M1")
            a.removeChannelPin("C1", "M1")
            val paths = f.seen.map { it.method + " " + it.path }
            assertTrue(paths.any { it == "GET /channels/C1/pins" })
            assertTrue(paths.any { it == "PUT /channels/C1/pins/M1" })
            assertTrue(paths.any { it == "DELETE /channels/C1/pins/M1" })
        } finally { f.stop() }
    }

    @Test fun schedulesAndHistoryPaths() {
        val f = fake { 200 to """[]""" }
        try {
            val a = api(f)
            a.getChannelSchedules("C1")
            a.getChannelMessages("C1", 50)
            val paths = f.seen.map { it.path }
            assertTrue(paths.any { it.startsWith("/channels/C1/schedules") })
            assertTrue(paths.any { it.startsWith("/channels/C1/messages?limit=50") })
        } finally { f.stop() }
    }

    @Test fun reactionPathsUseEmojiTypeAndId() {
        val f = fake { 200 to "{}" }
        try {
            val a = api(f)
            a.addReaction("C1", "M1", "128077", 1)
            a.removeReaction("C1", "M1", "128077", 1)
            val paths = f.seen.map { it.method + " " + it.path }
            assertTrue(paths.any { it == "PUT /channels/C1/messages/M1/reactions/1/128077" })
            assertTrue(paths.any { it == "DELETE /channels/C1/messages/M1/reactions/1/128077" })
        } finally { f.stop() }
    }

    @Test fun channelInfoAndName() {
        val f = fake { req ->
            if (req.path == "/channels/C1") 200 to """{"name":"公告"}""" else 200 to "{}"
        }
        try {
            assertEquals("公告", api(f).getChannelName("C1"))
            assertEquals("/channels/C1", f.seen.last().path)
        } finally { f.stop() }
    }

    @Test fun arrayResponseUnwrapsItemsWrapper() {
        val f = fake { 200 to """{"items":[{"id":"x"}]}""" }
        try {
            val arr = api(f).getMyGuilds()
            assertEquals(1, arr.size())
        } finally { f.stop() }
    }
}
