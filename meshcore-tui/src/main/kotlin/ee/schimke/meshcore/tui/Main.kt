package ee.schimke.meshcore.tui

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int

/**
 * Single-command entry point. The TUI is inherently one screen – there are no subcommands – so it's
 * a plain [CliktCommand] rather than a multi-command tree.
 */
class MeshcoreTui : CliktCommand(name = "meshcore-tui") {
  override fun help(context: Context): String = "Interactive MeshCore dashboard backed by Mordant."

  val host by option("--host", "-h").default("127.0.0.1").help("TCP host")
  val port by option("--port", "-p").int().default(5000).help("TCP port")
  val historySize by
    option("--history").int().default(20).help("How many recent events to keep on screen")

  override fun run() = Dashboard(host, port, historySize).run()
}

fun main(args: Array<String>) = MeshcoreTui().main(args)
