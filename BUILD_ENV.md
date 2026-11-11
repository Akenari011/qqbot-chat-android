# 构建与测试环境（服务器本地，不走 GitHub Actions）

## 主机

| 角色 | 主机 | 配置 | 用途 |
| --- | --- | --- | --- |
| 编译机 | 春川 a1-chuncheon-24c 158.180.80.69 | aarch64 / 24C / 141GB / Ubuntu 22.04 | Gradle 编译、JVM 单元测试、APK 打包 |
| 测试机 | instabox m_agq4n6zhqjd6uyxyv4hlecu4f4 | x86_64 / 4C / 8GB / Android 模板 | adb 安装到 emulator-5554、UI 验证 |

SSH（编译机）：ssh -i ~/.ssh/id_ed25519 ubuntu@158.180.80.69，sudo 免密。

## 工具链（编译机 ~/android-build/）

| 组件 | 路径 | 版本 |
| --- | --- | --- |
| JDK | ~/android-build/jdk | Temurin 17.0.21 aarch64 |
| Android SDK | ~/android-build/sdk | platform android-35 / build-tools 35.0.0 + 34.0.0 / platform-tools |
| Gradle | ~/android-build/gradle/gradle-8.9/bin/gradle | 8.9 |
| x86 sysroot | ~/android-build/x86sysroot | amd64 libc6/libgcc-s1/libstdc++6/zlib 解压 |
| qemu | /usr/bin/qemu-x86_64-static | binfmt_misc 已注册 |

### arm64 上跑 Android 构建的关键

AGP 的 aapt2 / zipalign / aidl 只发布 x86_64 ELF，arm64 直接执行会 Exec format error。
解决：qemu-user-static + binfmt_misc 透明执行，并用 amd64 运行时库建 x86sysroot。

编译时必须导出：

    export JAVA_HOME=$HOME/android-build/jdk
    export ANDROID_HOME=$HOME/android-build/sdk
    export ANDROID_SDK_ROOT=$ANDROID_HOME
    export QEMU_LD_PREFIX=$HOME/android-build/x86sysroot

## 项目

本地：/home/ubuntu/work/qqbot-chat-android/
远程：~/android-build/QQBotChat/（用 scp 同步）

模块：

- :core 纯 Kotlin JVM 库（模型、解析、API、WebSocket、存储），可跑 JVM 单元测试
- :app Android 应用（Compose + Material 3）

## 常用命令

    scp -i ~/.ssh/id_ed25519 -r core app settings.gradle.kts build.gradle.kts gradle.properties ubuntu@158.180.80.69:~/android-build/QQBotChat/
    gradle :core:test --no-daemon --rerun-tasks
    gradle :app:assembleDebug --no-daemon

产物：app/build/outputs/apk/debug/app-debug.apk

## UI 复用说明

界面设计参考 Telegram for Android（DrKLO/Telegram，GPL-2.0）：色值、圆角、内边距、字号、行高、布局层级按 TG 的 Theme / ChatMessageCell / DialogCell 常量实现。TG 的 UI 是 Java 自定义 View + XML，深度耦合 MTProto / MessageObject / TLRPC，无法作为独立库嵌入，故采用「复用设计常量 + Kotlin/Compose 独立实现」。

## 注意

- 春川大机是临时实例，凭据库标注 2026-11-27 02:00 自动删除/降配，勿长期依赖。
- instabox 测试机单次运行上限 120 分钟、空闲 30 分钟挂起，用完即释放。
- 不要把凭据写进仓库 / 日志 / 文档。

## 正式签名（2026-11-11 起）

Release 包已改用自有 keystore（不再用 debug 证书）：

- keystore：`~/android-build/qqbot-release.jks`（alias `qqbot`，RSA 2048，有效期 10000 天，CN=QQBotChat）
- 密码文件：`~/android-build/QQBotChat/signing.properties`（服务器本地，**不进仓库**，`.gitignore` 已排除 `signing.properties` / `*.jks` / `*.apk`）
- `app/build.gradle.kts` 在 `android {}` 前读取 `rootProject.file("signing.properties")`：存在且 keystore 有效 → 用 `signingConfigs.release`；否则回退 `debug` 签名（保证任何机器都能产出可安装 APK）
- 验证：`apksigner verify --verbose app-release.apk` → v2 scheme true、1 signer；`--print-certs` 可看证书 DN 与 SHA-256 指纹
- 注意：正式签名包与 debug 签名包证书不同，模拟器换包安装需先 `adb uninstall org.example.qqbotchat`

## 演示模式

登录页「演示模式（无需凭证）」调用 `ChatViewModel.enterDemoMode()` 直接进入本地演示数据，
用于无凭证 / 无网络环境的 UI 预览。真实「连接」按钮会先调 QQ `/app/getAppAccessToken` 校验凭证，
无效凭证拦在登录页并显示「AppID 或 AppSecret 无效」。
