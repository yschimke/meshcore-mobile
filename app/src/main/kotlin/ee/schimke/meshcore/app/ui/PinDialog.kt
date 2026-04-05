package ee.schimke.meshcore.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ee.schimke.meshcore.app.ui.theme.MeshcoreTheme

/**
 * Collects a PIN for a specific device. Caller owns whether/how the
 * result is persisted — the dialog just returns the string.
 */
@Composable
fun PinDialog(
    deviceLabel: String,
    initialPin: String = "",
    onConfirm: (pin: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pin by remember { mutableStateOf(initialPin) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Unlock $deviceLabel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("This device requires a PIN to connect. It will be remembered for next time.")
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter { c -> c.isDigit() || c.isLetter() } },
                    label = { Text("PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = pin.isNotEmpty(),
                onClick = { onConfirm(pin) },
            ) { Text("Connect") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Preview(showBackground = true, name = "PinDialog — empty")
@Composable
fun PinDialogEmptyPreview() {
    MeshcoreTheme {
        PinDialog(deviceLabel = "MeshCore-ABCD", onConfirm = {}, onDismiss = {})
    }
}

@Preview(showBackground = true, name = "PinDialog — prefilled")
@Composable
fun PinDialogFilledPreview() {
    MeshcoreTheme {
        PinDialog(
            deviceLabel = "MeshCore-ABCD",
            initialPin = "1234",
            onConfirm = {},
            onDismiss = {},
        )
    }
}
