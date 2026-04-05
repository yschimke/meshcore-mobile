package ee.schimke.meshcore.app.ui.theme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SettingsBrightness
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Modal theme picker with two independent sections: which palette
 * (MeshCore brand vs Material You dynamic) and which light/dark mode
 * (follow system, always light, always dark). Caller keeps the
 * authoritative [current] state and persists each change.
 */
@Composable
fun ThemePickerDialog(
    current: ThemeSettings,
    onModeSelect: (ThemeMode) -> Unit,
    onPaletteSelect: (ThemePalette) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Theme") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionLabel("Palette")
                ThemeOption(
                    icon = Icons.Rounded.Palette,
                    label = "MeshCore",
                    description = "Hand-tuned teal brand palette",
                    selected = current.palette == ThemePalette.Meshcore,
                    onClick = { onPaletteSelect(ThemePalette.Meshcore) },
                )
                ThemeOption(
                    icon = Icons.Rounded.AutoAwesome,
                    label = "Dynamic (system)",
                    description = "Material You colors from your wallpaper",
                    selected = current.palette == ThemePalette.Dynamic,
                    onClick = { onPaletteSelect(ThemePalette.Dynamic) },
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SectionLabel("Light / dark")
                ThemeOption(
                    icon = Icons.Rounded.SettingsBrightness,
                    label = "Follow system",
                    description = "Match your device's light/dark setting",
                    selected = current.mode == ThemeMode.System,
                    onClick = { onModeSelect(ThemeMode.System) },
                )
                ThemeOption(
                    icon = Icons.Rounded.LightMode,
                    label = "Light",
                    description = "Always use the light palette",
                    selected = current.mode == ThemeMode.Light,
                    onClick = { onModeSelect(ThemeMode.Light) },
                )
                ThemeOption(
                    icon = Icons.Rounded.DarkMode,
                    label = "Dark",
                    description = "Always use the dark palette",
                    selected = current.mode == ThemeMode.Dark,
                    onClick = { onModeSelect(ThemeMode.Dark) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
    )
}

@Composable
private fun ThemeOption(
    icon: ImageVector,
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val alpha = if (enabled) 1f else 0.38f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick, enabled = enabled)
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            )
        }
    }
}

@Preview(showBackground = true, name = "ThemePicker — MeshCore + System")
@Composable
fun ThemePickerMeshcoreSystemPreview() {
    MeshcoreTheme {
        ThemePickerDialog(
            current = ThemeSettings(mode = ThemeMode.System, palette = ThemePalette.Meshcore),
            onModeSelect = {},
            onPaletteSelect = {},
            onDismiss = {},
        )
    }
}

@Preview(showBackground = true, name = "ThemePicker — Dynamic + Dark")
@Composable
fun ThemePickerDynamicDarkPreview() {
    MeshcoreTheme(darkTheme = true) {
        ThemePickerDialog(
            current = ThemeSettings(mode = ThemeMode.Dark, palette = ThemePalette.Dynamic),
            onModeSelect = {},
            onPaletteSelect = {},
            onDismiss = {},
        )
    }
}
