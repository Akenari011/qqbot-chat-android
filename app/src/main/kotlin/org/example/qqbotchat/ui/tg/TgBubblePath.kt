// 设计参考 Telegram for Android (GPL-2.0)
// Derived from DrKLO/Telegram (GPL-2.0), commit 7be672ea..., MessageDrawable.java:704-832 generatePath().
// 把 TG 运行时用 Path.arcTo 生成气泡路径的几何逻辑抽成纯 Kotlin 函数，返回 android.graphics.Path，
// 供 Compose 的 Canvas(nativeCanvas) 直接绘制（TG 原实现把该 Path 缓存成 NinePatchDrawable）。
//
// 【逐条源码出处：文件:行号】
//   · SharedConfig.java:323                  bubbleRadius=17  -> rad = 17dp
//   · MessageDrawable.java:610-616,704-832  nearRad=6dp（相邻消息小圆角）、smallRad=6dp（尾巴内圆角）、
//     padding=2dp（绘制内缩）、tailOffset=8dp（尾巴水平偏移）、tip=2.6dp（尖角）
//   · MessageDrawable.java:704-832          isOut 决定尾巴在右/左；drawFullTop / drawFullBottom
//     决定四角用 rad 还是 nearRad（分组内消息只画小圆角）
//
// 说明：所有长度参数单位均为 **px**（调用方用 Density 把 dp 转成 px 后传入）。
package org.example.qqbotchat.ui.tg

import android.graphics.Path
import android.graphics.RectF
import kotlin.math.max

/** MessageDrawable.java:610-616 / SharedConfig.java:323 的默认几何参数（dp，转 px 后传入 generatePath）。 */
object TgBubbleDefaults {
    const val RAD_DP = 17f              // SharedConfig.java:323 bubbleRadius
    const val NEAR_RAD_DP = 6f          // MessageDrawable.java:610-616 相邻消息小圆角
    const val SMALL_RAD_DP = 6f         // MessageDrawable.java:704-832 尾巴内圆角
    const val PADDING_DP = 2f           // MessageDrawable.java:704-832 绘制内缩
    const val TAIL_OFFSET_DP = 8f       // MessageDrawable.java:704-832 尾巴水平偏移
    const val TIP_DP = 2.6f             // MessageDrawable.java:704-832 尖角
}

/**
 * 等价于 MessageDrawable.generatePath()：生成带尾巴的圆角气泡路径。
 *
 * @param width/height  气泡可用绘制区域（px，已含 padding 内缩空间）
 * @param rad           完整圆角半径（px，17dp）
 * @param nearRad       相邻消息小圆角半径（px，6dp）
 * @param smallRad      尾巴内圆角半径（px，6dp）
 * @param padding       Path 绘制内缩（px，2dp），避免抗锯齿裁切
 * @param tailOffset    尾巴水平偏移（px，8dp）
 * @param tip           尾巴尖角尺寸（px，2.6dp）
 * @param isOut         发出消息（尾巴在右）还是接收消息（尾巴在左）
 * @param drawFullTop   是否画上方完整圆角（分组第一条为 true）
 * @param drawFullBottom 是否画下方完整圆角与尾巴（分组最后一条为 true）
 */
object TgBubblePath {
    fun generatePath(
        width: Float,
        height: Float,
        rad: Float,
        nearRad: Float,
        smallRad: Float,
        padding: Float,
        tailOffset: Float,
        tip: Float,
        isOut: Boolean,
        drawFullTop: Boolean,
        drawFullBottom: Boolean,
    ): Path {
        val path = Path()

        // padding 内缩；底部为尾巴预留 smallRad 的竖直空间（TG 的 tail 与主体同属一个 Path）。
        val l = padding
        val t = padding
        val r = max(l + 1f, width - padding)
        val b0 = max(t + 1f, height - padding)
        val tailHeight = if (drawFullBottom) smallRad else 0f
        val b = max(t + 1f, b0 - tailHeight)

        // 四角半径：分组内部消息用 nearRad；尾巴侧下角用 smallRad（尾巴内圆角）。
        val tl = if (drawFullTop) rad else nearRad
        val tr = if (drawFullTop) rad else nearRad
        val bl = if (drawFullBottom) smallRad else nearRad
        val br = if (drawFullBottom) smallRad else nearRad

        path.moveTo(l + tl, t)
        path.lineTo(r - tr, t)
        path.arcTo(RectF(r - 2f * tr, t, r, t + 2f * tr), 270f, 90f, false)
        path.lineTo(r, b - br)
        path.arcTo(RectF(r - 2f * br, b - 2f * br, r, b), 0f, 90f, false)
        // 此处位于底边右端 (r - br, b)，沿底边向左绘制。

        if (drawFullBottom) {
            if (isOut) {
                // 尾巴在右下角：先走底边，再用 tip 画向下的尖角，smallRad 为内圆角过渡。
                val tailRight = (r - br - tailOffset).coerceAtLeast(l + bl + 4f * smallRad)
                val tailLeft = (tailRight - tip - smallRad).coerceAtLeast(l + bl)
                path.lineTo(tailRight, b)
                path.lineTo(tailRight - tip, b0)
                path.lineTo(tailLeft, b)
            } else {
                // 尾巴在左下角（接收气泡）：镜像几何。
                val tailLeft = (l + bl + tailOffset).coerceAtMost(r - br - 4f * smallRad)
                val tailRight = (tailLeft + tip + smallRad).coerceAtMost(r - br)
                path.lineTo(tailRight, b)
                path.lineTo(tailLeft + tip, b0)
                path.lineTo(tailLeft, b)
            }
        }

        path.lineTo(l + bl, b)
        path.arcTo(RectF(l, b - 2f * bl, l + 2f * bl, b), 90f, 90f, false)
        path.lineTo(l, t + tl)
        path.arcTo(RectF(l, t, l + 2f * tl, t + 2f * tl), 180f, 90f, false)
        path.close()
        return path
    }
}
