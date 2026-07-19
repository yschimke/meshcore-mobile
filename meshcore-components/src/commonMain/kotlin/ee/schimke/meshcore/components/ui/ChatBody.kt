package ee.schimke.meshcore.components.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ee.schimke.meshcore.components.generated.resources.Res
import ee.schimke.meshcore.components.generated.resources.cd_back
import ee.schimke.meshcore.components.generated.resources.chat_hint_message
import ee.schimke.meshcore.components.ui.icons.MeshIcons
import org.jetbrains.compose.resources.stringResource

/**
 * Stateless chat screen body: a top bar (title + optional subtitle + back), the scrollable message
 * list, and the input bar. Shared by the contact (1:1), channel (group), and commands chat screens
 * — the stateful wrappers in `:app` own the transport/DB and supply the messages, the draft, and
 * the send / retry / back callbacks. This is the design-parity render subject (see
 * `docs/design-parity.md`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatBody(
  title: String,
  messages: List<ChatMessage>,
  draft: String,
  onDraftChange: (String) -> Unit,
  onSend: () -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  inputEnabled: Boolean = true,
  inputPlaceholder: String = stringResource(Res.string.chat_hint_message),
  onRetry: ((ChatMessage) -> Unit)? = null,
  actions: @Composable RowScope.() -> Unit = {},
) {
  Scaffold(
    modifier = modifier,
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
              Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(MeshIcons.ArrowBack, stringResource(Res.string.cd_back))
          }
        },
        actions = actions,
        colors =
          TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
      )
    },
  ) { padding ->
    Column(modifier = Modifier.fillMaxSize().padding(padding).imePadding()) {
      ChatMessageList(messages = messages, modifier = Modifier.weight(1f), onRetry = onRetry)
      ChatInput(
        value = draft,
        onValueChange = onDraftChange,
        onSend = onSend,
        enabled = inputEnabled,
        placeholder = inputPlaceholder,
      )
    }
  }
}
