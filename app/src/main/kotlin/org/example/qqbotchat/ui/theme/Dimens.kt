// 设计参考 Telegram for Android (GPL-2.0)
// 本文件集中 TG Android 源码的尺寸 / 字号常量（本轮源码精读的权威结论，逐条注明「源码文件:行号」）。
//
// 【气泡几何】SharedConfig.java:323 bubbleRadius=17；MessageDrawable.java:610-616, 704-832 generatePath()：
//   rad=17dp、相邻消息小圆角 nearRad=6dp、尾巴内圆角 smallRad=6dp、绘制内缩 padding=2dp、
//   尾巴水平偏移 tailOffset=8dp、尖角 tip=2.6dp
// 【气泡尺寸】ChatMessageCell.java:13726-13728：
//   发出气泡左右内边距 11dp / 接收 17dp、上下 10dp、总高 = textHeight + 19.5dp、气泡宽 = 文本宽 + 31dp；
//   最大宽度 = min(parentW, displayH) - 80dp - (有头像 ? 42dp : 0)
// 【会话行】DialogCell.java:174-177,173,2514,2502：
//   行高 70dp(两行)/76dp(三行)、文字起始 72dp、名字左侧 76dp(两行)/78dp(三行)、时间右 15dp、
//   分隔线 1px #FFD9D9D9；DialogCell.java:2518,2495,3196-3198：头像 52dp(两行)/56dp(三行)、
//   圆角 26dp、60dp 仅为点击热区
// 【字号】SharedConfig.java:323 / Theme.java:8133 正文 16sp、作者名 14sp bold、Theme.java:8506 时间 12sp、
//   DialogCell.java:1298-1313 会话名 17sp(两行)/16sp(三行)、摘要 16sp(两行)/15sp(三行)、日期胶囊 13sp(近似)
// 【未读角标】DialogCell.java:1275-1284,5386：高 20.666dp、圆角 11.5dp、文字 12sp
// 【归档数值】third_party/telegram-android/res/values/styles.xml:132 分隔线 1dp、:238 角标高 23dp、
//   :239/:240 水平内边距 7dp、:244 minWidth 23dp、:245 字号 13dp；values.xml:5 圆角 16dp
// 【iOS 规格（保留作对照）】气泡宽上限 0.85 屏宽、圆角 16dp、内边距 11/6dp、头像 60dp、
//   字号 16/15/14/11/13sp、分隔线 86dp 缩进（README.md:29-30 曾归纳，本轮已按 TG Android 源码修正）
package org.example.qqbotchat.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object TgDimens {
    // ---- MessageDrawable.java:610-616,704-832 / SharedConfig.java:323 ----
    val BubbleRadius = 17.dp             // rad（SharedConfig.java:323 bubbleRadius=17）
    val BubbleNearRadius = 6.dp          // nearRad（相邻消息小圆角）
    val BubbleSmallRadius = 6.dp         // smallRad（尾巴内圆角）
    val BubbleDrawPadding = 2.dp         // padding（Path 绘制内缩）
    val BubbleTailOffset = 8.dp          // tailOffset（尾巴水平偏移）
    val BubbleTip = 2.6.dp               // tip（尖角）
    val BubbleCorner = 17.dp             // 兼容别名：= BubbleRadius

    // ---- ChatMessageCell.java:13726-13728 ----
    val BubblePaddingOutHorizontal = 11.dp  // 发出气泡左右内边距
    val BubblePaddingInHorizontal = 17.dp   // 接收气泡左右内边距
    val BubblePaddingVertical = 10.dp       // 上下内边距
    val BubbleExtraHeight = 19.5.dp        // 总高 = textHeight + 19.5dp
    val BubbleExtraWidth = 31.dp           // 气泡宽 = 文本宽 + 31dp
    val MaxWidthBaseDeduction = 80.dp      // min(parentW, displayH) - 80dp
    val MaxWidthAvatarDeduction = 42.dp    // 有头像再减 42dp
    val BubbleMaxWidthFraction = 0.85f     // iOS 规格保留（与上面公式取较小值）

    // ---- DialogCell.java（会话行）----
    val AvatarSize = 52.dp                // 两行头像 52dp（:2518）
    val AvatarSizeThreeLine = 56.dp       // 三行头像 56dp（:2495）
    val AvatarCorner = 26.dp              // 头像圆角 26dp（:3196-3198）
    val AvatarTouchSize = 60.dp           // 60dp 仅为点击热区（:3196-3198）
    val ListItemHeight = 70.dp            // 两行行高（:174-177）
    val ListItemHeightThreeLine = 76.dp   // 三行行高（:173）
    val TextStart = 72.dp                 // 文字起始 72dp（分隔线缩进同值）
    val DividerStartPadding = 72.dp       // 分隔线左缩进 = 文字起始
    val NameLeftTwoLine = 76.dp           // 名字左侧 76dp（:2514）
    val NameLeftThreeLine = 78.dp         // 名字左侧 78dp（:2502）
    val TimeRightPadding = 15.dp          // 时间右 15dp
    val DividerThickness = 1.dp           // 1px #FFD9D9D9（近似 1dp）
    val CellHorizontalPadding = 16.dp     // 单元格左右边距（沿用）

    // ---- 字号 ----
    val MessageTextSize = 16.sp           // 正文 16sp（SharedConfig.java:323 / Theme.java:8133）
    val AuthorNameSize = 14.sp            // 作者名 14sp bold
    val MessageTimeSize = 12.sp           // 气泡时间 12sp（Theme.java:8506）
    val NameSize = 17.sp                  // 两行会话名 17sp（DialogCell.java:1298-1313）
    val NameSizeThreeLine = 16.sp         // 三行会话名 16sp
    val SubtitleSize = 16.sp              // 两行摘要 16sp
    val SubtitleSizeThreeLine = 15.sp     // 三行摘要 15sp
    val ListTimeSize = 12.sp              // 列表时间采用 Theme.java:8506 的 12sp
    val DateChipTextSize = 13.sp          // 日期胶囊 13sp（近似）

    // ---- 未读角标 DialogCell.java:1275-1284,5386 ----
    val UnreadBadgeHeight = 20.666.dp
    val UnreadBadgeRadius = 11.5.dp
    val UnreadBadgeTextSize = 12.sp

    // ---- 其它 ----
    val ChatAvatarSize = 32.dp
    val DateChipHeight = 22.dp            // iOS 规格保留（日期胶囊高）
    val ActionBarHeight = 56.dp
    val ComposerHeight = 48.dp
    val ComposerCorner = 24.dp
    val IconSize = 24.dp
}
