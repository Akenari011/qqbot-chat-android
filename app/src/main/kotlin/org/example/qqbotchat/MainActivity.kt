// 设计参考 Telegram for Android (GPL-2.0)
// 应用入口：ComponentActivity + setContent（对应 TG LaunchActivity 的入口职责）。
// 来自 TG 的常量：无（入口不含视觉常量）；窗口主题见 res/values/themes.xml，
// 亮/暗配色与字号由 ui/theme/Theme.kt（ThemeColors.java:20 / SharedConfig.java:323）注入。
package org.example.qqbotchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.qqbotchat.ui.nav.AppNavigation
import org.example.qqbotchat.ui.theme.QQBotChatTheme
import org.example.qqbotchat.ui.theme.TgTheme
import org.example.qqbotchat.vm.ChatViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: ChatViewModel = viewModel()
            val settings by vm.settings.collectAsStateWithLifecycle()
            QQBotChatTheme(
                darkTheme = settings.darkTheme,
                blueGradient = settings.blueGradient,
                fontScale = settings.fontScale,
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = TgTheme.colors.background,
                ) {
                    AppNavigation(viewModel = vm)
                }
            }
        }
    }
}
