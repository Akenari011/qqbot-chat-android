// 设计参考 Telegram for Android (GPL-2.0)
// 本文件定义亮/暗两套配色，按 TG Android 源码 ThemeColors.java:20 createDefaultColors() 实现。
// 核对基准：TG 上游 DrKLO/Telegram（commit 7be672ea...），归档见 qqbot-chat-android/third_party/telegram-android/。
//
// 【逐条源码出处：文件路径 + 行号】
//  A) ThemeColors.java:20 createDefaultColors()（默认浅色主题，本轮精读的权威色值）：
//     chat_inBubble=#FFFFFFFF、chat_outBubble=#FFEFFFDE、chat_inBubbleSelected=#FFECF7FD、
//     chat_outBubbleSelected=#FFD9F7C5、chat_messageTextIn/Out=#FF000000、
//     chat_messageLinkIn/Out=#FF2678B6、chat_inTimeText=#FFA1AAB3、chat_outTimeText=#FF70B15C、
//     actionBarDefault=#FFFFFFFF、actionBarDefaultTitle=#FF1A1D21、divider=#FFD9D9D9、
//     chats_name=#FF1A1D21、chats_message=#FF75787A、chats_date=#FF848688、
//     chats_unreadCounter=#FF229AF0、chats_unreadCounterMuted=#FFBEC3C7、chats_pinnedIcon=#FF919294、
//     chats_sentCheck=#FF46AA36、chats_draft=#FFDD4B39、
//     avatar 七色 #FFCC5049/#FFD67722/#FF955CDB/#FF40A920/#FF309EBA/#FF368AD1/#FFC7508B
//  B) 归档 res/values/colors.xml（widget 资源，保留作核对）：
//     :3 divider #dcdcdc、:4 widget_name #212121、:5 widget_badge #ffffff、:6 widget_time #96999C、
//     :7 widget_text #8B8D8F、:8 widget_divider #d9d9d9、:9 widget_action_text #8B8D8F、:10 widget_edit_text #999999
//  C) 归档 res/values/styles.xml：:115 ActionBar 背景 #ff527da3、:10/:21 白背景、:35 黑背景（Dark）、
//     :133 ListView divider=@color/divider、:250 Badge textColor=@color/widget_badge
//  D) 本任务 iOS 版规格（可选「蓝色渐变」主题，非 TG Android 默认）：
//     chat_outBubble 渐变 #57B2E0→#0088FF、chat_inBubble=#FFFFFF、时间灰 #96999C
//  E) third_party/telegram-android/README.md:29-30 归纳的圆角/内边距/字号常量
package org.example.qqbotchat.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** 归档 res/values/colors.xml 的原始 widget 色值（逐行核对用，见文件头 B 段）。 */
object TgPalette {
    val Divider = Color(0xFFDCDCDC)              // colors.xml:3  divider
    val NameBlack = Color(0xFF212121)            // colors.xml:4  widget_name
    val BadgeWhite = Color(0xFFFFFFFF)           // colors.xml:5  widget_badge
    val TimeGray = Color(0xFF96999C)             // colors.xml:6  widget_time
    val SubtitleGray = Color(0xFF8B8D8F)         // colors.xml:7  widget_text
    val WidgetDivider = Color(0xFFD9D9D9)        // colors.xml:8  widget_divider
    val WidgetActionText = Color(0xFF8B8D8F)     // colors.xml:9  widget_action_text
    val WidgetEditText = Color(0xFF999999)       // colors.xml:10 widget_edit_text
    val ActionBarClassicBlue = Color(0xFF527DA3) // styles.xml:115 #ff527da3
}

/**
 * ThemeColors.java:20 createDefaultColors() 的默认浅色色值，变量名保留 TG 的 key_* 便于逐个核对。
 * 这是本轮精读的权威结论，优先于此前基于 iOS 侧的估算值。
 */
object TgDefaultColors {
    val chat_inBubble = Color(0xFFFFFFFF)                 // chat_inBubble
    val chat_outBubble = Color(0xFFEFFFDE)                // chat_outBubble（浅绿）
    val chat_inBubbleSelected = Color(0xFFECF7FD)         // chat_inBubbleSelected
    val chat_outBubbleSelected = Color(0xFFD9F7C5)        // chat_outBubbleSelected
    val chat_messageTextIn = Color(0xFF000000)            // chat_messageTextIn
    val chat_messageTextOut = Color(0xFF000000)           // chat_messageTextOut
    val chat_messageLinkIn = Color(0xFF2678B6)            // chat_messageLinkIn
    val chat_messageLinkOut = Color(0xFF2678B6)           // chat_messageLinkOut
    val chat_inTimeText = Color(0xFFA1AAB3)               // chat_inTimeText
    val chat_outTimeText = Color(0xFF70B15C)              // chat_outTimeText
    val actionBarDefault = Color(0xFFFFFFFF)              // actionBarDefault
    val actionBarDefaultTitle = Color(0xFF1A1D21)         // actionBarDefaultTitle
    val divider = Color(0xFFD9D9D9)                       // divider
    val chats_name = Color(0xFF1A1D21)                    // chats_name
    val chats_message = Color(0xFF75787A)                 // chats_message
    val chats_date = Color(0xFF848688)                    // chats_date
    val chats_unreadCounter = Color(0xFF229AF0)           // chats_unreadCounter
    val chats_unreadCounterMuted = Color(0xFFBEC3C7)      // chats_unreadCounterMuted
    val chats_pinnedIcon = Color(0xFF919294)              // chats_pinnedIcon
    val chats_sentCheck = Color(0xFF46AA36)               // chats_sentCheck
    val chats_draft = Color(0xFFDD4B39)                   // chats_draft
    val avatarColors = listOf(                            // AvatarDrawable 七色
        Color(0xFFCC5049), Color(0xFFD67722), Color(0xFF955CDB), Color(0xFF40A920),
        Color(0xFF309EBA), Color(0xFF368AD1), Color(0xFFC7508B),
    )
}

/** iOS 版规格的可选蓝渐变气泡色（本任务原始要求，非 TG Android 默认）。 */
object TgIosColors {
    val outBubbleStart = Color(0xFF57B2E0)
    val outBubbleEnd = Color(0xFF0088FF)
    val outText = Color(0xFFFFFFFF)
    val outMeta = Color(0xCCFFFFFF)
    val inBubble = Color(0xFFFFFFFF)
    val timeGray = Color(0xFF96999C)
}

/**
 * 屏幕取色的唯一入口。字段名对应 TG Theme.java 的 key_* 与 DialogCell / ChatMessageCell 常量。
 * outBubbleStart/outBubbleEnd 相同即为纯色气泡（默认 TG 绿）；不同则为渐变（可选 iOS 蓝主题）。
 */
data class TgColors(
    val actionBar: Color,
    val actionBarTitle: Color,
    val background: Color,
    val surface: Color,
    val inBubble: Color,
    val inBubbleSelected: Color,
    val inBubbleBorder: Color,
    val inBubbleText: Color,
    val outBubbleStart: Color,
    val outBubbleEnd: Color,
    val outBubbleSelected: Color,
    val outBubbleText: Color,
    val link: Color,
    val inMeta: Color,
    val outMeta: Color,
    val divider: Color,
    val name: Color,
    val subtitle: Color,
    val time: Color,
    val unreadBadge: Color,
    val unreadBadgeMuted: Color,
    val unreadText: Color,
    val pinnedIcon: Color,
    val sentCheck: Color,
    val draft: Color,
    val primary: Color,
    val composerBackground: Color,
    val composerBorder: Color,
    val dateChip: Color,
    val dateChipText: Color,
    val replyBar: Color,
    val icon: Color,
    val error: Color,
    val avatarPalette: List<Color>,
)

/** 默认浅色主题：TG ThemeColors.java:20 createDefaultColors() 原值。 */
fun lightTgColors(): TgColors = TgColors(
    actionBar = TgDefaultColors.actionBarDefault,
    actionBarTitle = TgDefaultColors.actionBarDefaultTitle,
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    inBubble = TgDefaultColors.chat_inBubble,
    inBubbleSelected = TgDefaultColors.chat_inBubbleSelected,
    inBubbleBorder = Color(0xFFE7E7E7),
    inBubbleText = TgDefaultColors.chat_messageTextIn,
    outBubbleStart = TgDefaultColors.chat_outBubble,       // 纯色浅绿 #FFEFFFDE
    outBubbleEnd = TgDefaultColors.chat_outBubble,
    outBubbleSelected = TgDefaultColors.chat_outBubbleSelected,
    outBubbleText = TgDefaultColors.chat_messageTextOut,
    link = TgDefaultColors.chat_messageLinkIn,
    inMeta = TgDefaultColors.chat_inTimeText,
    outMeta = TgDefaultColors.chat_outTimeText,
    divider = TgDefaultColors.divider,
    name = TgDefaultColors.chats_name,
    subtitle = TgDefaultColors.chats_message,
    time = TgDefaultColors.chats_date,
    unreadBadge = TgDefaultColors.chats_unreadCounter,
    unreadBadgeMuted = TgDefaultColors.chats_unreadCounterMuted,
    unreadText = TgPalette.BadgeWhite,
    pinnedIcon = TgDefaultColors.chats_pinnedIcon,
    sentCheck = TgDefaultColors.chats_sentCheck,
    draft = TgDefaultColors.chats_draft,
    primary = Color(0xFF229AF0),
    composerBackground = Color(0xCCFFFFFF),
    composerBorder = TgDefaultColors.divider,
    dateChip = Color(0x66546A8A),
    dateChipText = Color(0xFFFFFFFF),
    replyBar = Color(0xFFF1F3F5),
    icon = Color(0xFF757575),
    error = Color(0xFFE53935),
    avatarPalette = TgDefaultColors.avatarColors,
)

/** 可选「蓝色渐变」主题：本任务 iOS 版规格，保留作为第二套浅色方案。 */
fun lightBlueTgColors(): TgColors = lightTgColors().copy(
    outBubbleStart = TgIosColors.outBubbleStart,           // #57B2E0
    outBubbleEnd = TgIosColors.outBubbleEnd,               // #0088FF
    outBubbleText = TgIosColors.outText,
    outMeta = TgIosColors.outMeta,
    inBubble = TgIosColors.inBubble,
    time = TgIosColors.timeGray,
)

/** 暗色主题：TG Night/Dark 近似（本轮精读未给出精确暗色 key，按 ThemeColors.java 结构映射）。 */
fun darkTgColors(): TgColors = TgColors(
    actionBar = Color(0xFF232E3C),
    actionBarTitle = Color(0xFFF2F2F2),
    background = Color(0xFF17212B),
    surface = Color(0xFF1E2C3A),
    inBubble = Color(0xFF2B3B4C),
    inBubbleSelected = Color(0xFF33465A),
    inBubbleBorder = Color(0xFF33465A),
    inBubbleText = Color(0xFFE9EDF2),
    outBubbleStart = Color(0xFF3B5A46),
    outBubbleEnd = Color(0xFF3B5A46),
    outBubbleSelected = Color(0xFF47684F),
    outBubbleText = Color(0xFFE9EDF2),
    link = Color(0xFF6AB0F2),
    inMeta = Color(0xFF8B9AAB),
    outMeta = Color(0xFF9CCB85),
    divider = Color(0xFF2E3B49),
    name = Color(0xFFE9EDF2),
    subtitle = Color(0xFF8B9AAB),
    time = Color(0xFF8B9AAB),
    unreadBadge = Color(0xFF229AF0),
    unreadBadgeMuted = Color(0xFF5A6875),
    unreadText = Color(0xFFFFFFFF),
    pinnedIcon = Color(0xFF9AA7B4),
    sentCheck = Color(0xFF6ABF5A),
    draft = Color(0xFFEF6B6B),
    primary = Color(0xFF3390EC),
    composerBackground = Color(0xCC1E2C3A),
    composerBorder = Color(0xFF33465A),
    dateChip = Color(0x80546A8A),
    dateChipText = Color(0xFFFFFFFF),
    replyBar = Color(0xFF263746),
    icon = Color(0xFF9AA7B4),
    error = Color(0xFFEF6B6B),
    avatarPalette = TgDefaultColors.avatarColors,
)

val LocalTgColors = staticCompositionLocalOf { lightTgColors() }

/** 便捷取值：TgTheme.colors.xxx（对应 TG Theme.getColor(key_*) 的调用方式）。 */
object TgTheme {
    val colors: TgColors
        @Composable get() = LocalTgColors.current
}
