@file:Suppress("RestrictedApi", "RestrictedApiAndroidX")

package ee.schimke.meshcore.wear.widget

import androidx.glance.wear.core.WearWidgetParams
import androidx.glance.wear.core.WidgetInstanceId
import ee.schimke.composeai.rcplayer.edit.RcTargetProfiles
import ee.schimke.composeai.rcplayer.edit.validateFor
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcWireLimits
import ee.schimke.composeai.rcwidget.RcWearHost
import ee.schimke.composeai.rcwidget.creation.RcWearBackground
import ee.schimke.composeai.rcwidget.wear.RcWearWidgetTaps
import ee.schimke.composeai.rcwidget.wear.RcWearWidgetTemplate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StatusWidgetTest {
  private val context = RuntimeEnvironment.getApplication()

  private fun params(host: RcWearHost): WearWidgetParams {
    val frame = host.large
    return WearWidgetParams(
      WidgetInstanceId("widgets", 1),
      frame.type.glanceType,
      frame.widthDp,
      frame.heightDp,
      frame.horizontalPaddingDp,
      frame.verticalPaddingDp,
      frame.cornerRadiusDp,
    )
  }

  /** The widget is generated from its template, not its fallback capture, on every host. */
  @Test
  fun theTemplateFitsEveryHost() {
    runBlocking {
      val template =
        RcWearWidgetTemplate.capture(context, background = RcWearBackground(StatusWidgetSurface)) {
          StatusWidgetContent(
            "node-ridge",
            connected = true,
            batteryPercent = 81,
            contactCount = 3,
            framed = true,
          )
        }
      for (host in RcWearHost.All) {
        assertNotNull(
          host.toString(),
          RcWearWidgetTemplate.instantiate(context, template, params(host)),
        )
      }
    }
  }

  @Test
  fun everyHostGetsAPlayableWidgetThatOpensTheApp() {
    runBlocking {
      for (host in RcWearHost.All) {
        val params = params(host)
        val content =
          StatusWidget()
            .provideWidgetData(context, params)
            .captureRawContent(context, params, false)
        val document = RcDocumentCodec.decode(content.rcDocument, RcWireLimits())
        assertEquals(
          host.toString(),
          emptyList<Any>(),
          document.validateFor(RcTargetProfiles.WEAR_WIDGETS),
        )
        assertNotNull(
          host.toString(),
          content.extras
            .getBundle(RcWearWidgetTaps.PENDING_INTENTS)
            ?.get(RcWearWidgetTaps.OPEN_APP_INDEX.toString()),
        )
      }
    }
  }
}
