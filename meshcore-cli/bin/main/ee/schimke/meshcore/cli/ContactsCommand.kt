package ee.schimke.meshcore.cli

import com.github.ajalt.mordant.rendering.TextColors.green
import com.github.ajalt.mordant.rendering.TextColors.yellow
import com.github.ajalt.mordant.rendering.TextStyles.bold
import com.github.ajalt.mordant.table.table

class ContactsCommand : SessionCommand("contacts", "List contacts known to the device") {
  override fun run() = withClient { client ->
    val contacts = client.getContacts()
    terminal.println(bold("${contacts.size} contacts"))
    if (contacts.isEmpty()) return@withClient
    terminal.println(
      table {
        header { row("name", "type", "path", "pubkey") }
        body {
          contacts.forEach { c ->
            val path = if (c.isFlood) yellow("flood") else green("${c.pathLength} hops")
            row(c.name, c.type.name, path, c.publicKey.toString().take(12) + "…")
          }
        }
      }
    )
  }
}
