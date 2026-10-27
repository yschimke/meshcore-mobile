@file:Suppress("RestrictedApi")

package ee.schimke.meshcore.app.widget

import android.app.Application
import ee.schimke.composeai.rcplayer.edit.RcTargetProfiles
import ee.schimke.composeai.rcplayer.edit.validateFor
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcWireLimits
import ee.schimke.composeai.rcwidget.creation.rcWidgetHostAction
import ee.schimke.composeai.rcwidget.launcher.RcWidgetSizing
import ee.schimke.composeai.rcwidget.launcher.captureLauncherWidget
import ee.schimke.composeai.rcwidget.launcher.systemThemedWidgetsProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// The capture runs on any SDK: only RemoteViews.DrawInstructions needs a widget-hosting one.
@Config(sdk = [35], application = Application::class)
class DeviceInfoWidgetTest {
  private val context = RuntimeEnvironment.getApplication()

  /**
   * Every slot the widget resizes to captures a document the API 37 launcher plays (the receiver is
   * enabled from values-v37), with its one tap.
   */
  @Test
  fun everySlotIsALauncherWidgetThatOpensTheApp() {
    runBlocking {
      for ((w, h) in listOf(4 to 2, 2 to 2, 5 to 3)) {
        val capture =
          captureLauncherWidget(context, RcWidgetSizing.cells(w, h), systemThemedWidgetsProfile) {
            DeviceInfoWidgetContent(
              deviceName = "node-peak",
              pubkeyPrefix = "ab1234567890cdef",
              radioInfo = "869.525 MHz · BW 125 kHz · SF 10 · CR 5",
              batteryLine = "81% · 3980 mV",
              batteryProgress = 0.81f,
              storageLine = "512 / 4096 kB",
              onTap = rcWidgetHostAction(name = "open", payload = "open"),
            )
          }
        assertEquals(listOf("open"), capture.actions.values.toList())
        val document = RcDocumentCodec.decode(capture.bytes, RcWireLimits())
        assertEquals("${w}x$h", emptyList<Any>(), document.validateFor(RcTargetProfiles.WIDGETS_V7))
      }
    }
  }
}
