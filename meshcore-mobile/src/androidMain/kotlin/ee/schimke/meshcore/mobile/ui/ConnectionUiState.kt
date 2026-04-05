package ee.schimke.meshcore.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import ee.schimke.meshcore.core.manager.ManagerState
import ee.schimke.meshcore.core.manager.MeshCoreManager

/**
 * Small Compose adapter so app code can observe a [MeshCoreManager]
 * with a single call. Returns the current [ManagerState] as a [State]
 * for direct use in composables.
 */
@Composable
fun MeshCoreManager.collectStateAsState(): State<ManagerState> = state.collectAsState()

/** True while a connect attempt is in progress. */
val ManagerState.isConnecting: Boolean get() = this is ManagerState.Connecting

/** True once a [MeshCoreManager] has produced a live client. */
val ManagerState.isConnected: Boolean get() = this is ManagerState.Connected
