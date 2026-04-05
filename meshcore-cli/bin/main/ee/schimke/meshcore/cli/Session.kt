package ee.schimke.meshcore.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import ee.schimke.meshcore.core.client.MeshCoreClient
import ee.schimke.meshcore.core.transport.TcpTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * Mixin that every CLI subcommand inherits from to get shared
 * `--host`/`--port` options plus a [withClient] helper that opens a
 * transport, runs the block, and tears everything down.
 */
abstract class TcpSessionCommand(
    name: String,
    help: String,
) : CliktCommand(name = name) {

    private val helpText = help
    override fun help(context: com.github.ajalt.clikt.core.Context): String = helpText

    protected val host by option("--host", "-h")
        .default("127.0.0.1")
        .help("TCP host of the MeshCore companion")

    protected val port by option("--port", "-p").int()
        .default(5000)
        .help("TCP port of the MeshCore companion")

    protected val warmupMs by option("--warmup")
        .int()
        .default(400)
        .help("How long to wait after CMD_APP_START for device responses (ms)")

    protected fun <T> withClient(block: suspend (MeshCoreClient) -> T): T = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val transport = TcpTransport(host, port)
        transport.connect()
        val client = MeshCoreClient(transport, scope)
        try {
            client.start()
            if (warmupMs > 0) delay(warmupMs.toLong())
            block(client)
        } finally {
            client.stop()
            transport.close()
            scope.cancel()
        }
    }
}
