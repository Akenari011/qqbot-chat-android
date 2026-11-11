// 设计参考 Telegram for Android (GPL-2.0)
// 本文件把 ISO 时间戳格式化为 TG 列表/气泡/日期胶囊上的中文短文案。
// 来自 TG 的常量：无（时间数值字号见 Dimens.kt：列表时间 14sp、气泡时间戳 11sp、日期胶囊 13sp）；
// 展示格式对照 TG DialogCell 的 "昨天 / 星期 / 日期" 与 ChatMessageCell 的 HH:mm 时间戳。
package org.example.qqbotchat.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun local(iso: String): LocalDateTime? = try {
    LocalDateTime.ofInstant(Instant.parse(iso), ZoneId.systemDefault())
} catch (e: Exception) {
    null
}

/** 按天分组用的 key（ChatMessage.timestamp 为 ISO-8601，与 core.MessageParser.epoch 一致）。 */
fun dayKey(iso: String): String = local(iso)?.toLocalDate()?.toString() ?: ""

/** 气泡/会话时间：HH:mm（TG ChatMessageCell 时间戳格式）。 */
fun clockTime(iso: String): String =
    local(iso)?.format(DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)) ?: ""

/** 会话列表右侧时间（TG DialogCell）：今天显示时间、昨天显示"昨天"、一周内显示星期、更早显示日期。 */
fun listTime(iso: String): String {
    val dt = local(iso) ?: return ""
    val today = LocalDate.now(ZoneId.systemDefault())
    val date = dt.toLocalDate()
    val week = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    return when {
        date == today -> dt.format(DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA))
        date == today.minusDays(1) -> "昨天"
        date.isAfter(today.minusDays(7)) -> week[dt.dayOfWeek.value - 1]
        date.year == today.year -> dt.format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINA))
        else -> dt.format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA))
    }
}

/** 聊天页居中的日期胶囊文案（TG chat_serviceBackground 上的 13sp 文本）。 */
fun dateChip(iso: String): String {
    val dt = local(iso) ?: return ""
    val today = LocalDate.now(ZoneId.systemDefault())
    val date = dt.toLocalDate()
    return when {
        date == today -> "今天"
        date == today.minusDays(1) -> "昨天"
        date.year == today.year -> dt.format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINA))
        else -> dt.format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA))
    }
}
