# 第三方组件与许可声明

本项目原创代码采用 **MIT License**（见 LICENSE）。以下内容涉及第三方版权或商标。

## 1. Telegram for Android（GPL-2.0）

- 上游仓库：https://github.com/DrKLO/Telegram
- 许可：**GNU General Public License v2.0**
- 引用的提交：7be672ead283d022812d75c729d3a04dd4c2a2c6（13.2.1 / 7291，2026-11-07）

### 派生内容清单

| 本项目文件 | 派生自 | 性质 |
| --- | --- | --- |
| app/src/main/kotlin/org/example/qqbotchat/ui/tg/TgBubblePath.kt | MessageDrawable.java:704-832 generatePath() | 几何算法翻译为 Kotlin |
| app/src/main/kotlin/org/example/qqbotchat/ui/tg/TgBadge.kt | DialogCell.java:1275-1284,5386 | 角标常量与绘制 |
| app/src/main/res/values/tg_colors.xml | ThemeColors.java:20 createDefaultColors() | 色值常量 |
| app/src/main/res/values/tg_dimens.xml | MessageDrawable / ChatMessageCell / DialogCell / Theme | 尺寸常量 |
| app/src/main/res/values/tg_style_dims.xml、tg_values.xml、tg_integers.xml、tg_attrs.xml、tg_ids.xml | res/values/*.xml | 原样归档资源 |

各派生文件头部均保留来源标注（上游路径 + 提交号 + 原始行号）。

### GPL-2.0 传染性说明

上述派生内容受 GPL-2.0 约束。若将本项目**闭源分发**，需自行确认合规路径；
若公开分发包含上述派生素材的二进制，则应按 GPL-2.0 提供对应源码。

## 2. QQ / 腾讯

- QQ 机器人 API 文档：https://bot.q.qq.com/wiki/
- 「QQ」「腾讯」是腾讯公司的商标。
- 本项目为**第三方独立客户端**，与腾讯公司无任何关联、未获其背书。
- 使用本项目需自行申请 QQ 机器人 AppID 与 AppSecret，并遵守腾讯的开发者协议。

## 3. 开源依赖

| 依赖 | 许可 |
| --- | --- |
| Kotlin / kotlinx-coroutines | Apache-2.0 |
| AndroidX（Compose、Lifecycle、Navigation、Activity） | Apache-2.0 |
| OkHttp / Okio | Apache-2.0 |
| Gson | Apache-2.0 |
| Coil | Apache-2.0 |
| JUnit 4 | Eclipse Public License 1.0 |

## 4. 图标

app/src/main/res/drawable/ic_*.xml 为本项目自绘的矢量图标，采用 MIT License。

## 5. 免责声明

本项目为**预览版**，按「现状」提供，不附带任何明示或暗示担保。
使用本项目造成的任何数据丢失、账号风险或其他损失，作者不承担责任。
请勿用于生产环境或重要沟通场景。
