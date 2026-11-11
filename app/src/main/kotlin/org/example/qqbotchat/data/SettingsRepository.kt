// 设计参考 Telegram for Android (GPL-2.0)
// 设置持久化：对应 TG 的 SharedConfig 偏好存储。
// TG 的 SharedConfig.java 用 SharedPreferences 保存大量 UI 偏好，例如
//   preferences.getInt("bubbleRadius", 17)
//   preferences.getBoolean("darkTheme", false)
// 本客户端沿用同样的「轻量键值 + 默认值兜底」做法，只存 3 个设置项：
//   深色主题 / 蓝色渐变气泡 / 全局字体缩放。
//
// 设计要点：
//   - 用 applicationContext 取 SharedPreferences，不持有 Activity 引用，避免内存泄漏；
//   - 字体缩放的合法区间与 ViewModel 的 coerceIn(0.85f, 1.30f) 保持一致，
//     写入前 clamp，读取时越界 / 缺失一律回落到 1f，防止旧版本或损坏值把 UI 撑坏；
//   - SharedPreferences 理论上不抛异常，但所有读写仍包 try/catch 做防御性处理：
//     读失败返回默认值、写失败静默忽略（持久化失败不应影响设置页交互）。
package org.example.qqbotchat.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 设置项的本地持久化（SharedPreferences）。
 *
 * @param context 应用上下文（内部只保存 applicationContext 派生的 SharedPreferences，不持有 Activity）。
 */
class SettingsRepository(context: Context) {

    // applicationContext 保证生命周期与应用一致；这里只保存 SharedPreferences 引用。
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /** 读取深色主题开关，缺失或读取失败返回默认关闭。 */
    fun loadDarkTheme(): Boolean = try {
        prefs.getBoolean(KEY_DARK_THEME, false)
    } catch (e: Exception) {
        false
    }

    /** 读取「蓝色渐变气泡」开关，缺失或读取失败返回默认关闭。 */
    fun loadBlueGradient(): Boolean = try {
        prefs.getBoolean(KEY_BLUE_GRADIENT, false)
    } catch (e: Exception) {
        false
    }

    /**
     * 读取全局字体缩放。
     *
     * 缺失、非有限值（NaN / Infinity）或超出 [MIN_FONT_SCALE]..[MAX_FONT_SCALE] 时返回 1f；
     * 区间内则 clamp 后返回（与 ViewModel 的 coerceIn 语义一致）。
     */
    fun loadFontScale(): Float = try {
        val stored = prefs.getFloat(KEY_FONT_SCALE, DEFAULT_FONT_SCALE)
        if (!stored.isFinite() || stored < MIN_FONT_SCALE || stored > MAX_FONT_SCALE) {
            DEFAULT_FONT_SCALE
        } else {
            stored.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)
        }
    } catch (e: Exception) {
        DEFAULT_FONT_SCALE
    }

    /** 保存深色主题开关，写入失败静默忽略。 */
    fun saveDarkTheme(value: Boolean) {
        try {
            prefs.edit().putBoolean(KEY_DARK_THEME, value).apply()
        } catch (e: Exception) {
            // 持久化失败不影响当前会话内的设置生效。
        }
    }

    /** 保存「蓝色渐变气泡」开关，写入失败静默忽略。 */
    fun saveBlueGradient(value: Boolean) {
        try {
            prefs.edit().putBoolean(KEY_BLUE_GRADIENT, value).apply()
        } catch (e: Exception) {
            // 同上。
        }
    }

    /** 保存全局字体缩放，写入前 clamp 到 [MIN_FONT_SCALE]..[MAX_FONT_SCALE]。 */
    fun saveFontScale(value: Float) {
        val safe = if (value.isFinite()) value.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE) else DEFAULT_FONT_SCALE
        try {
            prefs.edit().putFloat(KEY_FONT_SCALE, safe).apply()
        } catch (e: Exception) {
            // 同上。
        }
    }

    /** 清空所有设置（退出登录 / 重置时调用），失败静默忽略。 */
    fun clear() {
        try {
            prefs.edit().clear().apply()
        } catch (e: Exception) {
            // 同上。
        }
    }

    companion object {
        /** SharedPreferences 文件名。 */
        private const val PREF_NAME = "qqbot_settings"

        /** 深色主题 key。 */
        private const val KEY_DARK_THEME = "dark_theme"

        /** 蓝色渐变气泡 key。 */
        private const val KEY_BLUE_GRADIENT = "blue_gradient"

        /** 字体缩放 key。 */
        private const val KEY_FONT_SCALE = "font_scale"

        /** 字体缩放下限（与 ViewModel 的 coerceIn 一致），供设置页 UI 复用。 */
        const val MIN_FONT_SCALE = 0.85f

        /** 字体缩放上限（与 ViewModel 的 coerceIn 一致），供设置页 UI 复用。 */
        const val MAX_FONT_SCALE = 1.30f

        /** 字体缩放默认值（基准 16sp）。 */
        const val DEFAULT_FONT_SCALE = 1f
    }
}
