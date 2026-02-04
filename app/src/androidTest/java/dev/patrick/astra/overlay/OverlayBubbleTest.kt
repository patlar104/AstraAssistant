package dev.patrick.astra.overlay

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import dev.patrick.astra.domain.AssistantPhase
import dev.patrick.astra.domain.AssistantVisualState
import dev.patrick.astra.domain.Emotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OverlayBubbleTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun longPress_showsHudAndTriggersAction() {
        var voiceRequests = 0

        composeRule.setContent {
            OverlayBubble(
                visualState = AssistantVisualState(
                    phase = AssistantPhase.Idle,
                    emotion = Emotion.Neutral
                ),
                onTap = {},
                onLongPress = {},
                onDrag = { _, _ -> },
                onDragEnd = {},
                onRequestVoice = { voiceRequests += 1 },
                onRequestTranslate = {},
                onRequestSettings = {},
                onRequestHide = {},
                overlaySide = OverlaySide.Right,
                hudDismissSignal = 0
            )
        }

        composeRule.onNodeWithTag(OVERLAY_BUBBLE_TAG).performTouchInput {
            longClick()
        }

        composeRule.onNodeWithTag(OVERLAY_HUD_TAG).assertExists()
        composeRule.onNodeWithTag("${OVERLAY_HUD_ACTION_PREFIX}ask").performClick()

        composeRule.runOnIdle {
            assertEquals(1, voiceRequests)
        }

        composeRule.onNodeWithTag(OVERLAY_HUD_TAG).assertDoesNotExist()
    }

    @Test
    fun drag_invokesCallbacks() {
        var dragCalls = 0
        var dragEndCalls = 0

        composeRule.setContent {
            OverlayBubble(
                visualState = AssistantVisualState(
                    phase = AssistantPhase.Idle,
                    emotion = Emotion.Neutral
                ),
                onTap = {},
                onLongPress = {},
                onDrag = { _, _ -> dragCalls += 1 },
                onDragEnd = { dragEndCalls += 1 },
                onRequestVoice = {},
                onRequestTranslate = {},
                onRequestSettings = {},
                onRequestHide = {},
                overlaySide = OverlaySide.Right,
                hudDismissSignal = 0
            )
        }

        composeRule.onNodeWithTag(OVERLAY_BUBBLE_TAG).performTouchInput {
            down(center)
            moveBy(Offset(60f, 0f))
            up()
        }

        composeRule.runOnIdle {
            assertTrue(dragCalls > 0)
            assertEquals(1, dragEndCalls)
        }
    }

    @Test
    fun hudDismissSignal_closesHud() {
        val signal = androidx.compose.runtime.mutableStateOf(0)

        composeRule.setContent {
            OverlayBubble(
                visualState = AssistantVisualState(
                    phase = AssistantPhase.Idle,
                    emotion = Emotion.Neutral
                ),
                onTap = {},
                onLongPress = {},
                onDrag = { _, _ -> },
                onDragEnd = {},
                onRequestVoice = {},
                onRequestTranslate = {},
                onRequestSettings = {},
                onRequestHide = {},
                overlaySide = OverlaySide.Right,
                hudDismissSignal = signal.value
            )
        }

        composeRule.onNodeWithTag(OVERLAY_BUBBLE_TAG).performTouchInput {
            longClick()
        }
        composeRule.onNodeWithTag(OVERLAY_HUD_TAG).assertExists()

        composeRule.runOnIdle {
            signal.value += 1
        }

        composeRule.onNodeWithTag(OVERLAY_HUD_TAG).assertDoesNotExist()
    }
}
