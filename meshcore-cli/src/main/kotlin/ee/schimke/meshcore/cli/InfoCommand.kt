package ee.schimke.meshcore.cli

import com.github.ajalt.mordant.rendering.TextColors.brightBlue
import com.github.ajalt.mordant.rendering.TextColors.cyan
import com.github.ajalt.mordant.rendering.TextColors.gray
import com.github.ajalt.mordant.rendering.TextStyles.bold
import com.github.ajalt.mordant.table.table

class InfoCommand : SessionCommand("info", "Show device self info, radio and battery") {
  override fun run() = withClient { client ->
    val self = client.selfInfo.value
    val radio = client.radio.value
    val battery = client.battery.value
    terminal.println((bold + brightBlue)("MeshCore device"))
    terminal.println(
      table {
        header { row("Field", "Value") }
        body {
          row("name", self?.name ?: "(unknown)")
          row("pubkey", self?.publicKey?.toString() ?: "—")
          row("location", self?.let { "${it.latitude}, ${it.longitude}" } ?: "—")
          row("tx power", self?.let { "${it.txPowerDbm} / ${it.maxPowerDbm} dBm" } ?: "—")
          radio?.let {
            row("frequency", "${it.frequencyHz / 1_000_000.0} MHz")
            row("bandwidth", "${it.bandwidthHz / 1_000.0} kHz")
            row("spreading", "SF${it.spreadingFactor}")
            row("coding rate", "4/${it.codingRate}")
          }
          battery?.let {
            row("battery", cyan("${it.millivolts} mV (${it.estimatePercent()}%)"))
            row("storage", "${it.storageUsedKb} / ${it.storageTotalKb} kB")
          }
        }
      }
    )
    if (self == null) {
      terminal.println(gray("No SELF_INFO received yet — try a longer --warmup."))
    }
  }
}
