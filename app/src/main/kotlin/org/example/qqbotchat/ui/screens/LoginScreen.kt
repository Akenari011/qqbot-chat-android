// 设计参考 Telegram for Android (GPL-2.0)
// 登录页，对应 TG 的 LoginActivity / 引导页（AppID / AppSecret 输入 + 连接状态）。
// 来自 TG 的常量：ActionBar 图标 24dp（res/drawable 图标网格）、主题色 actionBarDefault /
// chats_name / chats_message（ThemeColors.java:20，见 ui/theme/Color.kt）；正文字号 16sp
// （SharedConfig.java:323 / Theme.java:8133）。本页为 QQ Bot 凭据表单，TG 无同名界面。
package org.example.qqbotchat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.qqbotchat.R
import org.example.qqbotchat.ui.theme.TgDimens
import org.example.qqbotchat.ui.theme.TgTheme
import org.example.qqbotchat.vm.LoginUiState

@Composable
fun LoginScreen(
    state: LoginUiState,
    onAppIdChange: (String) -> Unit,
    onAppSecretChange: (String) -> Unit,
    onConnect: () -> Unit,
    onDemo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = TgTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_launcher),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(80.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "QQBot Chat",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = c.actionBarTitle,
        )
        Spacer(Modifier.height(6.dp))
        Text(text = "连接你的 QQ 机器人", fontSize = 14.sp, color = c.subtitle)
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(
            value = state.appId,
            onValueChange = onAppIdChange,
            label = { Text("AppID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.appSecret,
            onValueChange = onAppSecretChange,
            label = { Text("AppSecret") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onConnect,
            enabled = !state.connecting,
            modifier = Modifier.fillMaxWidth().height(TgDimens.ComposerHeight),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = c.primary),
        ) {
            Text(if (state.connecting) "连接中…" else "连接", fontSize = TgDimens.MessageTextSize)
        }
        Spacer(Modifier.height(10.dp))
        // 演示模式：无需真实凭证即可浏览完整 UI（真实连接仍走上面的凭证校验）。
        Button(
            onClick = onDemo,
            enabled = !state.connecting,
            modifier = Modifier.fillMaxWidth().height(44.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = c.surface,
                contentColor = c.subtitle,
            ),
        ) {
            Text("演示模式（无需凭证）", fontSize = 14.sp)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = state.status,
            fontSize = 13.sp,
            color = if (state.connected) c.primary else c.subtitle,
        )
    }
}
