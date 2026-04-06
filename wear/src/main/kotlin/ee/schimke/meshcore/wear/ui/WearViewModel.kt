package ee.schimke.meshcore.wear.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ee.schimke.meshcore.grpc.ConnectionState
import ee.schimke.meshcore.grpc.ContactMsg
import ee.schimke.meshcore.wear.MeshcoreWearApp
import ee.schimke.meshcore.wear.data.MeshcoreWearClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

sealed class WearUiState {
    data object Loading : WearUiState()
    data object PhoneDisconnected : WearUiState()
    data object RadioDisconnected : WearUiState()
    data class Connected(
        val deviceName: String,
        val batteryPercent: Int?,
        val contactCount: Int,
        val radioInfo: String?,
        val contacts: List<ContactMsg> = emptyList(),
    ) : WearUiState()

    data class Error(val message: String) : WearUiState()
}

class WearViewModel : ViewModel() {

    private val client: MeshcoreWearClient = MeshcoreWearApp.get().meshClient

    private val _state = MutableStateFlow<WearUiState>(WearUiState.Loading)
    val state: StateFlow<WearUiState> = _state.asStateFlow()

    init {
        refresh()
        subscribeConnectionStatus()
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                val status = client.getConnectionStatus()
                when (status.state) {
                    ConnectionState.CONNECTED -> loadConnectedState()
                    ConnectionState.CONNECTING -> _state.value = WearUiState.Loading
                    ConnectionState.DISCONNECTED,
                    ConnectionState.FAILED,
                    -> _state.value = WearUiState.RadioDisconnected

                    else -> _state.value = WearUiState.RadioDisconnected
                }
            } catch (_: Exception) {
                _state.value = WearUiState.PhoneDisconnected
            }
        }
    }

    private suspend fun loadConnectedState() {
        try {
            val self = client.getSelfInfo()
            val battery = client.getBatteryInfo()
            val contacts = client.getContacts()

            val radioLine = if (self.hasRadio()) {
                val r = self.radio
                val freq = r.frequencyHz / 1_000_000.0
                "%.3f MHz · SF%d · BW%dk".format(freq, r.spreadingFactor, r.bandwidthHz / 1000)
            } else {
                null
            }

            _state.value = WearUiState.Connected(
                deviceName = self.name.ifEmpty { "MeshCore" },
                batteryPercent = battery.estimatedPercent.takeIf { it > 0 },
                contactCount = contacts.contactsCount,
                radioInfo = radioLine,
                contacts = contacts.contactsList,
            )
        } catch (e: Exception) {
            _state.value = WearUiState.Error(e.message ?: "Failed to load")
        }
    }

    private fun subscribeConnectionStatus() {
        viewModelScope.launch {
            client.subscribeConnectionStatus()
                .catch { _state.value = WearUiState.PhoneDisconnected }
                .collect { status ->
                    when (status.state) {
                        ConnectionState.CONNECTED -> loadConnectedState()
                        ConnectionState.CONNECTING -> _state.value = WearUiState.Loading
                        else -> _state.value = WearUiState.RadioDisconnected
                    }
                }
        }
    }

    suspend fun sendDirectMessage(recipientKey: com.google.protobuf.ByteString, text: String) =
        client.sendDirectMessage(recipientKey, text)
}
