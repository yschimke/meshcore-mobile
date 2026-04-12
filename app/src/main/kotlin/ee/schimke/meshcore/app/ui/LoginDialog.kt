package ee.schimke.meshcore.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * State machine for the room/repeater login dialog.
 * - [Prompting]: show the password field, user hasn't submitted yet or
 *   a previous attempt failed.
 * - [Authenticating]: login request is in-flight.
 */
sealed class LoginDialogState {
    data class Prompting(val errorMessage: String? = null) : LoginDialogState()
    data object Authenticating : LoginDialogState()
}

/**
 * Dialog shown when the user taps a room or repeater for the first
 * time. Rooms and repeaters require a password to join — common
 * defaults are an empty string `""` or `"hello"`.
 */
@Composable
fun LoginDialog(
    contactName: String,
    contactType: String,
    state: LoginDialogState,
    onLogin: (password: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = {
            if (state !is LoginDialogState.Authenticating) onDismiss()
        },
        title = { Text("Join $contactType") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Enter the password for \"$contactName\".",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Many rooms and repeaters use an empty password or \"hello\" as defaults.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    placeholder = { Text("empty or hello") },
                    singleLine = true,
                    enabled = state is LoginDialogState.Prompting,
                    isError = state is LoginDialogState.Prompting && state.errorMessage != null,
                    supportingText = if (state is LoginDialogState.Prompting && state.errorMessage != null) {
                        { Text(state.errorMessage, color = MaterialTheme.colorScheme.error) }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state is LoginDialogState.Authenticating) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(top = 4.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onLogin(password) },
                enabled = state is LoginDialogState.Prompting,
            ) {
                Text("Join")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = state !is LoginDialogState.Authenticating,
            ) {
                Text("Cancel")
            }
        },
    )
}
