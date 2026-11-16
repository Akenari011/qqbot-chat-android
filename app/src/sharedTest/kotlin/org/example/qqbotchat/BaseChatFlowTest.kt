package org.example.qqbotchat

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import org.junit.Rule
import org.junit.Test


abstract class BaseChatFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun enterDemo() {
        rule.onNodeWithText("演示模式（无需凭证）").performScrollTo().performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("QQ 官方助手").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun draftsStayInTheirOwnConversationAndSendingClearsOnlyCurrentDraft() {
        enterDemo()
        rule.onNodeWithText("QQ 官方助手").performClick()
        rule.onNode(hasSetTextAction()).performTextInput("first draft")
        rule.onNodeWithContentDescription("返回").performClick()
        rule.onNodeWithText("草稿：first draft").assertIsDisplayed()
        rule.onNodeWithText("Android 开发交流群").performClick()
        rule.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        rule.onNode(hasSetTextAction()).performTextInput("second draft")
        rule.onNodeWithContentDescription("返回").performClick()
        rule.onNodeWithText("QQ 官方助手").performClick()
        rule.onNode(hasSetTextAction()).assertTextEquals("first draft")
        rule.onNodeWithContentDescription("发送").performClick()
        rule.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        rule.onNodeWithText("first draft").assertIsDisplayed()
        rule.onNodeWithContentDescription("返回").performClick()
        rule.onNodeWithText("草稿：second draft").assertIsDisplayed()
    }

    @Test fun archivedConversationIsReachableAndSearchable() {
        enterDemo()
        rule.onNodeWithText("QQ 官方助手").performTouchInput { longClick() }
        rule.onNodeWithText("归档", useUnmergedTree = true).performClick()
        rule.onNodeWithText("1 个会话").performClick()
        rule.onNodeWithText("QQ 官方助手").assertIsDisplayed()
        rule.onNodeWithContentDescription("搜索").performClick()
        rule.onNode(hasSetTextAction()).performTextInput("欢迎")
        rule.onNodeWithText("QQ 官方助手").assertIsDisplayed()
        rule.onNodeWithContentDescription("关闭").performClick()
        rule.onNodeWithText("QQ 官方助手").performClick()
        rule.onNodeWithContentDescription("返回").assertIsDisplayed()
    }

    @Test fun forwardingRequiresTargetAndExplicitConfirmation() {
        enterDemo()
        rule.onNodeWithText("Android 开发交流群").performClick()
        rule.onNodeWithText("LazyColumn 的 key 一定要加").performTouchInput { longClick() }
        rule.onNode(hasText("转发") and hasClickAction()).performClick()
        rule.onNodeWithText("转发给…").assertIsDisplayed()
        rule.onNodeWithText("QQ 官方助手").performClick()
        rule.onNodeWithText("将这条文本消息转发给“QQ 官方助手”？").assertIsDisplayed()
        rule.onNode(hasText("转发") and hasClickAction()).performClick()
        rule.onNodeWithContentDescription("返回").performClick()
        rule.onNodeWithText("QQ 官方助手").performClick()
        rule.onNodeWithText("LazyColumn 的 key 一定要加").assertIsDisplayed()
    }

    @Test fun emojiPanelAndReplyCanBeDismissedWithoutLosingText() {
        enterDemo()
        rule.onNodeWithText("Android 开发交流群").performClick()
        rule.onNodeWithText("LazyColumn 的 key 一定要加").performTouchInput { longClick() }
        rule.onNodeWithText("回复").performClick()
        rule.onNodeWithContentDescription("取消回复").assertIsDisplayed()
        rule.onNodeWithContentDescription("表情").performClick()
        rule.onNodeWithText("😀").performClick()
        rule.onNode(hasSetTextAction()).assertTextEquals("😀")
        rule.onNodeWithContentDescription("取消回复").performClick()
        rule.onNode(hasSetTextAction()).assertTextEquals("😀")
    }
}
