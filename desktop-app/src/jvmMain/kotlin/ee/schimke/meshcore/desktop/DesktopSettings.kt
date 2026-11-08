package ee.schimke.meshcore.desktop

import java.awt.Rectangle
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

internal data class DesktopPreferences(
  val bounds: Rectangle = Rectangle(0, 0, 1100, 800),
  val positioned: Boolean = false,
  val maximized: Boolean = false,
  val host: String = "192.168.1.10",
  val port: Int = 5000,
)

/** Small host preferences; radio messages live separately in Room. */
internal class DesktopSettings(private val file: File) {
  fun load(): DesktopPreferences {
    val properties = Properties()
    runCatching { file.inputStream().use(properties::load) }
    fun int(key: String, fallback: Int) = properties.getProperty(key)?.toIntOrNull() ?: fallback
    return DesktopPreferences(
      bounds = Rectangle(int("x", 0), int("y", 0), int("width", 1100), int("height", 800)),
      positioned = properties.getProperty("positioned").toBoolean(),
      maximized = properties.getProperty("maximized").toBoolean(),
      host = properties.getProperty("host")?.takeIf { it.isNotBlank() } ?: "192.168.1.10",
      port = int("port", 5000).takeIf { it in 1..65535 } ?: 5000,
    )
  }

  fun save(preferences: DesktopPreferences) {
    val properties =
      Properties().apply {
        setProperty("x", preferences.bounds.x.toString())
        setProperty("y", preferences.bounds.y.toString())
        setProperty("width", preferences.bounds.width.toString())
        setProperty("height", preferences.bounds.height.toString())
        setProperty("positioned", preferences.positioned.toString())
        setProperty("maximized", preferences.maximized.toString())
        setProperty("host", preferences.host)
        setProperty("port", preferences.port.toString())
      }
    file.parentFile.mkdirs()
    val temporary = Files.createTempFile(file.parentFile.toPath(), "desktop-", ".properties")
    try {
      Files.newOutputStream(temporary).use { properties.store(it, "MeshCore desktop") }
      try {
        Files.move(
          temporary,
          file.toPath(),
          StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.ATOMIC_MOVE,
        )
      } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
        Files.move(temporary, file.toPath(), StandardCopyOption.REPLACE_EXISTING)
      }
    } finally {
      Files.deleteIfExists(temporary)
    }
  }
}

/** Fit the whole window into an attached monitor, including after a monitor is unplugged. */
internal fun restoreBounds(saved: Rectangle, screens: List<Rectangle>): Rectangle {
  val screen =
    screens.maxByOrNull { area ->
      val intersection = area.intersection(saved)
      if (intersection.isEmpty) 0L else intersection.width.toLong() * intersection.height
    } ?: Rectangle(0, 0, 1920, 1080)
  val width = saved.width.coerceAtLeast(640).coerceAtMost(screen.width)
  val height = saved.height.coerceAtLeast(480).coerceAtMost(screen.height)
  return Rectangle(
    saved.x.coerceIn(screen.x, screen.x + screen.width - width),
    saved.y.coerceIn(screen.y, screen.y + screen.height - height),
    width,
    height,
  )
}
