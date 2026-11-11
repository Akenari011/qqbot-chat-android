# Telegram for Android UI 权威常量表

来源：https://github.com/DrKLO/Telegram ，提交 7be672ead283d022812d75c729d3a04dd4c2a2c6（13.2.1 / 7291，2026-11-07）。
许可：GPL-2.0。本项目按用户授权复用，所有派生文件须保留来源标注。路径前缀均为 TMessagesProj/src/main/。

## 1. 气泡几何（MessageDrawable.java）

气泡没有 XML shape，完全由 java/org/telegram/ui/ActionBar/MessageDrawable.java:704-832 的 generatePath() 用 Path.arcTo 运行时生成，再缓存为 NinePatchDrawable（:396-432）。

| 项 | 值 | 位置 |
| --- | --- | --- |
| 默认圆角 | SharedConfig.bubbleRadius = 17 dp | SharedConfig.java:323,617 |
| 实际圆角 rad | dp(17) | MessageDrawable.java:613,676 |
| 相邻小圆角 nearRad | dp(min(6,17)) = 6dp | :614,677 |
| 尾巴内圆角 smallRad | dp(6) | :616,679 |
| 绘制内缩 padding | dp(2) | :600,663 |
| 尾巴水平偏移 | dp(8) | :715,723,738-739,766-767,778,786,799-800,823-824 |
| 尖角起点 | dp(2.6) | :717,780 |
| 类型 | TYPE_TEXT=0, TYPE_MEDIA=1, TYPE_PREVIEW=2 | :88-90 |
| 8 个实例 | new MessageDrawable(TYPE_TEXT/TYPE_MEDIA, in/out, selected) | Theme.java:8271-8278 |
| 取色 | getColor(isOut ? key_chat_outBubble : key_chat_inBubble) | :387,223-229 |
| 渐变 | key_chat_outBubbleGradient1/2/3，gradientColor1 != 0 才启用 | :224-227,235-236,290-299 |
| 阴影 bitmap | 50x40dp，LinearGradient(0x155F6569 -> 0x295F6569) | :396,446,499,406,504 |
| 绘制入口 | ChatMessageCell.drawBackground(...) | ChatMessageCell.java:19120-19159 |

## 2. 默认浅色主题色值（ThemeColors.java:20 createDefaultColors()）

| 键 | 值 | 行 |
| --- | --- | --- |
| TELEGRAM_COLOR | 0xFF229AF0 | :14 |
| TELEGRAM_COLOR_TEXT | 0xFF298ACF | :15 |
| DEFAULT_BLACK_TEXT | 0xFF1A1D21 | :16 |
| chat_inBubble | 0xFFFFFFFF | :307 |
| chat_inBubbleSelected | 0xFFECF7FD | :308 |
| chat_inBubbleShadow | 0xFF1D3753 | :309 |
| chat_outBubble | 0xFFEFFFDE | :350 |
| chat_outBubbleSelected | 0xFFD9F7C5 | :352 |
| chat_outBubbleShadow | 0xFF1E750C | :353 |
| chat_outBubbleGradient | 默认 0（无渐变，纯色） | :1269-1271 |
| chat_messageTextIn / Out | 0xFF000000 | :358,359 |
| chat_messageLinkIn / Out | 0xFF2678B6 | :360,361 |
| chat_inTimeText | 0xFFA1AAB3 | :441 |
| chat_outTimeText | 0xFF70B15C | :443 |
| chat_mediaTimeText | 0xFFFFFFFF | :436 |
| chat_inFileNameText | 0xFF298ACF | :473 |
| chat_outFileNameText | 0xFF55AB4F | :474 |
| chat_inFileBackground / out | 0xFFEBF0F5 / 0xFFDAF5C3 | :479,480 |
| chat_inReplyNameText / out | 0xFF298ACF / 0xFF55AB4F | :411,412 |
| divider（主题） | 0xFFD9D9D9 | :136 |
| divider（res） | #DCDCDC | res/values/colors.xml:3 |
| actionBarDefault | 0xFFFFFFFF | :192 |
| actionBarDefaultTitle / Icon | 0xFF1A1D21 | :197,193 |
| actionBarDefaultSubtitle | 0xFF79817E | :198 |
| windowBackgroundWhite | 0xFFFFFFFF | :79 |
| chats_name | 0xFF1A1D21 | :237 |
| chats_message | 0xFF75787A | :242 |
| chats_date | 0xFF848688 | :252 |
| chats_unreadCounter | 0xFF229AF0 | :230 |
| chats_unreadCounterMuted | 0xFFBEC3C7 | :231 |
| chats_unreadCounterText | 0xFFFFFFFF | :232 |
| chats_pinnedIcon | 0xFF919294 | :241 |
| chats_muteIcon | 0xFFBDC1C4 | :263 |
| chats_sentCheck / sentReadCheck | 0xFF46AA36 | :256,257 |
| chats_draft | 0xFFDD4B39 | :245 |
| avatar_nameInMessage 七色 | 0xFFCC5049 / 0xFFD67722 / 0xFF955CDB / 0xFF40A920 / 0xFF309EBA / 0xFF368AD1 / 0xFFC7508B | :184-190 |
| 启动页经典蓝 | #FF527DA3 | res/values/styles.xml:115 |

注：源码中无 key_chat_timeText、无 key_chat_inFileName（现名 chat_inFileNameText）。

## 3. 字号 / 排版

| 项 | 值 | 位置 |
| --- | --- | --- |
| 正文字号 | SharedConfig.fontSize = 16（手机），平板 18 | SharedConfig.java:321,615 |
| 正文 TextPaint | dp(16) | Theme.java:8133 |
| smallerDp | (2*16+10)/3 = 14dp | Theme.java:8136 |
| 作者名 chat_namePaint | 14dp bold | :8137,8498 |
| 引用名 / 引用文本 | 14dp | :8138,8139 |
| 引用正文 / topic / admin | 13dp | :8140,8143,8146 |
| 时间戳 chat_timePaint | dp(12) | :8506 |
| 文件名 / 按钮 | dp(15) | :8486,8493,8494 |
| 会话名 dialogs_namePaint | dp(17) / dp(16) | DialogCell.java:1298,1303,1312 |
| 会话摘要 dialogs_messagePaint | dp(16) / dp(15) | :1300,1305 |
| 会话时间 / 计数 | dp(12) / dp(14) / dp(12) / dp(13) | Theme.java:7995,7994,7894,7895 |
| 行高 | spacingMult 1.0f，spacingAdd dp(1) | ChatMessageCell.java:5424；DialogCell.java:2838 |
| 最大宽度 | min(parentW, displayH) - dp(80 + 有头像 42) | ChatMessageCell.java:6887 |
| 气泡宽 | 文本宽 + dp(31) | :7203-7213,7241 |
| 气泡左右内边距 | 发出 11dp / 接收 17dp | :13726-13728 |
| 气泡上下内边距 | textY = dp(10) + namesOffset；总高 textHeight + dp(19.5) | :12332,7252,13732 |
| 时间戳 x | 发出 layoutWidth - timeW - dp(38.5)；接收 backgroundW - dp(9) - timeW | :12268,12273 |
| 作者名高度 | dp(5) + 14dp = 19dp（无头像） | :16124 |

## 4. 会话行 DialogCell

| 项 | 值 | 位置 |
| --- | --- | --- |
| 行高 | 70dp（两行）/ 76dp（三行），论坛 86/91dp | DialogCell.java:174-177,1070-1081,1057 |
| 头像 | 52dp（两行）/ 56dp（三行），圆角 26dp | :2518,2495,746,3306 |
| 头像位置 | avatarStart=11dp，top=9dp/11dp | :172,2500,2477 |
| 60dp 真相 | 仅 isPointInsideAvatar() 点击热区，不是头像尺寸 | :3196-3198 |
| 文字起始 | messagePaddingStart=72dp；nameLeft=76dp/78dp | :173,2514,2491 |
| 时间 | top=16/13dp，left=width-15dp-timeW，置顶 +24dp | :2502,2479,2316,2314 |
| 未读角标 | BADGE_SIZE=20.666f, TEXT_PADDING=6.333f, MIN_WIDTH=8f, GAP=17f, MARGIN=15.666f, DRAWABLE_SIZE=16f, OFFSET=2.333f | :1275-1284 |
| 角标圆角 | dp(11.5f)；论坛 dp(10.33f) | :5386,5379 |
| 角标位置 | countTop=38/42.33dp，色 key_chats_unreadCounter | :2505,2482；Theme.java:8031 |
| 置顶图标 | R.drawable.list_pin，left=width-iw-14dp，top=39/43dp，色 #FF919294 | Theme.java:7966,8046；DialogCell.java:2539-2541 |
| 分隔线 | +1px，drawLine，dividerPaint strokeWidth=1，色 key_divider | DialogCell.java:1045,1072-1074,4869-4887；Theme.java:7737,7828 |

## 5. 可直接复用清单

1. res/values/tg_colors.xml —— 上表约 45 个色值，零 Java 依赖
2. res/values/tg_dimens.xml —— 上表尺寸常量
3. TgBubblePath.kt —— 从 MessageDrawable.java:704-832 抽纯几何函数，参数化依赖，产出 android.graphics.Path（约 130 行）
4. TgBubbleDrawable.kt —— 精简自 MessageDrawable，保留 setBounds/draw/setColor/圆角（约 100 行）
5. TgBubbleShadowCache.kt —— 复刻 :396-432 阴影缓存（可选）
6. TgRoundRect.kt —— Theme.java:4759-4791 的 createRoundRectDrawable 系列
7. MessageBackgroundDrawable.kt —— ui/Components/MessageBackgroundDrawable.java 全文 192 行，仅依赖 CubicBezierInterpolator，选中态圆形揭示动画 240ms
8. TgBadge.kt —— DialogCell BADGE_* 常量 + drawCounter 圆角矩形
9. res/drawable/tg_placeholder_in.xml —— 等价 photo_placeholder_in.xml 的 #0fffffff layer-list
10. res/values/tg_styles.xml —— styles.xml:129-134，ListView dividerHeight=1dp

## 6. 必须重写、无法直接复用

| 类 | 规模 | 耦合 |
| --- | --- | --- |
| ChatMessageCell.java | 24,168 行 / 1.35MB / 252 import（159 个 org.telegram.*） | MessageObject、TLRPC、ChatActivity、Theme、ImageReceiver、RadialProgress2、ReactionsLayoutInBubble、StaticLayoutEx、AnimatedEmojiSpan、GiveawayMessageCell、RichMessageLayout、DownloadController |
| DialogCell.java | 6,596 行 / 137 import（81 个 org.telegram.*） | MessageObject、TLRPC、MessagesController、MessagesStorage、Stories、Forum、Theme、DialogsAdapter |
| Theme.java | 10,400 行静态资源管理器 | currentColors/defaultColors SparseIntArray、ResourcesProvider、getColor、drawable 注册表 |
| ThemeColors.java | 1,832 行 | 键名映射 + 900+ 键数组 |
| MessageDrawable.java | 908 行 | Theme.getColor、SharedConfig.bubbleRadius、AndroidUtilities.dp、MotionBackgroundDrawable、NinePatchBuilder、ResourcesProvider |
| MessageObject.java | — | 数据模型 + generateLayout，耦合 TLRPC.TL_message、StaticLayoutEx、RichMessageLayout |

其他：AndroidUtilities.dp() 依赖全局 density；LocaleController 依赖资源；StaticLayoutEx/Emoji/AnimatedEmojiSpan/SpoilerEffect 需换原生实现；ImageReceiver/AvatarDrawable/FileLoader 换 Coil；R.drawable.* 图标需自备。

## 7. 复用成本评估

| 方案 | 连带类 | 工作量 | 建议 |
| --- | --- | --- | --- |
| A. 只搬「数据 + 几何」 | 0 个 | 3~5 人日 | 推荐 |
| B. 搬单个 MessageDrawable 类 | Theme/SharedConfig/AndroidUtilities/MotionBackgroundDrawable/NinePatchBuilder/CubicBezierInterpolator 等约 6 真类 + 4 stub，1100+ 行 | 4~6 人日 | 收益低 |
| C. 整体搬 ChatMessageCell + DialogCell | 去重后约 150~200 个 org.telegram 类，含 tgnet/TLRPC 生成层、控制器层、60+ Drawable、jni、数千 R 资源 | 3 万行以上机械转换，20~40 人日，不可维护 | 不建议 |

## 8. 许可与标注

- 仓库根 LICENSE，GitHub API license.spdx_id = GPL-2.0。
- 被引用的 .java/.xml 文件全部在 GPL-2.0 覆盖内；由它们翻译、改写得到的 Kotlin 类属演绎作品，同样受 GPL-2.0 约束。
- 落地要求：①每个复制/派生文件头保留来源 URL + commit + 原始行号；②NOTICE / THIRD_PARTY_LICENSES 收录 GPL-2.0 全文、原始版权声明、修改说明；③色值与尺寸数值虽属事实性数据，仍逐条标注来源；④若闭源分发须法务确认合规路径。
- 文件头模板：// Derived from DrKLO/Telegram (GPL-2.0), commit 7be672ead283d022812d75c729d3a04dd4c2a2c6, file <path>:<lines>. Modified by <team>, <date>.

## 9. 最小落地顺序

1. 建 tg_colors.xml / tg_dimens.xml（逐条抄，标来源）
2. 写 TgBubblePath.kt（照 :704-832，参数化 17/6/6/2/8/2.6）
3. 写气泡 View：左右 11/17dp、上 10dp、总高 textHeight+19.5dp、最大宽 min(w,h)-dp(80+42)、时间 12sp 贴右下
4. 写会话行：70dp 行高、52dp 头像/26dp 圆角、左 72dp 起、名字 17sp、摘要 16sp、时间 12sp 右 15dp、角标 20.666dp/11.5dp 圆角、分隔线 1px #FFD9D9D9
5. 补 RTL / 分组圆角 / 夜间
6. 加 LICENSE 与 NOTICE
