package ee.schimke.adaptivepilot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ContactBrowserTest {
  @Test
  fun selectionSurvivesResizeAndBackReturnsToList() =
    runDesktopComposeUiTest(width = 1000, height = 800) {
      var opened: String? = null
      val width = mutableStateOf(412.dp)
      setContent {
        Box(Modifier.requiredSize(width.value, 720.dp)) { ContactBrowser(onOpen = { opened = it }) }
      }
      onNodeWithTag("contacts-list").assertIsDisplayed()
      onNodeWithTag("contacts-detail").assertDoesNotExist()
      onNodeWithTag("entry-1").performClick()
      onNodeWithTag("contacts-detail").assertIsDisplayed()
      onNodeWithText("Bob").assertIsDisplayed()
      onNodeWithTag("contacts-list").assertDoesNotExist()
      runOnIdle { width.value = 840.dp }
      onNodeWithTag("contacts-list").assertIsDisplayed()
      onNodeWithTag("contacts-detail").assertIsDisplayed()
      runOnIdle { width.value = 412.dp }
      onNodeWithText("Bob").assertIsDisplayed()
      onNodeWithTag("open-entry").performClick()
      runOnIdle { assertEquals("bob", opened) }
      onNodeWithTag("back-to-list").performClick()
      onNodeWithTag("contacts-list").assertIsDisplayed()
      onNodeWithTag("contacts-detail").assertDoesNotExist()
    }

  @Test
  fun breakpointOpensBothPanesAt840dp() =
    runDesktopComposeUiTest(width = 1000, height = 800) {
      val width = mutableStateOf(839.dp)
      setContent { Box(Modifier.requiredSize(width.value, 720.dp)) { ContactBrowser() } }
      onNodeWithTag("contacts-detail").assertDoesNotExist()
      runOnIdle { width.value = 840.dp }
      onNodeWithTag("contacts-list").assertIsDisplayed()
      onNodeWithTag("contacts-detail").assertIsDisplayed()
    }
}
