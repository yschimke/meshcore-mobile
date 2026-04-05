package ee.schimke.meshcore.mobile

import android.content.Context
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import ee.schimke.meshcore.core.manager.MeshCoreManager

/**
 * Metro dependency graph for Android host apps. Wire an Application
 * [Context] in once at startup, and this graph hands out a
 * [MeshCoreManager] plus the Android-only helpers (USB port listing,
 * etc.) without callers needing to pass a Context around.
 *
 * Typical usage in `Application.onCreate`:
 *
 * ```kotlin
 * val graph = createGraph<MobileGraph>(MobileGraph.Factory { applicationContext })
 * val manager = graph.manager
 * val usbLister = graph.usbPorts
 * ```
 */
@DependencyGraph
interface MobileGraph {

    val manager: MeshCoreManager
    val usbPorts: AndroidUsbPortLister

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides context: Context): MobileGraph
    }

    @Provides
    fun provideManager(): MeshCoreManager = MeshCoreManager()
}
