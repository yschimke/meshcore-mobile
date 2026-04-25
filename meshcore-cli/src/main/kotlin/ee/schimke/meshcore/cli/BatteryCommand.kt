package ee.schimke.meshcore.cli

import com.github.ajalt.mordant.rendering.TextColors.green
import com.github.ajalt.mordant.rendering.TextColors.red
import com.github.ajalt.mordant.rendering.TextColors.yellow

class BatteryCommand : SessionCommand("battery", "Show battery and storage status") {
  override fun run() = withClient { client ->
    val b = client.getBatteryAndStorage()
    val pct = b.estimatePercent()
    val colored =
      when {
        pct >= 60 -> green("$pct%")
        pct >= 25 -> yellow("$pct%")
        else -> red("$pct%")
      }
    terminal.println("Battery: ${b.millivolts} mV ($colored)")
    terminal.println("Storage: ${b.storageUsedKb} / ${b.storageTotalKb} kB")
  }
}
