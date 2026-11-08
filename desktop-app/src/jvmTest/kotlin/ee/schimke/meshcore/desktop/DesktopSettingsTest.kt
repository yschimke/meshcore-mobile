package ee.schimke.meshcore.desktop

import java.awt.Rectangle
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopSettingsTest {
  @Test
  fun removedMonitorAndOversizedWindowAreRecovered() {
    val screen = Rectangle(0, 30, 1280, 690)
    val restored = restoreBounds(Rectangle(2500, -400, 1800, 1200), listOf(screen))
    assertEquals(screen, restored)
  }

  @Test
  fun windowStaysOnItsNegativeCoordinateMonitor() {
    val bounds = Rectangle(-1500, 100, 900, 700)
    assertEquals(
      bounds,
      restoreBounds(
        bounds,
        listOf(Rectangle(0, 0, 1920, 1080), Rectangle(-1920, 0, 1920, 1080)),
      ),
    )
  }

  @Test
  fun preferencesSurviveRestart() {
    val directory = Files.createTempDirectory("meshcore-settings").toFile()
    try {
      val file = directory.resolve("desktop.properties")
      val preferences =
        DesktopPreferences(Rectangle(100, 50, 1000, 700), true, true, "radio.local", 6000)
      DesktopSettings(file).save(preferences)
      assertEquals(preferences, DesktopSettings(file).load())
      assertEquals(listOf("desktop.properties"), directory.list()!!.toList())
    } finally {
      directory.deleteRecursively()
    }
  }

  @Test
  fun malformedSettingsUseSafeDefaults() {
    val directory = Files.createTempDirectory("meshcore-settings").toFile()
    try {
      val file = directory.resolve("desktop.properties")
      file.writeText("port=99999\nwidth=not-a-number\nhost=\n")
      val saved = DesktopSettings(file).load()
      assertEquals(5000, saved.port)
      assertEquals(1100, saved.bounds.width)
      assertTrue(saved.host.isNotBlank())
    } finally {
      directory.deleteRecursively()
    }
  }
}
