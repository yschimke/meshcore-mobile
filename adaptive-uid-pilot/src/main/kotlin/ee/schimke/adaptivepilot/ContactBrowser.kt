@file:OptIn(androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi::class)

package ee.schimke.adaptivepilot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// Design proposal with fixed English sample data, not a live session or navigation destination.
// The Compose implementation is independent of ui-builder/designs/contacts.uid.
@Composable
fun ContactBrowser(
  dark: Boolean = false,
  initialDetail: Boolean = false,
  initialSelectedIndex: Int = 0,
  onOpen: (String) -> Unit = {},
  onSend: (String, String) -> Unit = { _, _ -> },
) {
  val windowInfo = currentWindowAdaptiveInfoV2()
  require(initialSelectedIndex in entries.indices)
  MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
    Scaffold { padding ->
      val navigator =
        rememberListDetailPaneScaffoldNavigator<Int>(
          scaffoldDirective = calculatePaneScaffoldDirective(windowInfo),
          initialDestinationHistory =
            listOf(
              androidx.compose.material3.adaptive.layout.ThreePaneScaffoldDestinationItem(
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
            Column(
              Modifier.fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
                .testTag("contacts-list"),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text("MeshCore", style = MaterialTheme.typography.labelLarge)
              Text("Contacts", style = MaterialTheme.typography.headlineMedium)
              Text("Choose a contact on your mesh.", style = MaterialTheme.typography.bodyLarge)
              entries.forEachIndexed { index, entry ->
                Button(
                  onClick = {
                    scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, index) }
                  },
                  modifier = Modifier.fillMaxWidth().testTag("entry-$index"),
                ) {
                  Text(entry.title)
                }
              }
            }
          }
        },
        detailPane = {
          AnimatedPane {
            val entry = entries[selected]
            Column(
              Modifier.fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
                .testTag("contacts-detail"),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text(entry.title, style = MaterialTheme.typography.headlineMedium)
              Text(entry.subtitle, style = MaterialTheme.typography.titleLarge)
              Text(entry.status, style = MaterialTheme.typography.labelLarge)
              Text(entry.body, style = MaterialTheme.typography.bodyLarge)
              var draft by rememberSaveable(selected) { mutableStateOf("") }
              TextField(
                draft,
                { draft = it },
                Modifier.fillMaxWidth().testTag("message-input"),
                label = { Text("Message") },
              )
              Button(
                onClick = {
                  onSend(entry.id, draft)
                  draft = ""
                },
                enabled = draft.isNotBlank(),
                modifier = Modifier.testTag("send-message"),
              ) {
                Text("Send")
              }
              Button(onClick = { onOpen(entry.id) }, modifier = Modifier.testTag("open-entry")) {
                Text("Contact details")
              }
              Button(
                onClick = {
                  scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.List, selected) }
                },
                modifier = Modifier.testTag("back-to-list"),
              ) {
                Text("Back to contacts")
              }
            }
          }
        },
      )
    }
  }
}

private data class Entry(
  val id: String,
  val title: String,
  val subtitle: String,
  val status: String,
  val body: String,
)

private val entries =
  listOf(
    Entry(
      "alice",
      "Alice",
      "Trail check-in",
      "Direct · heard 2 minutes ago",
      "Alice: At the north trailhead.\nYou: On my way. See you in ten.",
    ),
    Entry(
      "bob",
      "Bob",
      "Camp coordination",
      "2 hops · heard 5 minutes ago",
      "Bob: Base camp is ready.\nYou: Bringing the spare battery.",
    ),
    Entry(
      "charlie",
      "Charlie",
      "Evening radio check",
      "1 hop · heard 12 minutes ago",
      "Charlie: Radio check at 19:00?\nYou: I will be listening.",
    ),
  )
