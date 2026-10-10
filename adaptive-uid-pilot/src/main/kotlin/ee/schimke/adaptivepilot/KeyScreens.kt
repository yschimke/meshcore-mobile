@file:OptIn(androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi::class)

package ee.schimke.adaptivepilot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.*
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Independent Compose design proposals; no UID documents are loaded by this screen. */
@Composable
fun KeyScreen(
  screen: String,
  dark: Boolean = false,
  initialDetail: Boolean = false,
  initialSelectedIndex: Int = 0,
  failure: Boolean = false,
  onAction: (String, String) -> Unit = { _, _ -> },
) {
  val spec = screenSpecs.getValue(screen)
  require(initialSelectedIndex in spec.panels.indices)
  val window = currentWindowAdaptiveInfoV2()
  MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
    Scaffold { padding ->
      val navigator =
        rememberListDetailPaneScaffoldNavigator<Int>(
          scaffoldDirective = calculatePaneScaffoldDirective(window),
          initialDestinationHistory =
            listOf(
              ThreePaneScaffoldDestinationItem(
                if (initialDetail) ListDetailPaneScaffoldRole.Detail
                else ListDetailPaneScaffoldRole.List,
                initialSelectedIndex,
              )
            ),
        )
      val selected = navigator.currentDestination?.contentKey ?: 0
      val scope = rememberCoroutineScope()
      ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        modifier = Modifier.fillMaxSize().padding(padding),
        listPane = {
          AnimatedPane {
            ScreenColumn("$screen-list") {
              Text("MeshCore", style = MaterialTheme.typography.labelLarge)
              Text(spec.title, style = MaterialTheme.typography.headlineMedium)
              Text(spec.summary, style = MaterialTheme.typography.bodyLarge)
              InfoCard(spec.statusTitle, spec.status)
              spec.panels.forEachIndexed { index, panel ->
                Button(
                  onClick = {
                    scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, index) }
                  },
                  modifier = Modifier.fillMaxWidth().testTag("panel-$index"),
                ) {
                  Text(panel.title)
                }
              }
              Text(spec.note, style = MaterialTheme.typography.bodyMedium)
            }
          }
        },
        detailPane = {
          AnimatedPane {
            val panel = spec.panels[selected]
            ScreenColumn("$screen-detail") {
              Text(panel.title, style = MaterialTheme.typography.headlineMedium)
              Text(panel.subtitle, style = MaterialTheme.typography.titleLarge)
              InfoCard(panel.cardTitle, panel.body)
              when (screen) {
                "scanner" -> {
                  var host by rememberSaveable { mutableStateOf("mesh.local:5000") }
                  if (selected == 1) {
                    TextField(
                      host,
                      { host = it },
                      Modifier.fillMaxWidth().testTag("input"),
                      singleLine = true,
                      label = { Text("Host and port") },
                    )
                  }
                  if (failure)
                    Text(
                      "Connection failed. Check the device and try again.",
                      style = MaterialTheme.typography.bodyLarge,
                    )
                  Button(
                    onClick = { onAction("connect", if (selected == 1) host else panel.title) },
                    enabled = selected != 2 && (selected != 1 || host.isNotBlank()),
                    modifier = Modifier.testTag("action"),
                  ) {
                    Text(if (failure) "Try again" else "Connect")
                  }
                }
                "channels",
                "commands" -> {
                  var draft by rememberSaveable(selected) { mutableStateOf("") }
                  TextField(
                    draft,
                    { draft = it },
                    Modifier.fillMaxWidth().testTag("input"),
                    label = { Text(if (screen == "commands") "Command" else "Message") },
                  )
                  Button(
                    onClick = {
                      onAction(panel.title, draft)
                      draft = ""
                    },
                    enabled = draft.isNotBlank(),
                    modifier = Modifier.testTag("action"),
                  ) {
                    Text(if (screen == "commands") "Run command" else "Send")
                  }
                }
                "settings" -> {
                  if (selected == 0) {
                    var buzzer by rememberSaveable { mutableStateOf(true) }
                    Text("Buzzer", style = MaterialTheme.typography.titleMedium)
                    Switch(
                      buzzer,
                      {
                        buzzer = it
                        onAction("buzzer", it.toString())
                      },
                      Modifier.testTag("buzzer").semantics { contentDescription = "Buzzer" },
                    )
                  } else
                    Text(
                      "Reconnect to discover the device's supported settings.",
                      style = MaterialTheme.typography.bodyLarge,
                    )
                }
                "cached" ->
                  Text(
                    "Read only · Connect to send messages or change settings.",
                    style = MaterialTheme.typography.bodyLarge,
                  )
                else ->
                  Text(
                    "Live data updates when your device is connected.",
                    style = MaterialTheme.typography.bodyLarge,
                  )
              }
              Button(
                onClick = {
                  scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.List, selected) }
                },
                modifier = Modifier.testTag("back-to-list"),
              ) {
                Text("Back to ${spec.title.lowercase()}")
              }
            }
          }
        },
      )
    }
  }
}

@Composable
private fun ScreenColumn(
  tag: String,
  content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
  Column(
    Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()).testTag(tag),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    content = content,
  )
}

@Composable
private fun InfoCard(title: String, body: String) {
  Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(title, style = MaterialTheme.typography.titleMedium)
      Text(body, style = MaterialTheme.typography.bodyLarge)
    }
  }
}

private data class Panel(
  val title: String,
  val subtitle: String,
  val cardTitle: String,
  val body: String,
)

private data class ScreenSpec(
  val title: String,
  val summary: String,
  val statusTitle: String,
  val status: String,
  val note: String,
  val panels: List<Panel>,
)

private val screenSpecs =
  mapOf(
    "scanner" to
      ScreenSpec(
        "Connect",
        "Choose how to connect your MeshCore radio.",
        "Saved device",
        "Trail radio · Last connected today",
        "Bluetooth needs permission. USB needs a connected radio. TCP uses a host and port.",
        listOf(
          Panel(
            "Bluetooth",
            "Nearby radios",
            "Trail radio",
            "Ready to pair · Strong signal\nChoose Connect to request Bluetooth access.",
          ),
          Panel(
            "TCP",
            "Connect over your network",
            "Network radio",
            "Enter the radio's host and port.\nDefault port: 5000",
          ),
          Panel(
            "USB",
            "Connect a radio by cable",
            "No USB radio connected",
            "Attach a supported radio and allow USB access.",
          ),
        ),
      ),
    "device" to
      ScreenSpec(
        "Device",
        "Your radio and conversations at a glance.",
        "Trail radio · Connected",
        "Battery 82% · 3.98 V\n12 contacts · 2 channels",
        "Choose a section to inspect your device.",
        listOf(
          Panel(
            "Radio status",
            "Trail radio",
            "Radio",
            "Frequency 869.525 MHz\nBandwidth 250 kHz · Spreading factor 11\nBattery 82% · 3.98 V",
          ),
          Panel(
            "Conversations",
            "Stay in touch",
            "Recent activity",
            "Alice · At the north trailhead\n#Camp · Dinner at 18:00\nBob · Base camp is ready",
          ),
          Panel(
            "Rooms and repeaters",
            "Mesh infrastructure",
            "Nearby",
            "Ridge repeater · Heard 2 minutes ago\nCamp room · 4 participants",
          ),
        ),
      ),
    "channels" to
      ScreenSpec(
        "Channels",
        "Shared conversations on your mesh.",
        "Trail radio · Connected",
        "2 channels · Shared conversations on this device",
        "Choose a channel to read and send messages.",
        listOf(
          Panel(
            "#Camp",
            "Shared channel",
            "Today · 17:42",
            "Alice: Dinner at 18:00.\nYou: Bringing the stove.\nBob: See you at camp.",
          ),
          Panel(
            "#Trail",
            "Shared channel",
            "Today · 16:15",
            "Charlie: North trail is clear.\nYou: Thanks for the update.",
          ),
        ),
      ),
    "commands" to
      ScreenSpec(
        "Commands",
        "Manage a repeater or room with text commands.",
        "Trail radio · Connected",
        "Commands are sent to the selected device.",
        "Verify the destination before sending a command.",
        listOf(
          Panel(
            "Ridge repeater",
            "Command session",
            "Recent response",
            "> version\nMeshCore repeater\n> battery\nBattery: 4.01 V",
          ),
          Panel(
            "Camp room",
            "Command session",
            "Recent response",
            "> help\nAvailable commands: help, version, battery\nReady for your next command.",
          ),
        ),
      ),
    "settings" to
      ScreenSpec(
        "Device settings",
        "Settings discovered from the connected device.",
        "Trail radio · Connected",
        "Only supported settings are shown.",
        "Changing a setting sends an update to your radio.",
        listOf(
          Panel(
            "Notifications",
            "Supported device setting",
            "Buzzer",
            "Play an audible alert for incoming messages.",
          ),
          Panel(
            "Unavailable settings",
            "Discovery unavailable",
            "No settings available",
            "The device did not return supported settings.\nNo changes can be made in this state.",
          ),
        ),
      ),
    "cached" to
      ScreenSpec(
        "Offline device",
        "Inspect saved information without a radio connection.",
        "Trail radio · Offline",
        "Saved today at 14:20\nInformation may be out of date.",
        "This is a read-only snapshot. Connect from the scanner to refresh it.",
        listOf(
          Panel(
            "Saved radio status",
            "Last known state",
            "Radio snapshot",
            "Battery 82% · 3.98 V\nFrequency 869.525 MHz\nSaved today at 14:20",
          ),
          Panel(
            "Saved contacts",
            "Last known contacts",
            "Contacts snapshot",
            "Alice · Direct\nBob · 2 hops\nCharlie · 1 hop",
          ),
        ),
      ),
  )
