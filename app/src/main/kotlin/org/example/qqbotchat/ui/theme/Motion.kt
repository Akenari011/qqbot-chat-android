package org.example.qqbotchat.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring

/** Short, interruptible transitions. Compose honors the system animator duration scale. */
object TgMotion {
    const val NavigationMillis = 240
    const val ContentMillis = 180
    fun <T> navigation() = tween<T>(NavigationMillis, easing = FastOutSlowInEasing)
    fun <T> content() = tween<T>(ContentMillis, easing = FastOutSlowInEasing)
    fun replyReturn() = spring<Float>(dampingRatio = 1f, stiffness = 600f)
}
