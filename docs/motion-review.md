# Motion review

## Verdict

**Approve**

Method: dual-agent (A: `/root/motion_judgment` · B: `/root/motion_rules`). Both assessments began with isolated context. Assessment A did not read rule findings. The final review below records the follow-up fixes; no blocking finding remains.

## Judgment

# Assessment A — independent motion judgment

Date: 2026-11-16

Verdict: **Approve**. The restrained motion direction is appropriate for a frequently used chat interface. All three original blockers are resolved in the inspected source, including cancellation recovery in the shared sheet wrapper. This is an independent motion judgment, not a release frame-time certification.

This assessment was formed independently from MOTION.md and the requested implementations. No skill standards, other review, or unrelated architecture was read. This follow-up inspected only the three earlier blockers and their directly supporting implementation. Source files were not edited and no UI or tests were run by this assessor.

## Motion purpose and repeated use

The assumed usage is a frequently used chat interface: tens to hundreds of typing, sending, and navigation interactions per day, with search, reply, emoji, and contextual menus used repeatedly within a session. Short transitions belong where they explain a change of place or state. The 240 ms hierarchical navigation, 180 ms local disclosure and placement tier, stable list keys, and lack of directional page transitions for top-level filters fit that frequency. Typing does not receive a per-character animation, and the composer sends through its callback without waiting for a transition.

The swipe return is now a documented direct-manipulation exception to the disclosure duration. A critically damped spring with release velocity is a reasonable choice: it can settle without deliberate bounce and continue from the current physical position. No decorative looping motion is needed here.

## Recheck of the three original blockers

| Original issue | Result | Source evidence |
| --- | --- | --- |
| Reply preview disappeared before its exit animation | Resolved | `ChatComposer.kt:45–49` uses `AnimatedContent(replyingTo)`, renders the transition's `reply` parameter, and applies matching 180 ms fades and size transformation. The outgoing reply is retained by the transition instead of being removed by the current null value. |
| Selecting a bottom-sheet action removed the sheet immediately | Resolved | `TgActionSheet.kt:17–26` retains the sheet through `state.hide()`, dispatches only after it is hidden, and restores the selection latch in `finally`. `MessageActions.kt:165,180`, `ConversationActionSheet.kt:95–105`, and `ConversationPicker.kt:32` route selections through that wrapper. |
| Re-grabbing a returning swipe jumped to zero | Resolved | `ChatScreen.kt:267–282` uses one remembered `Animatable`; a new drag stops the current return, accumulates deltas from the displayed value, and the return receives release velocity. `Motion.kt:13` supplies a critically damped spring. `MOTION.md:8` explicitly records this exception. |

The sheet fix also handles interruption at the source level: a cancelled hide does not fall through to the action, while `finally` restores `closing = false` so a sheet that remains visible can accept another selection. The `!state.isVisible` check prevents dispatch if hide returns without reaching the hidden state. The previous immediate-removal problem and the follow-up latch-recovery concern are both closed. The interrupted gesture sequence was not run by this assessor.

## Coherence and disabled animations

The reply preview's fade and size change now describe one event rather than a disappearing label followed by empty motion. A menu selection normally hides the outgoing sheet before presenting a reply preview, another picker, or a confirmation dialog; this sequencing matches `MOTION.md:10`. That adds the Material dismissal interval to menu selections, while the main typing and sending controls remain immediate. It is a reasonable tradeoff for avoiding competing modal surfaces. The cancellation cleanup preserves the ability to continue using a sheet after its dismissal is interrupted.

The inspected UI states retain meaning without animation: text identifies searches, replies, recording time, and errors; icons and content descriptions identify the composer action; selected tabs remain selected. The swipe still has a direct position and a threshold affordance, and reply remains available from the long-press menu. No reviewed operation relies on watching an animation to understand its result. Source inspection is not a substitute for observing Android animator duration scale 0 on a device.

The original observation that emoji disclosure and system IME dismissal can run together remains an observation limit, not a newly added blocker: their combined height change was not visually assessed here. This follow-up did not broaden into that behavior.

## Validation boundary

The coordinating agent reports that source checks and automated UI tests pass. This assessor did not independently run or inspect those results; the final cancellation-cleanup revision was verified here by source inspection only. This report does not claim automated coverage of interrupted sheet dismissal. No release frame-time measurement, jank trace, or device performance measurement was collected. Debug/test success cannot establish release animation smoothness.

Recommendation: accept these motion fixes within the reviewed scope. The choices fit the stated chat usage and do not call for additional decorative animation or a broader redesign. No blocking source-level motion issue remains among the three rechecked items.

## Findings

# 动效规则发现复查

复查日期：2026-11-16。范围仅限上一轮 Assessment B 的 5 项发现；依据当前源码及 `MOTION.md`，未扩大评审范围。下列行号均从本次编号源码读取。

结果：**4 项 resolved，1 项 unresolved**。唯一保留项为 T-008（taste / minor）。原 floor 发现已解决；这不是对整个应用动效的重新评审结论。

所有 Kotlin 路径均相对于 `app/src/main/kotlin/org/example/qqbotchat/`。

| 原发现 | 状态 | 规则 / 类别 / 严重度 | 当前源码证据（实际行范围） | 已落实的修复或剩余修复 |
|---|---|---|---|---|
| 消息滑动回复丢失松手速度 | resolved | F-002 / floor / major（原严重度） | `ui/screens/ChatScreen.kt:267–282`；`ui/theme/Motion.kt:13` | 已改为按消息 ID 记忆的 `Animatable(0f)`；拖动通过 `snapTo` 跟手，重新拖动调用 `stop()`，`startDragImmediately = translation.isRunning` 支持接管回弹；松手使用 `animateTo(0f, TgMotion.replyReturn(), initialVelocity = velocity)`，回弹 token 为临界阻尼 spring。位移仍在 `graphicsLayer {}` 内读取。 |
| 搜索与表情面板的系统返回不能跟随手势预览 | unresolved | T-008 / taste / minor | `ui/screens/ChatScreen.kt:137–143`；面板过渡在 `ui/screens/ChatScreen.kt:169–170`、`218–219` | 仍由普通 `BackHandler` 在返回提交时直接修改 `searchOpen` / `emojiOpen`，未使用手势进度。具体修复：把录音取消与面板返回分开，为面板接入 `PredictiveBackHandler` 与 `SeekableTransitionState`，支持取消手势恢复原状态及提交后关闭。项目未建立自定义面板预测返回惯例，保留 minor。 |
| 返回导航的位移与透明度时长不一致 | resolved | T-020 / taste / major（原严重度） | `ui/nav/AppNavigation.kt:49`；`ui/theme/Motion.kt:9–11` | `popExitTransition` 中 `slideOutHorizontally` 与 `fadeOut` 均使用 `TgMotion.navigation()`，统一为 240 ms，符合 `MOTION.md:5`。 |
| 会话操作或取消时底部面板被立即移除 | resolved | T-024 / taste / major（原严重度） | `ui/components/ConversationActionSheet.kt:63`、`95–105`；`ui/components/TgActionSheet.kt:13–23`；父级 `ui/screens/ConversationListScreen.kt:177–183` | 操作与取消统一经过 `TgActionSheet` 的 `close`；共享容器先 `state.hide()`，完成后才调用操作回调，因此父级清空 `actionTarget` 前保留面板退场。此顺序与当前 `MOTION.md:10` 的面板选择约定一致。 |
| 选择转发接收方时选择面板被立即替换 | resolved | T-024 / taste / major（原严重度） | `ui/components/ConversationPicker.kt:22–32`；`ui/components/TgActionSheet.kt:17–23`；父级 `ui/screens/ChatScreen.kt:248–256` | 选择项已改为 `close { onSelect(conversation) }`；`TgActionSheet` 完成 `hide()` 后才触发 `onSelect`，此后父级更新 `forwardTarget` 并显示确认框，原选择面板不再于退场开始前移除。 |

本次验证为源码复查，未运行构建或设备测试，也未据此声称实际设备上的手势流畅度或帧耗时已验证。未解决的 T-008 仍需开发及设备手势验证。

## Floor / taste split

Unresolved floor: **0**. Unresolved obligation: **0**. Unresolved taste: **1 minor** (T-008).

Original rule findings: floor 1, taste 4; the floor finding and three major taste findings are resolved. The remaining local-panel predictive-back preview does not contradict a DECIDED entry and does not block this change.

## Integration verification

The final code passed core and local UI tests, Debug / Release builds and Android instrumentation. The 5 Android tests also passed with animator, transition and window animation scales set to 0. These tests cover final state and interaction outcomes, not frame-time performance, every interruptible gesture sequence or all IME combinations. Screenshots were visually inspected in portrait; the same flow suite also ran in landscape before portrait captures were added.
