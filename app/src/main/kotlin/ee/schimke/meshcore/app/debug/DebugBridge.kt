package ee.schimke.meshcore.app.debug

import android.app.Service
import java.io.PrintWriter

/**
 * Thin seam between main and the debug-only source set. A concrete
 * [DebugBridge] is installed by `DebugInit` (ContentProvider, registered
 * only in `app/src/debug/AndroidManifest.xml`) before [android.app.Application.onCreate]
 * runs. In release builds no provider is declared, so [instance] stays
 * null and `Service.dump()` returns a short "not available" message.
 */
interface DebugBridge {

    /** Handle a `dumpsys activity service <svc>` invocation. */
    fun dumpService(service: Service, writer: PrintWriter, args: Array<String>?)

    /**
     * Invoked from [android.app.Application.onCreate] once the app graph is
     * initialized. The bridge is installed earlier (via ContentProvider) so it
     * cannot safely touch graph-backed state from its constructor.
     */
    fun onAppReady() {}

    companion object {
        @Volatile
        var instance: DebugBridge? = null
    }
}
