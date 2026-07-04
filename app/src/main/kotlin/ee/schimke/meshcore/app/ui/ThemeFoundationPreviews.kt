package ee.schimke.meshcore.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ee.schimke.meshcore.app.ui.theme.MeshcoreTheme

// ---------------------------------------------------------------------------
// Theme-foundation showcase.
//
// Renders a theme's colour roles, surface-elevation strip and type ramp as a
// single tile, for both the branded MeshcoreTheme and the untinted Material 3
// baseline (light + dark each). Published in the design catalog so the theme
// foundations sit alongside the components that use them.
// ---------------------------------------------------------------------------

@Composable
private fun Swatch(label: String, color: Color, onColor: Color) {
  Box(
    Modifier.size(width = 52.dp, height = 40.dp).clip(RoundedCornerShape(8.dp)).background(color),
    contentAlignment = Alignment.Center,
  ) {
    Text(label, color = onColor, style = MaterialTheme.typography.labelSmall)
  }
}

@Composable
private fun RowScope.SurfaceBand(label: String, color: Color, onColor: Color, outline: Color) {
  Box(
    Modifier.weight(1f)
      .height(32.dp)
      .clip(RoundedCornerShape(6.dp))
      .background(color)
      .border(1.dp, outline, RoundedCornerShape(6.dp)),
    contentAlignment = Alignment.Center,
  ) {
    Text(label, color = onColor, style = MaterialTheme.typography.labelSmall)
  }
}

@Composable
private fun ThemeFoundation(title: String, tagline: String) {
  val cs = MaterialTheme.colorScheme
  Surface(color = cs.background) {
    Column(
      Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Column {
        Text(title, style = MaterialTheme.typography.titleLarge, color = cs.primary)
        Text(tagline, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Swatch("P", cs.primary, cs.onPrimary)
        Swatch("PC", cs.primaryContainer, cs.onPrimaryContainer)
        Swatch("S", cs.secondary, cs.onSecondary)
        Swatch("T", cs.tertiary, cs.onTertiary)
        Swatch("Sv", cs.surfaceVariant, cs.onSurfaceVariant)
        Swatch("E", cs.error, cs.onError)
      }
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SurfaceBand("0", cs.surface, cs.onSurface, cs.outlineVariant)
        SurfaceBand("1", cs.surfaceContainerLowest, cs.onSurface, cs.outlineVariant)
        SurfaceBand("2", cs.surfaceContainerLow, cs.onSurface, cs.outlineVariant)
        SurfaceBand("3", cs.surfaceContainer, cs.onSurface, cs.outlineVariant)
        SurfaceBand("4", cs.surfaceContainerHigh, cs.onSurface, cs.outlineVariant)
        SurfaceBand("5", cs.surfaceContainerHighest, cs.onSurface, cs.outlineVariant)
      }
      Column {
        Text("Display", style = MaterialTheme.typography.displaySmall, color = cs.onBackground)
        Text("Headline", style = MaterialTheme.typography.headlineSmall, color = cs.onBackground)
        Text("Title", style = MaterialTheme.typography.titleMedium, color = cs.onBackground)
        Text(
          "Body — the quick brown fox jumps over the lazy dog",
          style = MaterialTheme.typography.bodyMedium,
          color = cs.onSurfaceVariant,
        )
        Text("LABEL", style = MaterialTheme.typography.labelLarge, color = cs.primary)
      }
    }
  }
}

@Preview(name = "Foundation — MeshCore light", widthDp = 360)
@Composable
fun ThemeFoundationMeshcoreLightPreview() {
  MeshcoreTheme(darkTheme = false) {
    ThemeFoundation("MeshCore", "Light · Orbitron / Space Grotesk / JetBrains Mono")
  }
}

@Preview(name = "Foundation — MeshCore dark", widthDp = 360)
@Composable
fun ThemeFoundationMeshcoreDarkPreview() {
  MeshcoreTheme(darkTheme = true) {
    ThemeFoundation("MeshCore", "Dark · Orbitron / Space Grotesk / JetBrains Mono")
  }
}

@Preview(name = "Foundation — Material 3 light", widthDp = 360)
@Composable
fun ThemeFoundationMaterial3LightPreview() {
  MaterialTheme(colorScheme = lightColorScheme()) {
    ThemeFoundation("Material 3", "Light · baseline (untinted) reference")
  }
}

@Preview(name = "Foundation — Material 3 dark", widthDp = 360)
@Composable
fun ThemeFoundationMaterial3DarkPreview() {
  MaterialTheme(colorScheme = darkColorScheme()) {
    ThemeFoundation("Material 3", "Dark · baseline (untinted) reference")
  }
}
