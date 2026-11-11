// 设计参考 Telegram for Android (GPL-2.0)
// 本文件实现圆形头像，对应 TG 的 AvatarDrawable / ImageReceiver。
// 【逐条源码出处：文件:行号】
//   · DialogCell.java:2518,2495,3196-3198  头像 52dp(两行)/56dp(三行)、圆角 26dp（52/2 即圆形）、60dp 仅为点击热区
//   · ThemeColors.java:20 createDefaultColors()  avatar 七色
//     #FFCC5049 / #FFD67722 / #FF955CDB / #FF40A920 / #FF309EBA / #FF368AD1 / #FFC7508B
package org.example.qqbotchat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import org.example.qqbotchat.ui.theme.TgDimens
import org.example.qqbotchat.ui.theme.TgTheme

@Composable
fun TgAvatar(
    name: String,
    avatarUrl: String?,
    size: Dp = TgDimens.AvatarSize,
    modifier: Modifier = Modifier,
) {
    // 调色板取自 ThemeColors.java:20（TgDefaultColors.avatarColors，见 ui/theme/Color.kt）。
    val palette = TgTheme.colors.avatarPalette
    val bg = remember(name) {
        palette[java.lang.Math.floorMod(name.hashCode(), palette.size)]
    }
    Box(
        // CircleShape 等价于 52dp 头像的 26dp 圆角（DialogCell.java:3196-3198）。
        modifier = modifier.size(size).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = name,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = name.trim().take(1).ifEmpty { "?" },
                color = Color.White,
                fontSize = (size.value * 0.4f).sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
