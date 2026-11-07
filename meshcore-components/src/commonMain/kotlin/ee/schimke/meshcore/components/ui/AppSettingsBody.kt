package ee.schimke.meshcore.components.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ee.schimke.meshcore.components.generated.resources.Res
import ee.schimke.meshcore.components.generated.resources.app_settings_about
import ee.schimke.meshcore.components.generated.resources.app_settings_developer
import ee.schimke.meshcore.components.generated.resources.app_settings_developer_mode
import ee.schimke.meshcore.components.generated.resources.app_settings_developer_mode_desc
import ee.schimke.meshcore.components.generated.resources.app_settings_developer_taps
import ee.schimke.meshcore.components.generated.resources.app_settings_developer_unlocked
import ee.schimke.meshcore.components.generated.resources.app_settings_licenses
import ee.schimke.meshcore.components.generated.resources.app_settings_title
import ee.schimke.meshcore.components.generated.resources.app_settings_version
import ee.schimke.meshcore.components.generated.resources.cd_back
import ee.schimke.meshcore.components.generated.resources.cd_collapse
import ee.schimke.meshcore.components.generated.resources.cd_expand
import ee.schimke.meshcore.components.ui.icons.MeshIcons
import ee.schimke.meshcore.components.ui.theme.Dimens
import org.jetbrains.compose.resources.stringResource

/** Taps on the version row that unlock the developer options, as on Android's build number. */
const val DEVELOPER_UNLOCK_TAPS = 7

/** Taps left before the version row starts counting down out loud. */
private const val DEVELOPER_HINT_AFTER = 3

/**
 * Stateless app Settings / About screen: the app version and licenses, plus a collapsed Developer
 * section that only appears once [developerMode] is on. Tapping the version row
 * [DEVELOPER_UNLOCK_TAPS] times calls [onDeveloperModeChange] with `true`; the Developer section's
 * switch turns it back off (hiding the section again).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsBody(
  versionName: String,
  developerMode: Boolean,
  onDeveloperModeChange: (Boolean) -> Unit,
  onOpenLicenses: () -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  initialDeveloperExpanded: Boolean = false,
) {
  var taps by rememberSaveable { mutableIntStateOf(0) }
  var developerExpanded by rememberSaveable { mutableStateOf(initialDeveloperExpanded) }
  Scaffold(
    modifier = modifier,
    topBar = {
      TopAppBar(
        title = { Text(stringResource(Res.string.app_settings_title)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(MeshIcons.ArrowBack, contentDescription = stringResource(Res.string.cd_back))
          }
        },
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
          ),
      )
    },
    containerColor = MaterialTheme.colorScheme.surface,
  ) { padding ->
    Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
      SettingsSectionLabel(stringResource(Res.string.app_settings_about))
      val remaining = DEVELOPER_UNLOCK_TAPS - taps
      ListItem(
        headlineContent = { Text(stringResource(Res.string.app_settings_version)) },
        supportingContent = {
          Text(
            when {
              developerMode && taps > 0 ->
                versionName + " · " + stringResource(Res.string.app_settings_developer_unlocked)
              !developerMode && taps >= DEVELOPER_HINT_AFTER ->
                stringResource(Res.string.app_settings_developer_taps, remaining)
              else -> versionName
            },
          )
        },
        modifier =
          Modifier.clickable {
            if (!developerMode) {
              taps += 1
              if (taps >= DEVELOPER_UNLOCK_TAPS) {
                developerExpanded = true
                onDeveloperModeChange(true)
              }
            }
          },
      )
      ListItem(
        headlineContent = { Text(stringResource(Res.string.app_settings_licenses)) },
        modifier = Modifier.clickable(onClick = onOpenLicenses),
      )

      if (developerMode) {
        HorizontalDivider(Modifier.padding(vertical = Dimens.S))
        ListItem(
          headlineContent = {
            Text(
              stringResource(Res.string.app_settings_developer),
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.primary,
            )
          },
          trailingContent = {
            Icon(
              if (developerExpanded) MeshIcons.ExpandLess else MeshIcons.ExpandMore,
              contentDescription =
                stringResource(
                  if (developerExpanded) Res.string.cd_collapse else Res.string.cd_expand,
                ),
            )
          },
          modifier = Modifier.clickable { developerExpanded = !developerExpanded },
        )
        AnimatedVisibility(visible = developerExpanded) {
          ListItem(
            headlineContent = { Text(stringResource(Res.string.app_settings_developer_mode)) },
            supportingContent = {
              Text(stringResource(Res.string.app_settings_developer_mode_desc))
            },
            trailingContent = {
              Switch(
                checked = true,
                onCheckedChange = { enabled ->
                  if (!enabled) {
                    taps = 0
                    onDeveloperModeChange(false)
                  }
                },
              )
            },
          )
        }
      }
    }
  }
}

@Composable
private fun SettingsSectionLabel(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.S),
  )
}
