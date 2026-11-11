// 设计参考 Telegram for Android (GPL-2.0)
// 本文件把 TG 亮/暗配色（Color.kt）与字号（Type.kt）注入 Material3，并对外提供 LocalTgColors。
// 来自 TG 的常量（源码文件:行号）：ThemeColors.java:20 actionBarDefault=#FFFFFFFF、
// actionBarDefaultTitle=#FF1A1D21、divider=#FFD9D9D9、chat_inBubble=#FFFFFFFF、
// chat_outBubble=#FFEFFFDE；styles.xml:10/:21 白背景、:35 黑背景；可选蓝渐变来自本任务 iOS 规格。
package org.example.qqbotchat.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * @param darkTheme     暗色主题（TG Dark/Night）
 * @param blueGradient  可选「蓝色渐变气泡」主题（本任务 iOS 版规格，非 TG Android 默认）
 * @param fontScale     全局字体缩放（设置页可调）
 */
@Composable
fun QQBotChatTheme(
    darkTheme: Boolean = false,
    blueGradient: Boolean = false,
    fontScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val tg = when {
        darkTheme && blueGradient -> darkTgColors().copy(
            outBubbleStart = TgIosColors.outBubbleStart,
            outBubbleEnd = TgIosColors.outBubbleEnd,
            outBubbleText = TgIosColors.outText,
            outMeta = TgIosColors.outMeta,
        )
        darkTheme -> darkTgColors()
        blueGradient -> lightBlueTgColors()
        else -> lightTgColors()
    }
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = tg.primary,
            onPrimary = tg.unreadText,
            background = tg.background,
            onBackground = tg.actionBarTitle,
            surface = tg.surface,
            onSurface = tg.actionBarTitle,
            outline = tg.divider,
            error = tg.error,
        )
    } else {
        lightColorScheme(
            primary = tg.primary,
            onPrimary = tg.unreadText,
            background = tg.background,
            onBackground = tg.actionBarTitle,
            surface = tg.surface,
            onSurface = tg.actionBarTitle,
            outline = tg.divider,
            error = tg.error,
        )
    }
    CompositionLocalProvider(LocalTgColors provides tg) {
        MaterialTheme(
            colorScheme = scheme,
            typography = appTypography(fontScale),
            content = content,
        )
    }
}
