package org.example.qqbotchat

import android.graphics.Bitmap
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.example.qqbotchat.vm.ChatViewModel
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Runs the same critical flows on a real Android runtime, plus exportable visual evidence. */
@RunWith(AndroidJUnit4::class)
class ChatDeviceTest : BaseChatFlowTest() {
    @Test fun captureLightDarkAndSettings() {
        rule.runOnUiThread {
            rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            ViewModelProvider(rule.activity)[ChatViewModel::class.java].apply {
                setDarkTheme(false); setBlueGradient(false); setFontScale(1f)
            }
        }
        rule.waitUntil(10_000) {
            rule.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        }
        rule.onNodeWithText("演示模式（无需凭证）").performScrollTo().performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("QQ 官方助手").fetchSemanticsNodes().isNotEmpty() }
        capture("conversations")
        rule.onNodeWithText("Android 开发交流群").performClick()
        rule.onNodeWithText("LazyColumn 的 key 一定要加").assertIsDisplayed()
        capture("chat-light")
        rule.runOnUiThread { ViewModelProvider(rule.activity)[ChatViewModel::class.java].setDarkTheme(true) }
        capture("chat-dark")
        rule.onNodeWithContentDescription("返回").performClick()
        rule.onNodeWithContentDescription("打开菜单").performClick()
        rule.onNodeWithText("设置").performClick()
        rule.onNodeWithText("聊天外观").assertIsDisplayed()
        capture("settings-dark")
        rule.runOnUiThread { ViewModelProvider(rule.activity)[ChatViewModel::class.java].setDarkTheme(false) }
    }

    private fun capture(name: String) {
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val output = File(rule.activity.getExternalFilesDir(null), "qa/$name.png")
        output.parentFile!!.mkdirs()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
