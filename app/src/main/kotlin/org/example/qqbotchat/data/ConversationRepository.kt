// 设计参考 Telegram for Android (GPL-2.0)
// 会话持久化：对应 TG 的 MessagesStorage / SQLite 会话表（本客户端用单文件 JSON 简化实现）。
// 来自 TG 的常量：无（持久化层不含视觉常量）；序列化格式由 :core 的 ConversationStore.toJson
// 决定（手写字段映射，避免 Gson 反射破坏 Kotlin val 默认值）。
//
// 设计要点：
//   - 存 filesDir/conversations.json，写入用「临时文件 + 原子重命名」，避免写一半掉电损坏数据；
//   - 读取失败（损坏 / 版本过新）时把坏文件备份为 corrupt 后缀并返回 0，保证 App 仍能启动；
//   - 只持久化用户真实会话，演示数据在无持久化数据时注入。
package org.example.qqbotchat.data

import android.content.Context
import org.example.qqbotchat.core.ConversationStore
import java.io.File

/**
 * 会话数据的本地持久化。
 *
 * @param context 应用上下文（只用 filesDir，不持有 Activity 引用）。
 */
class ConversationRepository(context: Context) {

    private val appContext = context.applicationContext

    private val file: File
        get() = File(appContext.filesDir, FILE_NAME)

    private val tmpFile: File
        get() = File(appContext.filesDir, FILE_NAME + ".tmp")

    /**
     * 从磁盘恢复会话，恢复成功返回会话数量。
     *
     * 文件不存在返回 0；文件损坏时备份为 corrupt 后缀后返回 0（不抛异常）。
     */
    fun load(store: ConversationStore): Int {
        val f = file
        if (!f.exists() || f.length() == 0L) return 0
        return try {
            store.loadJson(f.readText())
        } catch (e: Exception) {
            // 数据损坏：备份后放弃恢复，避免每次启动都失败。
            runCatching {
                f.renameTo(File(appContext.filesDir, FILE_NAME + ".corrupt-" + System.currentTimeMillis()))
            }
            0
        }
    }

    /**
     * 把会话写入磁盘。写入失败不抛异常（持久化失败不应影响聊天）。
     *
     * @return 是否写入成功。
     */
    fun save(store: ConversationStore): Boolean = try {
        val json = store.toJson()
        tmpFile.writeText(json)
        // 原子替换：先写临时文件，再覆盖正式文件。
        if (file.exists()) file.delete()
        tmpFile.renameTo(file)
    } catch (e: Exception) {
        false
    }

    /** 删除本地持久化文件（退出登录 / 清空记录时调用）。 */
    fun clear() {
        runCatching { file.delete() }
        runCatching { tmpFile.delete() }
    }

    /** 是否已有持久化数据（用于决定是否注入演示数据）。 */
    fun hasData(): Boolean = file.exists() && file.length() > 0L

    companion object {
        /** 持久化文件名。 */
        private const val FILE_NAME = "conversations.json"
    }
}
