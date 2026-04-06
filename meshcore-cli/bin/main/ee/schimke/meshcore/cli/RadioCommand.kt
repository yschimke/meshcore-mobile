package ee.schimke.meshcore.cli

import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int

class RadioCommand : SessionCommand("radio", "Read (or update) LoRa radio parameters") {

    private val setFreq by option("--freq").int().help("Set frequency in Hz (optional)")
    private val setBw by option("--bw").int().help("Set bandwidth in Hz (optional)")
    private val setSf by option("--sf").int().help("Set spreading factor (5-12)")
    private val setCr by option("--cr").int().help("Set coding rate (5-8)")

    override fun run() = withClient { client ->
        val current = client.getRadioSettings()
        if (listOf(setFreq, setBw, setSf, setCr).any { it != null }) {
            client.setRadioParams(
                freqHz = setFreq ?: current.frequencyHz,
                bwHz = setBw ?: current.bandwidthHz,
                sf = setSf ?: current.spreadingFactor,
                cr = setCr ?: current.codingRate,
            )
            terminal.println("Sent new radio params; re-reading…")
        }
        val updated = client.getRadioSettings()
        terminal.println("Frequency  : ${updated.frequencyHz / 1_000_000.0} MHz")
        terminal.println("Bandwidth  : ${updated.bandwidthHz / 1_000.0} kHz")
        terminal.println("Spreading  : SF${updated.spreadingFactor}")
        terminal.println("CodingRate : 4/${updated.codingRate}")
    }
}
