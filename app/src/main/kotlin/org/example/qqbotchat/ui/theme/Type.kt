// 设计参考 Telegram for Android (GPL-2.0)
// 本文件把 TG 的字号接入 Material3 Typography，并提供全局字体缩放。
// 来自 TG 的常量（源码文件:行号）：正文 16sp（SharedConfig.java:323 → Theme.java:8133）、
// 作者名 14sp bold、时间 12sp（Theme.java:8506）、会话名 17sp/16sp、摘要 16sp/15sp
// （DialogCell.java:1298-1313）、日期胶囊 13sp（近似）；数值本体集中在 Dimens.kt。
package org.example.qqbotchat.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

fun appTypography(scale: Float): Typography {
    val s = scale.coerceIn(0.85f, 1.30f)
    return Typography(
        titleMedium = TextStyle(fontSize = (17f * s).sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontSize = (16f * s).sp),      // 正文 16sp
        bodyMedium = TextStyle(fontSize = (14f * s).sp),     // 作者名 14sp
        bodySmall = TextStyle(fontSize = (12f * s).sp),      // 时间 12sp
        labelSmall = TextStyle(fontSize = (12f * s).sp),
    )
}
