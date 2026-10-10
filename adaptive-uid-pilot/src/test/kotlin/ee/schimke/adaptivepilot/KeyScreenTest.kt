package ee.schimke.adaptivepilot

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class KeyScreenTest {
  @Test
  fun channelDraftAndDestinationSurviveResize() =
    runDesktopComposeUiTest(width = 1000, height = 800) {
      val width = mutableStateOf(412.dp)
      var sent: Pair<String, String>? = null
      setContent {
        TestWindow(width.value) {
          KeyScreen("channels", onAction = { to, body -> sent = to to body })
        }
      }
      onNodeWithTag("panel-1").performClick()
      onNodeWithTag("action").assertIsNotEnabled()
      onNodeWithTag("input").performTextInput("Trail is clear")
      runOnIdle { width.value = 840.dp }
      onNodeWithTag("channels-list").assertIsDisplayed()
      onNodeWithTag("channels-detail").assertIsDisplayed()
      onNodeWithTag("input").assertTextContains("Trail is clear")
      runOnIdle { width.value = 412.dp }
      onNodeWithTag("action").performClick()
      runOnIdle { assertEquals("#Trail" to "Trail is clear", sent) }
      onNodeWithTag("input").assertTextContains("")
      onNodeWithTag("action").assertIsNotEnabled()
      onNodeWithTag("back-to-list").performClick()
      onNodeWithTag("channels-list").assertIsDisplayed()
    }

  @Test
  fun unavailableSettingsHaveNoWritableControl() =
    runDesktopComposeUiTest(width = 840, height = 800) {
      setContent { KeyScreen("settings", initialDetail = true, initialSelectedIndex = 1) }
      onNodeWithTag("buzzer").assertDoesNotExist()
      onNodeWithText("No settings available").assertIsDisplayed()
    }

  @Test
  fun cachedDeviceHasNoSendOrSettingsAction() =
    runDesktopComposeUiTest(width = 840, height = 800) {
      setContent { KeyScreen("cached") }
      onNodeWithTag("action").assertDoesNotExist()
      onNodeWithTag("input").assertDoesNotExist()
      onNodeWithTag("buzzer").assertDoesNotExist()
      onNodeWithText("Read only · Connect to send messages or change settings.").assertIsDisplayed()
    }

  @Test
  fun buzzerChangesReachCallback() =
    runDesktopComposeUiTest(width = 840, height = 800) {
      var change: Pair<String, String>? = null
      setContent { KeyScreen("settings", onAction = { key, value -> change = key to value }) }
      onNodeWithTag("buzzer").performClick()
      runOnIdle { assertEquals("buzzer" to "false", change) }
    }
}
