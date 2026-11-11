// 设计参考 Telegram for Android (GPL-2.0)
// Derived from DrKLO/Telegram (GPL-2.0), commit 7be672ea..., DialogCell.java:1275-1284, 5386.
// 抄取 DialogCell 的 BADGE_* 常量与圆角矩形绘制（未读角标 / 提及角标）。
//
// 【逐条源码出处：文件:行号】
//   · DialogCell.java:1275-1284,5386  BADGE 高 20.666dp、圆角 11.5dp、文字 12sp
//   · third_party/telegram-android/res/values/styles.xml:239/:240  水平内边距 7dp
//   · third_party/telegram-android/res/values/styles.xml:238       角标高 23dp（AppWidget Badge，保留作对照）
//   · third_party/telegram-android/res/values/styles.xml:244       minWidth 23dp（同上）
//   · third_party/telegram-android/res/values/styles.xml:245       字号 13dp（同上）
package org.example.qqbotchat.ui.tg

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/** DialogCell.java 的 BADGE_* 常量（dp/sp，绘制时转 px）。 */
object TgBadge {
    const val BADGE_HEIGHT_DP = 20.666f        // DialogCell.java:1275-1284
    const val BADGE_RADIUS_DP = 11.5f          // DialogCell.java:1275-1284
    const val BADGE_TEXT_SIZE_SP = 12f         // DialogCell.java:5386
    const val BADGE_PADDING_DP = 7f            // styles.xml:239-240
    const val BADGE_MIN_WIDTH_DP = 20.666f     // 与高度一致，形成胶囊

    /** 生成 TG 角标的圆角矩形 Path（等价 DialogCell 的 counterPath）。 */
    fun buildBadgePath(width: Float, height: Float, radius: Float): Path {
        val path = Path()
        path.addRoundRect(
            RectF(0f, 0f, width, height),
            radius,
            radius,
            Path.Direction.CW,
        )
        return path
    }

    /** 用 Canvas.drawRoundRect 直接绘制角标背景（等价 DialogCell 的 drawCounter）。 */
    fun drawBadge(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        radius: Float,
        paint: Paint,
    ) {
        canvas.drawRoundRect(RectF(left, top, left + width, top + height), radius, radius, paint)
    }
}
