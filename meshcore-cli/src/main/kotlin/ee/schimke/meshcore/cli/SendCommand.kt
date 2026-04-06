package ee.schimke.meshcore.cli

import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.mordant.rendering.TextColors.green
import ee.schimke.meshcore.core.model.PublicKey
import kotlin.time.Clock

class SendCommand : SessionCommand("send", "Send a direct text message to a contact") {

    private val targetHex by option("--to")
        .help("Recipient hex pubkey (full or 6-byte prefix). If omitted, pick by --name.")
    private val targetName by option("--name")
        .help("Recipient contact name (substring match, case-insensitive)")
    private val text by argument().help("Message body")

    override fun run() = withClient { client ->
        val contacts = client.getContacts()
        val recipient: PublicKey = when {
            targetHex != null -> PublicKey.fromHex(targetHex!!)
            targetName != null -> {
                val needle = targetName!!.lowercase()
                contacts.firstOrNull { it.name.lowercase().contains(needle) }
                    ?.publicKey
                    ?: throw CliktError("no contact matches '$needle'")
            }
            else -> throw CliktError("specify --to <hex> or --name <substring>")
        }
        val ack = client.sendText(
            recipient = recipient,
            text = text,
            timestamp = Clock.System.now(),
        )
        terminal.println(green("Sent.") + " flood=${ack.isFlood} timeout=${ack.timeoutMs}ms")
    }
}
