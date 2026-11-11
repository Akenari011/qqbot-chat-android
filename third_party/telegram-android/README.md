# Telegram for Android 资源引用

本项目 UI 设计参考 Telegram for Android 客户端。

- 上游仓库：https://github.com/DrKLO/Telegram
- 上游许可：GPL-2.0（完整应用源码，Java + XML 自定义 View + AppCompat）
- 取用方式：按用户授权复用，此处归档其 res/values/ 原始文件作为设计常量的核对依据

## 已归档文件

| 文件 | 用途 |
| --- | --- |
| res/values/colors.xml | divider #dcdcdc、widget_name #212121、widget_time #96999C、widget_text #8B8D8F |
| res/values/styles.xml | Theme.TMessages / ActionBar 样式、#ff527da3 经典蓝 |
| res/values/attrs.xml | 自定义属性声明 |
| res/values/values.xml | 布尔与圆角常量 |
| res/values/integers.xml | 整数常量 |
| res/values/ids.xml | 控件 id 定义 |

## 复用边界

TG Android 的 UI 主要由 Java 自定义 View 代码绘制（ChatMessageCell / DialogCell / ActionBar），
这些类深度依赖 MessageObject、TLRPC、MessagesController、Theme 静态资源管理器与 MTProto 网络层，
无法作为独立 UI 库嵌入本项目的 Kotlin + Compose 工程。

因此本项目采用：

1. 直接复用资源层：上表 XML 文件原样归档，色值与尺寸逐项搬入 app 的 ui/theme/Color.kt 与 Dimens.kt，并注明来源。
2. 按 TG 常量独立实现 UI：Compose 声明式重写气泡、会话行、输入栏，严格对齐圆角 16dp、
   内边距 11/6dp、宽上限 0.85、头像 60dp、字号 16/15/14/11/13sp、分隔线 86dp 缩进等。

## 许可提示

GPL-2.0 具有传染性：若后续把 TG 的 Java/XML 代码原样编译进本应用，则本应用整体须以
GPL-2.0 发布并公开全部源码。当前做法仅复用设计常量与资源文件，未编译 TG 的 Java 类。
如需进一步直接搬入其 View 代码，请先确认发布许可策略。
