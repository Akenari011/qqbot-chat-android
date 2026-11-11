package org.example.qqbotchat.core

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64

/**
 * QQ Bot 开放平台 REST 客户端。
 *
 * 纯 Kotlin / JVM 实现，不引用任何 Android API，可直接在服务器 JVM 上做单元测试。
 * 依赖仅 OkHttp 4.12.0 与 Gson 2.11.0。
 *
 * 设计要点：
 * - 所有端点方法成功时返回 [JsonObject] 或 [JsonArray]，失败时抛出 [QQBotApiException]；
 * - [request] 会在每次调用前通过 [ensureToken] 校验 access_token，过期自动刷新；
 *   若服务端返回 401，则强制刷新一次 token 并重试，仍失败则抛出异常；
 * - [getGatewayUrl]、[getGroupName]、[getChannelName]、[uploadMediaFileInfo]、[accessToken]
 *   是基于上述 JSON 结果的便捷取值器（返回标量），其失败路径同样抛出异常；
 * - [client]、[baseUrl]、[gson]、[nowMillis] 均可注入，便于用本地 HTTP 服务做 JVM 单测。
 *
 * 调用方负责 [client] 的生命周期；本类不会主动关闭外部注入的 OkHttpClient。
 */
class QQBotApi(
    private val credentials: Credentials,
    private val client: OkHttpClient = OkHttpClient(),
    private val baseUrl: String = BASE,
    private val gson: Gson = Gson(),
    private val nowMillis: () -> Long = { System.currentTimeMillis() }
) {
    companion object {
        /** QQ Bot API 根地址。 */
        const val BASE = "https://api.bot.qq.com"

        /** 请求体使用的 JSON 媒体类型。 */
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /** token 过期前的提前刷新余量（毫秒），避免请求落在临界过期点上。 */
        private const val TOKEN_SKEW_MS = 60_000L

        /** 接口未返回 expires_in 时采用的默认有效期（秒）。 */
        private const val DEFAULT_EXPIRES_IN = 7200L
    }

    @Volatile
    private var cachedToken: String = ""

    @Volatile
    private var tokenExpiresAt: Long = 0L

    /**
     * 获取应用 access token（该端点无需鉴权）。
     *
     * 请求体为 {appId, clientSecret}，返回官方原始响应对象（含 access_token / expires_in）。
     * 同时把结果写入内部 token 缓存，后续 [request] 无需再次取 token。
     */
    fun getAppAccessToken(): JsonObject {
        val result = fetchToken()
        cacheToken(result)
        return result
    }

    /**
     * 返回当前有效的 access_token（不含 "QQBot " 前缀），必要时自动刷新。
     *
     * 供 [GatewayClient] 构造 Identify / Resume 帧使用。
     */
    fun accessToken(): String {
        ensureToken()
        return cachedToken
    }

    /**
     * 通用 REST 请求，返回 [JsonObject]。
     *
     * 自动携带 Authorization: QQBot &lt;token&gt;，并在 token 过期时自动刷新。
     * 若响应是数组，会被包装成 {"items": [...]}；若响应为空则返回空对象。
     *
     * @param method HTTP 方法，如 GET / POST / PUT / DELETE / PATCH。
     * @param path 以 "/" 开头的路径，可带 query。
     * @param body 可选 JSON 请求体；GET / DELETE 通常传 null。
     * @throws QQBotApiException 网络失败、非 2xx 响应、响应非法 JSON 或 401 重试仍失败。
     */
    fun request(method: String, path: String, body: JsonObject? = null): JsonObject =
        parseObject(perform(method, path, body, auth = true))

    /**
     * 通用 REST 请求，返回 [JsonArray]。
     *
     * 与 [request] 相同的鉴权与 token 刷新语义；对官方常见的 {"items":[...]}、
     * {"messages":[...]} 等包装对象会自动解包为数组。
     */
    fun requestArray(method: String, path: String, body: JsonObject? = null): JsonArray {
        val element = parseElement(perform(method, path, body, auth = true))
        return when (element) {
            null -> JsonArray()
            is JsonArray -> element
            is JsonObject -> arrOf(element, "items")
                ?: arrOf(element, "messages")
                ?: arrOf(element, "data")
                ?: arrOf(element, "guilds")
                ?: arrOf(element, "channels")
                ?: JsonArray()
            else -> JsonArray()
        }
    }

    /**
     * 获取 WebSocket 网关地址（GET /gateway/bot），带 Authorization 头。
     *
     * 返回官方原始响应对象；通常含 url 字段。
     */
    fun getGateway(): JsonObject = request("GET", "/gateway/bot")

    /**
     * 获取可直接用于 OkHttp WebSocket 连接的网关 url。
     *
     * 兼容官方返回 {"url": "..."} 与 [{"url": "...", ...}] 两种形态。
     * @throws QQBotApiException 请求失败或响应中找不到 url。
     */
    fun getGatewayUrl(): String {
        val result = getGateway()
        val direct = stringOf(result, "url")
        if (!direct.isNullOrEmpty()) return direct
        val items = arrOf(result, "items") ?: JsonArray()
        for (element in items) {
            if (element.isJsonObject) {
                val url = stringOf(element.asJsonObject, "url")
                if (!url.isNullOrEmpty()) return url
            }
        }
        throw QQBotApiException("/gateway/bot 响应缺少 url 字段", -1, result.toString())
    }

    /**
     * 发送消息。
     *
     * - c2c   -> POST /v2/users/{id}/messages
     * - group -> POST /v2/groups/{id}/messages
     * - channel -> POST /channels/{id}/messages
     *
     * 请求体规则：
     * - 默认 msg_type=0（文本）+ content；
     * - 单聊 / 群聊必须携带 msg_id（被动回复的消息 id）与 msg_seq（replySeq+1）；
     * - quoteId 非空时携带 message_reference{message_id}；
     * - mediaFileInfo 非空时改为 msg_type=7 + media{file_info}；
     * - buttons 非空时附加 keyboard（每个标签一行一个按钮）。
     *
     * @param scene c2c / group / channel。
     * @param targetId 用户 openid、群 openid 或子频道 id。
     * @param text 文本内容。
     * @param replyId 被动回复的原消息 id，对应 Conversation.replyId。
     * @param replySeq 当前已用的消息序号，实际发送值为 replySeq+1。
     * @param quoteId 引用消息 id，为空则不引用。
     * @param mediaFileInfo [uploadMedia] 返回的 file_info，为空则发文本。
     * @param buttons 键盘按钮文案列表。
     * @throws IllegalArgumentException scene 不受支持。
     * @throws QQBotApiException 请求失败。
     */
    fun sendMessage(
        scene: String,
        targetId: String,
        text: String,
        replyId: String = "",
        replySeq: Int = 0,
        quoteId: String? = null,
        mediaFileInfo: String? = null,
        buttons: List<String> = emptyList()
    ): JsonObject {
        val path = when (scene) {
            "c2c" -> "/v2/users/" + targetId + "/messages"
            "group" -> "/v2/groups/" + targetId + "/messages"
            "channel" -> "/channels/" + targetId + "/messages"
            else -> throw IllegalArgumentException("不支持的消息场景: " + scene)
        }
        val body = JsonObject().apply {
            addProperty("content", text)
            if (!mediaFileInfo.isNullOrEmpty()) {
                addProperty("msg_type", 7)
                add("media", JsonObject().apply { addProperty("file_info", mediaFileInfo) })
            } else {
                addProperty("msg_type", 0)
            }
            if (scene == "c2c" || scene == "group") {
                addProperty("msg_id", replyId)
                addProperty("msg_seq", replySeq + 1)
            }
            if (!quoteId.isNullOrEmpty()) {
                add("message_reference", JsonObject().apply { addProperty("message_id", quoteId) })
            }
            if (buttons.isNotEmpty()) {
                add("keyboard", buildKeyboard(buttons))
            }
        }
        return request("POST", path, body)
    }

    /**
     * 上传富媒体文件并返回官方原始响应（含 file_info）。
     *
     * - c2c / group -> POST /v2/{users|groups}/{id}/files
     * - channel -> POST /channels/{id}/files
     *
     * file_type：1 图片、2 视频、3 音频、4 文件；file_data 为 bytes 的 base64 编码。
     * 附加 srv_send_msg=false，表示只上传不直接发送，由 [sendMessage] 复用 file_info。
     */
    fun uploadMedia(
        scene: String,
        targetId: String,
        fileType: Int,
        fileName: String,
        bytes: ByteArray
    ): JsonObject {
        val path = when (scene) {
            "c2c" -> "/v2/users/" + targetId + "/files"
            "group" -> "/v2/groups/" + targetId + "/files"
            "channel" -> "/channels/" + targetId + "/files"
            else -> throw IllegalArgumentException("不支持的上传场景: " + scene)
        }
        val body = JsonObject().apply {
            addProperty("file_type", fileType)
            addProperty("file_name", fileName)
            addProperty("file_data", Base64.getEncoder().encodeToString(bytes))
            addProperty("srv_send_msg", false)
        }
        return request("POST", path, body)
    }

    /**
     * 上传富媒体并直接取出 file_info 字符串，便于传给 [sendMessage]。
     *
     * @throws QQBotApiException 上传失败或响应缺少 file_info。
     */
    fun uploadMediaFileInfo(
        scene: String,
        targetId: String,
        fileType: Int,
        fileName: String,
        bytes: ByteArray
    ): String {
        val result = uploadMedia(scene, targetId, fileType, fileName, bytes)
        return stringOf(result, "file_info")
            ?: throw QQBotApiException("上传成功但响应缺少 file_info", -1, result.toString())
    }

    /**
     * 撤回消息（DELETE）。
     *
     * - c2c -> /v2/users/{id}/messages/{mid}
     * - group -> /v2/groups/{id}/messages/{mid}
     * - channel -> /channels/{id}/messages/{mid}?hidetip=false
     * - dms -> /dms/{guild}/messages/{mid}?hidetip=false
     *
     * @param scene c2c / group / channel / dms。
     * @param targetId 用户 openid、群 openid、子频道 id 或频道 guild id（dms）。
     * @param messageId 待撤回的消息 id。
     */
    fun recallMessage(scene: String, targetId: String, messageId: String): JsonObject {
        val path = when (scene) {
            "c2c" -> "/v2/users/" + targetId + "/messages/" + messageId
            "group" -> "/v2/groups/" + targetId + "/messages/" + messageId
            "channel" -> "/channels/" + targetId + "/messages/" + messageId + "?hidetip=false"
            "dms" -> "/dms/" + targetId + "/messages/" + messageId + "?hidetip=false"
            else -> throw IllegalArgumentException("不支持的撤回场景: " + scene)
        }
        return request("DELETE", path)
    }

    /** 获取群资料（GET /v2/groups/{id}/info），返回官方原始对象。 */
    fun getGroupInfo(groupId: String): JsonObject =
        request("GET", "/v2/groups/" + groupId + "/info")

    /** 取群名（group_name 字段），字段缺失时返回空串。 */
    fun getGroupName(groupId: String): String = stringOf(getGroupInfo(groupId), "group_name") ?: ""

    /** 获取子频道资料（GET /channels/{id}），返回官方原始对象。 */
    fun getChannel(channelId: String): JsonObject = request("GET", "/channels/" + channelId)

    /** 取子频道名（name 字段），字段缺失时返回空串。 */
    fun getChannelName(channelId: String): String = stringOf(getChannel(channelId), "name") ?: ""

    /** 拉取子频道历史消息（GET /channels/{id}/messages?limit=N），返回消息数组。 */
    fun getChannelMessages(channelId: String, limit: Int = 50): JsonArray =
        requestArray("GET", "/channels/" + channelId + "/messages?limit=" + limit)

    /** 获取当前机器人加入的频道列表（GET /users/@me/guilds）。 */
    fun getMyGuilds(): JsonArray = requestArray("GET", "/users/@me/guilds")

    /** 获取指定频道下的子频道列表（GET /guilds/{id}/channels）。 */
    fun getGuildChannels(guildId: String): JsonArray =
        requestArray("GET", "/guilds/" + guildId + "/channels")

    /** 获取子频道精华消息列表（GET /channels/{id}/pins）。 */
    fun getChannelPins(channelId: String): JsonArray =
        requestArray("GET", "/channels/" + channelId + "/pins")

    /** 添加精华消息（PUT /channels/{id}/pins/{mid}），空响应返回空对象。 */
    fun addChannelPin(channelId: String, messageId: String): JsonObject =
        request("PUT", "/channels/" + channelId + "/pins/" + messageId)

    /** 移除精华消息（DELETE /channels/{id}/pins/{mid}），空响应返回空对象。 */
    fun removeChannelPin(channelId: String, messageId: String): JsonObject =
        request("DELETE", "/channels/" + channelId + "/pins/" + messageId)

    /** 获取子频道日程列表（GET /channels/{id}/schedules）。 */
    fun getChannelSchedules(channelId: String): JsonArray =
        requestArray("GET", "/channels/" + channelId + "/schedules")

    /**
     * 给消息添加表情表态（PUT /channels/{cid}/messages/{mid}/reactions/{type}/{emojiId}）。
     *
     * @param emojiType 表情类型，官方系统表情为 1、自定义 emoji 为 2，默认 2。
     * @param emojiId 表情 id。
     */
    fun addReaction(
        channelId: String,
        messageId: String,
        emojiId: String,
        emojiType: Int = 2
    ): JsonObject = request("PUT", reactionPath(channelId, messageId, emojiType, emojiId))

    /** 移除消息表情表态（DELETE 同 [addReaction] 的路径）。 */
    fun removeReaction(
        channelId: String,
        messageId: String,
        emojiId: String,
        emojiType: Int = 2
    ): JsonObject = request("DELETE", reactionPath(channelId, messageId, emojiType, emojiId))

    /** 构造表态端点路径。 */
    private fun reactionPath(
        channelId: String,
        messageId: String,
        emojiType: Int,
        emojiId: String
    ): String = "/channels/" + channelId + "/messages/" + messageId +
        "/reactions/" + emojiType + "/" + emojiId

    /** 构造 keyboard 对象：每个按钮标签占一行，含 id / render_data / action。 */
    private fun buildKeyboard(labels: List<String>): JsonObject {
        val rows = JsonArray()
        labels.forEachIndexed { index, label ->
            val button = JsonObject().apply {
                addProperty("id", "btn_" + index)
                add("render_data", JsonObject().apply {
                    addProperty("label", label)
                    addProperty("visited_label", label)
                    addProperty("style", 1)
                })
                add("action", JsonObject().apply {
                    addProperty("type", 2)
                    add("permission", JsonObject().apply { addProperty("type", 2) })
                    addProperty("data", label)
                    addProperty("enter", false)
                })
            }
            rows.add(JsonObject().apply {
                add("buttons", JsonArray().apply { add(button) })
            })
        }
        return JsonObject().apply {
            add("content", JsonObject().apply { add("rows", rows) })
        }
    }

    /** 确保 token 有效；[force] 为 true 时无条件刷新（用于 401 重试）。 */
    @Synchronized
    private fun ensureToken(force: Boolean = false) {
        if (!force && cachedToken.isNotEmpty() && nowMillis() < tokenExpiresAt - TOKEN_SKEW_MS) return
        refreshToken()
    }

    /** 重新取 token 并写入缓存。 */
    @Synchronized
    private fun refreshToken() {
        cacheToken(fetchToken())
    }

    /** 发起无需鉴权的取 token 请求，返回解析后的官方响应对象。 */
    private fun fetchToken(): JsonObject {
        val body = JsonObject().apply {
            addProperty("appId", credentials.appId)
            addProperty("clientSecret", credentials.clientSecret)
        }
        return parseObject(perform("POST", "/app/getAppAccessToken", body, auth = false))
    }

    /** 把官方 token 响应写入缓存，缺少 access_token 时抛异常。 */
    @Synchronized
    private fun cacheToken(result: JsonObject) {
        val token = stringOf(result, "access_token")
            ?: throw QQBotApiException("获取 access_token 失败：响应缺少该字段", -1, result.toString())
        cachedToken = token
        tokenExpiresAt = nowMillis() + (longOf(result, "expires_in") ?: DEFAULT_EXPIRES_IN) * 1000L
    }

    /**
     * 执行一次 HTTP 请求并返回响应文本。
     *
     * [auth] 为 true 时携带 token 并支持 401 强制刷新后重试一次；
     * 任何非 2xx、网络异常都会转换为 [QQBotApiException]。
     */
    private fun perform(
        method: String,
        path: String,
        body: JsonObject?,
        auth: Boolean,
        allowAuthRetry: Boolean = true
    ): String {
        val token: String? = if (auth) {
            ensureToken()
            cachedToken
        } else {
            null
        }
        val request = buildRequest(method, path, body, token)
        val outcome: Pair<Int, String> = try {
            client.newCall(request).execute().use { response ->
                response.code to (response.body?.string() ?: "")
            }
        } catch (e: Exception) {
            throw QQBotApiException("网络请求失败: " + method + " " + path + " -> " + e.message, -1, "", e)
        }
        val code = outcome.first
        val text = outcome.second
        if (code !in 200..299) {
            if (auth && code == 401 && allowAuthRetry) {
                ensureToken(force = true)
                return perform(method, path, body, auth = true, allowAuthRetry = false)
            }
            throw QQBotApiException(
                "HTTP " + code + ": " + method + " " + path + " -> " + text.take(500),
                code,
                text
            )
        }
        return text
    }

    /** 根据方法与 token 构造 OkHttp Request。 */
    private fun buildRequest(method: String, path: String, body: JsonObject?, token: String?): Request {
        val builder = Request.Builder().url(baseUrl.removeSuffix("/") + path)
        builder.header("Accept", "application/json")
        if (token != null) builder.header("Authorization", "QQBot " + token)
        val requiresBody = method.equals("POST", ignoreCase = true) ||
            method.equals("PUT", ignoreCase = true) ||
            method.equals("PATCH", ignoreCase = true)
        val requestBody = when {
            body != null -> body.toString().toRequestBody(JSON_MEDIA_TYPE)
            requiresBody -> "{}".toRequestBody(JSON_MEDIA_TYPE)
            else -> null
        }
        return builder.method(method.uppercase(), requestBody).build()
    }

    /** 把响应文本解析为 JsonElement，空响应返回 null，非法 JSON 抛异常。 */
    private fun parseElement(text: String): JsonElement? {
        if (text.isBlank()) return null
        return try {
            gson.fromJson(text, JsonElement::class.java)
        } catch (e: Exception) {
            throw QQBotApiException("响应不是合法 JSON: " + text.take(200), -1, text, e)
        }
    }

    /** 把响应文本规范化为 JsonObject；数组包装成 {"items": [...]}，标量包装成 {"value": ...}。 */
    private fun parseObject(text: String): JsonObject = when (val element = parseElement(text)) {
        null -> JsonObject()
        is JsonObject -> element
        is JsonArray -> JsonObject().apply { add("items", element) }
        else -> JsonObject().apply { add("value", element) }
    }

    /** 安全读取字符串字段；字段为 JsonNull 或非原始值时返回 null。 */
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

    /** 安全读取数组字段；字段缺失或不是数组时返回 null。 */
    private fun arrOf(obj: JsonObject?, key: String): JsonArray? {
        val element = obj?.get(key) ?: return null
        return if (element.isJsonArray) element.asJsonArray else null
    }
}

/**
 * REST 请求失败时抛出的异常。
 *
 * @property httpCode HTTP 状态码；网络层错误或本地校验失败为 -1。
 * @property responseBody 服务端原始响应体，便于排查。
 */
class QQBotApiException(
    message: String,
    val httpCode: Int,
    val responseBody: String = "",
    cause: Throwable? = null
) : RuntimeException(message, cause)
