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
import ee.schimke.meshcore.components.generated.resources.Res
import ee.schimke.meshcore.components.generated.resources.theme_title
import ee.schimke.meshcore.components.generated.resources.theme_palette
import ee.schimke.meshcore.components.generated.resources.theme_light_dark
import ee.schimke.meshcore.components.generated.resources.theme_done
import ee.schimke.meshcore.components.generated.resources.theme_light
import ee.schimke.meshcore.components.generated.resources.theme_dark
import ee.schimke.meshcore.components.generated.resources.theme_follow_system
import ee.schimke.meshcore.components.generated.resources.theme_dynamic
import ee.schimke.meshcore.components.generated.resources.theme_meshcore_desc
import ee.schimke.meshcore.components.generated.resources.theme_dynamic_desc
import ee.schimke.meshcore.components.generated.resources.theme_follow_system_desc
import ee.schimke.meshcore.components.generated.resources.theme_light_desc
import ee.schimke.meshcore.components.generated.resources.theme_dark_desc
import org.jetbrains.compose.resources.stringResource

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
        title = { Text(stringResource(Res.string.theme_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionLabel(stringResource(Res.string.theme_palette))
                ThemeOption(
                    icon = Icons.Rounded.Palette,
                    label = "MeshCore",
                    description = stringResource(Res.string.theme_meshcore_desc),
                    selected = current.palette == ThemePalette.Meshcore,
                    onClick = { onPaletteSelect(ThemePalette.Meshcore) },
                )
                ThemeOption(
                    icon = Icons.Rounded.AutoAwesome,
                    label = stringResource(Res.string.theme_dynamic),
                    description = stringResource(Res.string.theme_dynamic_desc),
                    selected = current.palette == ThemePalette.Dynamic,
                    onClick = { onPaletteSelect(ThemePalette.Dynamic) },
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SectionLabel(stringResource(Res.string.theme_light_dark))
                ThemeOption(
                    icon = Icons.Rounded.SettingsBrightness,
                    label = stringResource(Res.string.theme_follow_system),
                    description = stringResource(Res.string.theme_follow_system_desc),
                    selected = current.mode == ThemeMode.System,
                    onClick = { onModeSelect(ThemeMode.System) },
                )
                ThemeOption(
                    icon = Icons.Rounded.LightMode,
                    label = stringResource(Res.string.theme_light),
                    description = stringResource(Res.string.theme_light_desc),
                    selected = current.mode == ThemeMode.Light,
                    onClick = { onModeSelect(ThemeMode.Light) },
                )
                ThemeOption(
                    icon = Icons.Rounded.DarkMode,
                    label = stringResource(Res.string.theme_dark),
                    description = stringResource(Res.string.theme_dark_desc),
                    selected = current.mode == ThemeMode.Dark,
                    onClick = { onModeSelect(ThemeMode.Dark) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.theme_done)) }
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
