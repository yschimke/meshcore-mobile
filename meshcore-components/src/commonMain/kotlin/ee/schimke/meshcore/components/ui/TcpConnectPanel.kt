package ee.schimke.meshcore.components.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.then
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ee.schimke.meshcore.components.ui.icons.MeshIcons

/**
 * Form for connecting to a MeshCore companion over TCP. Stateless at the API boundary — it owns the
 * host/port text fields internally but delegates the actual connect to the caller.
 */
@Composable
fun TcpConnectPanel(
  busy: Boolean,
  onConnect: (host: String, port: Int) -> Unit,
  modifier: Modifier = Modifier,
  defaultHost: String = "192.168.1.10",
  defaultPort: Int = 5000,
) {
  val hostState = rememberTextFieldState(initialText = defaultHost)
  val portState = rememberTextFieldState(initialText = defaultPort.toString())
  val digitsOnly = InputTransformation {
    if (!asCharSequence().all { it.isDigit() }) {
      revertAllChanges()
    }
  }
  val portValue = portState.text.toString().toIntOrNull()
  Card(
    modifier = modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = MeshIcons.Router,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.size(8.dp))
        Text(
          text = "Companion over TCP",
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }
      OutlinedTextField(
        state = hostState,
        label = { Text("Host") },
        enabled = !busy,
        lineLimits = TextFieldLineLimits.SingleLine,
        modifier = Modifier.fillMaxWidth(),
      )
      OutlinedTextField(
        state = portState,
        label = { Text("Port") },
        enabled = !busy,
        lineLimits = TextFieldLineLimits.SingleLine,
        inputTransformation = digitsOnly.then(InputTransformation.maxLength(5)),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
      )
      Button(
        enabled = !busy && hostState.text.isNotBlank() && portValue in 1..65535,
        onClick = { onConnect(hostState.text.toString().trim(), portValue!!) },
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(if (busy) "Connecting…" else "Connect")
      }
    }
  }
}
