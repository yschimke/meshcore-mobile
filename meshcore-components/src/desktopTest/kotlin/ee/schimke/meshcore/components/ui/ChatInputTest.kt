package ee.schimke.meshcore.components.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Rule

class ChatInputTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun holdingEnterSubmitsOnlyOnRelease() {
    var sends = 0
    compose.setContent { MaterialTheme { ChatInput("hello", {}, { sends++ }) } }
    compose.onNode(hasSetTextAction()).performClick().performKeyInput {
      keyDown(Key.Enter)
      advanceEventTime(1000)
    }
    compose.runOnIdle { assertEquals(0, sends) }
    compose.onNode(hasSetTextAction()).performKeyInput { keyUp(Key.Enter) }
    compose.runOnIdle { assertEquals(1, sends) }
  }

  @Test
  fun modifiedEnterDoesNotSubmit() {
    var sends = 0
    compose.setContent { MaterialTheme { ChatInput("hello", {}, { sends++ }) } }
    compose.onNode(hasSetTextAction()).performClick().performKeyInput {
      keyDown(Key.ShiftLeft)
      pressKey(Key.Enter)
      keyUp(Key.ShiftLeft)
    }
    compose.runOnIdle { assertEquals(0, sends) }
  }

  @Test
  fun blankMessageDoesNotSubmit() {
    var sends = 0
    compose.setContent { MaterialTheme { ChatInput("   ", {}, { sends++ }) } }
    compose.onNode(hasSetTextAction()).performClick().performKeyInput { pressKey(Key.Enter) }
    compose.runOnIdle { assertEquals(0, sends) }
  }

  @Test
  fun releasingModifierBeforeEnterDoesNotSubmit() {
    var sends = 0
    compose.setContent { MaterialTheme { ChatInput("hello", {}, { sends++ }) } }
    for (modifier in listOf(Key.ShiftLeft, Key.CtrlLeft, Key.AltLeft, Key.MetaLeft)) {
      compose.onNode(hasSetTextAction()).performClick().performKeyInput {
        keyDown(modifier)
        keyDown(Key.Enter)
        keyUp(modifier)
        keyUp(Key.Enter)
      }
    }
    compose.runOnIdle { assertEquals(0, sends) }
    compose.onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Enter) }
    compose.runOnIdle { assertEquals(1, sends) }
  }
}
