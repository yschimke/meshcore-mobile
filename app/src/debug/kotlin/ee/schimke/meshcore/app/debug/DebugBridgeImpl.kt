package ee.schimke.meshcore.app.debug

import android.app.Service
import ee.schimke.meshcore.app.MeshcoreApp
import java.io.PrintWriter

/**
 * Debug-build implementation of [DebugBridge]. Single event ring buffer
 * shared across dumpsys calls so `--events` reflects activity the
 * caller didn't necessarily observe live.
 */
internal class DebugBridgeImpl(private val app: MeshcoreApp) : DebugBridge {

    private val events = DebugEventBuffer().also { it.attach(app) }

    override fun dumpService(service: Service, writer: PrintWriter, args: Array<String>?) {
        DebugDump.dispatch(app, writer, args, events)
    }
}
