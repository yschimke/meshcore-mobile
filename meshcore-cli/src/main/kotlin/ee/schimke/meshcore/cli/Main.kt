package ee.schimke.meshcore.cli

import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.mordant.terminal.Terminal

/**
 * Entry point for the `meshcore` CLI. Wires all subcommands together
 * under a single root command. The CLI connects to a MeshCore device
 * over TCP (the companion "serial bridge"); BLE and USB are mobile
 * concerns and live in the Android sample.
 */
class MeshcoreCli : NoOpCliktCommand(name = "meshcore") {
    override fun help(context: com.github.ajalt.clikt.core.Context): String =
        "Talk to a MeshCore device over TCP from the command line."
}

val terminal: Terminal = Terminal()

fun main(args: Array<String>) = MeshcoreCli()
    .subcommands(
        InfoCommand(),
        ContactsCommand(),
        BatteryCommand(),
        RadioCommand(),
        SendCommand(),
        ReplCommand(),
    )
    .main(args)
