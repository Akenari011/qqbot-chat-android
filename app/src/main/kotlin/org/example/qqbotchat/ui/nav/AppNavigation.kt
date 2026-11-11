// 设计参考 Telegram for Android (GPL-2.0)
// 简单 Navigation（登录→会话列表→聊天→设置），对应 TG 的 Fragment 回退栈。
// 来自 TG 的常量：无（导航不含视觉常量）；各屏幕的色值/尺寸见 ui/theme/Color.kt 与 Dimens.kt。
package org.example.qqbotchat.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.example.qqbotchat.ui.screens.ChatScreen
import org.example.qqbotchat.ui.screens.ConversationListScreen
import org.example.qqbotchat.ui.screens.LoginScreen
import org.example.qqbotchat.ui.screens.SettingsScreen
import org.example.qqbotchat.vm.ChatViewModel

sealed class Route(val route: String) {
    data object Login : Route("login")
    data object Conversations : Route("conversations")
    data object Chat : Route("chat/{conversationId}") {
        fun of(id: String): String = "chat/" + id
    }
    data object Settings : Route("settings")
}

@Composable
fun AppNavigation(viewModel: ChatViewModel, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Route.Login.route,
        modifier = modifier,
    ) {
        composable(Route.Login.route) {
            val state by viewModel.login.collectAsStateWithLifecycle()
            LaunchedEffect(state.connected) {
                if (state.connected) {
                    navController.navigate(Route.Conversations.route) {
                        popUpTo(Route.Login.route) { inclusive = true }
                    }
                }
            }
            LoginScreen(
                state = state,
                onAppIdChange = viewModel::onAppIdChange,
                onAppSecretChange = viewModel::onAppSecretChange,
                onConnect = viewModel::connect,
                onDemo = viewModel::enterDemoMode,
            )
        }

        composable(Route.Conversations.route) {
            val state by viewModel.conversations.collectAsStateWithLifecycle()
            ConversationListScreen(
                state = state,
                onQueryChange = viewModel::onQueryChange,
                onOpen = { id ->
                    viewModel.openConversation(id)
                    navController.navigate(Route.Chat.of(id))
                },
                onTogglePin = viewModel::togglePin,
                onMarkRead = viewModel::markRead,
                onToggleMute = viewModel::toggleMute,
                onToggleArchive = viewModel::toggleArchive,
                onDelete = viewModel::deleteConversation,
                onSettings = { navController.navigate(Route.Settings.route) },
            )
        }

        composable(
            route = Route.Chat.route,
            arguments = listOf(navArgument("conversationId") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("conversationId") ?: ""
            LaunchedEffect(id) { viewModel.openConversation(id) }
            val state by viewModel.chat.collectAsStateWithLifecycle()
            val clipboard = LocalClipboardManager.current
            val uriHandler = LocalUriHandler.current
            ChatScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onDraftChange = viewModel::onDraftChange,
                onSend = viewModel::send,
                onReply = viewModel::setReplyingTo,
                onCancelReply = viewModel::clearReply,
                onRecall = viewModel::recall,
                onSearch = viewModel::searchInChat,
                onShowActions = viewModel::showActions,
                onDismissActions = viewModel::dismissActions,
                onCopy = { text -> clipboard.setText(AnnotatedString(text)) },
                onForward = { messageId ->
                    // 转发到列表里第一个非当前会话（简化版，后续可加目标选择器）。
                    val others = viewModel.conversations.value.conversations
                        .filter { it.id != id }
                    others.firstOrNull()?.let { viewModel.forward(messageId, it.id) }
                },
                onReact = { messageId, emoji -> viewModel.react(messageId, emoji) },
                onDelete = viewModel::deleteMessage,
                onChannelAction = viewModel::runChannelAction,
                onDismissTools = viewModel::dismissTools,
                onAttachMedia = { bytes, name, type -> viewModel.sendMedia(bytes, name, type) },
                onButtonClick = viewModel::onButtonClick,
                onOpenMedia = { url ->
                    // 把媒体 / 文件 URL 交给系统查看器；本地占位 URL 直接忽略。
                    if (url.startsWith("http")) runCatching { uriHandler.openUri(url) }
                },
            )
        }

        composable(Route.Settings.route) {
            val state by viewModel.settings.collectAsStateWithLifecycle()
            SettingsScreen(
                state = state,
                onDarkThemeChange = viewModel::setDarkTheme,
                onBlueGradientChange = viewModel::setBlueGradient,
                onBumpFontScale = viewModel::bumpFontScale,
                onClearAll = viewModel::clearAll,
                onExportLogs = viewModel::exportLogs,
                onDismissExport = viewModel::dismissExport,
                onDismissStatus = viewModel::dismissStatus,
                onLogout = {
                    viewModel.logout()
                    navController.navigate(Route.Login.route) {
                        popUpTo(Route.Conversations.route) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
