@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ee.schimke.meshcore.components.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ee.schimke.meshcore.components.ui.icons.MeshIcons
import ee.schimke.meshcore.components.ui.theme.Dimens
import ee.schimke.meshcore.components.ui.theme.Section
import ee.schimke.meshcore.components.ui.theme.SectionStates
import ee.schimke.meshcore.core.model.BatteryInfo
import ee.schimke.meshcore.core.model.ChannelInfo
import ee.schimke.meshcore.core.model.Contact
import ee.schimke.meshcore.core.model.ContactType
import ee.schimke.meshcore.core.model.RadioSettings
import ee.schimke.meshcore.core.model.SelfInfo

const val COMMANDS_CHANNEL_NAME = "meshcore-commands"

// DeviceBody + helpers, extracted from the app so the Device screen renders on
// the CMP desktop target (design-parity). The stateful DeviceScreen/ConnectedDevice
// wrappers stay in :app.

sealed class LastMessageInfo {
  abstract val text: String
  abstract val snr: Int

  data class Dm(
    val contactKeyHex: String,
    val contactName: String?,
    override val text: String,
    override val snr: Int,
  ) : LastMessageInfo()

  data class Channel(
    val channelIndex: Int,
    val channelName: String?,
    val sender: String?,
    override val text: String,
    override val snr: Int,
  ) : LastMessageInfo()
}

@Composable
fun DeviceBody(
  self: SelfInfo?,
  battery: BatteryInfo?,
  radio: RadioSettings?,
  contacts: List<Contact>,
  contactsLoading: Boolean = false,
  contactsRefreshing: Boolean = false,
  channels: List<ChannelInfo> = emptyList(),
  contactedKeys: Set<String> = emptySet(),
  contactedChannelIndices: Set<Int> = emptySet(),
  sectionStates: SectionStates = SectionStates(),
  onSectionExpandedChange: (Section, Boolean) -> Unit = { _, _ -> },
  onSectionShowAllChange: (Section, Boolean) -> Unit = { _, _ -> },
  lastMessage: LastMessageInfo? = null,
  onLastMessageClick: (LastMessageInfo) -> Unit = {},
  onContactClick: (Contact) -> Unit = {},
  onChannelClick: (ChannelInfo) -> Unit = {},
  onCommandsClick: () -> Unit = {},
  onSettingsClick: () -> Unit = {},
  onDisconnect: () -> Unit,
  onOpenThemePicker: () -> Unit = {},
  warnings: List<String> = emptyList(),
  onDismissWarning: (String) -> Unit = {},
) {
  val scroll = rememberScrollState()

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          if (self != null) {
            Text(text = self.name, style = MaterialTheme.typography.titleLarge)
          } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
              CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
              Spacer(Modifier.size(Dimens.S))
              Text(
                text = "Loading\u2026",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        },
        actions = {
          var toolsMenuOpen by remember { mutableStateOf(false) }
          Box {
            IconButton(onClick = { toolsMenuOpen = true }) {
              Icon(imageVector = MeshIcons.Terminal, contentDescription = "Tools")
            }
            DropdownMenu(expanded = toolsMenuOpen, onDismissRequest = { toolsMenuOpen = false }) {
              DropdownMenuItem(
                text = { Text("Commands") },
                leadingIcon = { Icon(MeshIcons.Terminal, contentDescription = null) },
                onClick = {
                  toolsMenuOpen = false
                  onCommandsClick()
                },
              )
              DropdownMenuItem(
                text = { Text("Device Settings") },
                leadingIcon = { Icon(MeshIcons.Settings, contentDescription = null) },
                onClick = {
                  toolsMenuOpen = false
                  onSettingsClick()
                },
              )
            }
          }
          IconButton(onClick = onOpenThemePicker) {
            Icon(imageVector = MeshIcons.Contrast, contentDescription = "Theme")
          }
          IconButton(onClick = onDisconnect) {
            Icon(imageVector = MeshIcons.Logout, contentDescription = "Disconnect")
          }
        },
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
          ),
      )
    },
    containerColor = MaterialTheme.colorScheme.surface,
  ) { padding ->
    Column(
      modifier =
        Modifier.padding(padding)
          .fillMaxSize()
          .verticalScrollbar(scroll)
          .verticalScroll(scroll)
          .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.S),
      verticalArrangement = Arrangement.spacedBy(Dimens.CardGap),
    ) {
      DeviceSummaryCard(self = self, radio = radio, battery = battery)

      warnings.forEach { warning ->
        WarningBanner(warning, onDismiss = { onDismissWarning(warning) })
      }

      lastMessage?.let { LastMessageBanner(it, onClick = { onLastMessageClick(it) }) }

      // Split contacts by type
      val chatContacts = contacts.filter { it.type == ContactType.CHAT }
      val rooms = contacts.filter { it.type == ContactType.ROOM }
      val repeaters = contacts.filter { it.type == ContactType.REPEATER }
      val sensors = contacts.filter { it.type == ContactType.SENSOR }

      // Subtle progress bar while refreshing with cached data visible
      AnimatedVisibility(
        visible = contactsRefreshing,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
      ) {
        LinearProgressIndicator(
          modifier = Modifier.fillMaxWidth(),
          color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
          trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
      }

      // --- Commands channel (hidden from regular channels list) ---
      val regularChannels = channels.filter { it.name != COMMANDS_CHANNEL_NAME }

      // --- Channels ---
      val visibleChannels =
        if (sectionStates.channelsShowAll) regularChannels
        else regularChannels.filter { it.index in contactedChannelIndices }
      CollapsibleSectionHeader(
        text = "Channels (${visibleChannels.size})",
        expanded = sectionStates.channelsExpanded,
        onToggle = { onSectionExpandedChange(Section.CHANNELS, !sectionStates.channelsExpanded) },
      ) {
        if (regularChannels.isNotEmpty()) {
          FilterChip(
            selected = !sectionStates.channelsShowAll,
            onClick = { onSectionShowAllChange(Section.CHANNELS, !sectionStates.channelsShowAll) },
            label = { Text(if (sectionStates.channelsShowAll) "All" else "Favourited") },
          )
        }
      }
      AnimatedVisibility(
        visible = sectionStates.channelsExpanded,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.CardGap)) {
          if (visibleChannels.isEmpty()) {
            Text(
              text =
                if (regularChannels.isEmpty()) "No channels configured"
                else "No favourited channels",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(vertical = 8.dp),
            )
          } else {
            visibleChannels.forEach { channel ->
              ChannelRow(channel, onClick = { onChannelClick(channel) })
            }
          }
        }
      }

      // --- Contacts (DM-able peers) ---
      val messagedContacts =
        if (sectionStates.contactsShowAll) chatContacts
        else chatContacts.filter { it.publicKey.toHex() in contactedKeys }
      CollapsibleSectionHeader(
        text = if (contactsLoading) "Contacts" else "Contacts (${messagedContacts.size})",
        expanded = sectionStates.contactsExpanded,
        onToggle = { onSectionExpandedChange(Section.CONTACTS, !sectionStates.contactsExpanded) },
      ) {
        if (!contactsLoading && chatContacts.isNotEmpty()) {
          FilterChip(
            selected = !sectionStates.contactsShowAll,
            onClick = { onSectionShowAllChange(Section.CONTACTS, !sectionStates.contactsShowAll) },
            label = { Text(if (sectionStates.contactsShowAll) "All" else "Favourited") },
          )
        }
      }
      AnimatedVisibility(
        visible = sectionStates.contactsExpanded,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.CardGap)) {
          if (contactsLoading) {
            LoadingPlaceholder("Fetching contacts\u2026")
          } else if (messagedContacts.isEmpty()) {
            ContactListEmpty()
          } else {
            messagedContacts.forEach { contact ->
              ContactRow(contact, onClick = { onContactClick(contact) })
            }
          }
        }
      }

      // --- Rooms ---
      if (contactsLoading || rooms.isNotEmpty()) {
        val visibleRooms =
          if (sectionStates.roomsShowAll) rooms
          else rooms.filter { it.publicKey.toHex() in contactedKeys }
        CollapsibleSectionHeader(
          text = if (contactsLoading) "Rooms" else "Rooms (${visibleRooms.size})",
          expanded = sectionStates.roomsExpanded,
          onToggle = { onSectionExpandedChange(Section.ROOMS, !sectionStates.roomsExpanded) },
        ) {
          if (!contactsLoading && rooms.isNotEmpty()) {
            FilterChip(
              selected = !sectionStates.roomsShowAll,
              onClick = { onSectionShowAllChange(Section.ROOMS, !sectionStates.roomsShowAll) },
              label = { Text(if (sectionStates.roomsShowAll) "All" else "Joined") },
            )
          }
        }
        AnimatedVisibility(
          visible = sectionStates.roomsExpanded,
          enter = expandVertically() + fadeIn(),
          exit = shrinkVertically() + fadeOut(),
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(Dimens.CardGap)) {
            if (!contactsLoading) {
              if (visibleRooms.isEmpty()) {
                Text(
                  text = "No rooms joined yet",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(vertical = 8.dp),
                )
              } else {
                visibleRooms.forEach { contact ->
                  ContactRow(contact, onClick = { onContactClick(contact) })
                }
              }
            }
          }
        }
      }

      // --- Repeaters ---
      if (!contactsLoading && repeaters.isNotEmpty()) {
        val visibleRepeaters =
          if (sectionStates.repeatersShowAll) repeaters
          else repeaters.filter { it.publicKey.toHex() in contactedKeys }
        CollapsibleSectionHeader(
          text = "Repeaters (${visibleRepeaters.size})",
          expanded = sectionStates.repeatersExpanded,
          onToggle = {
            onSectionExpandedChange(Section.REPEATERS, !sectionStates.repeatersExpanded)
          },
        ) {
          FilterChip(
            selected = !sectionStates.repeatersShowAll,
            onClick = {
              onSectionShowAllChange(Section.REPEATERS, !sectionStates.repeatersShowAll)
            },
            label = { Text(if (sectionStates.repeatersShowAll) "All" else "Joined") },
          )
        }
        AnimatedVisibility(
          visible = sectionStates.repeatersExpanded,
          enter = expandVertically() + fadeIn(),
          exit = shrinkVertically() + fadeOut(),
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(Dimens.CardGap)) {
            if (visibleRepeaters.isEmpty()) {
              Text(
                text = "No repeaters joined yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
              )
            } else {
              visibleRepeaters.forEach { contact -> ContactRow(contact) }
            }
          }
        }
      }

      // --- Sensors ---
      if (!contactsLoading && sensors.isNotEmpty()) {
        CollapsibleSectionHeader(
          text = "Sensors (${sensors.size})",
          expanded = sectionStates.sensorsExpanded,
          onToggle = { onSectionExpandedChange(Section.SENSORS, !sectionStates.sensorsExpanded) },
        )
        AnimatedVisibility(
          visible = sectionStates.sensorsExpanded,
          enter = expandVertically() + fadeIn(),
          exit = shrinkVertically() + fadeOut(),
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(Dimens.CardGap)) {
            sensors.forEach { contact -> ContactRow(contact) }
          }
        }
      }

      Spacer(Modifier.size(Dimens.L))
    }
  }
}

@Composable
private fun LoadingPlaceholder(text: String) {
  Box(
    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
    contentAlignment = Alignment.Center,
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      CircularProgressIndicator(modifier = Modifier.size(28.dp))
      Spacer(Modifier.height(8.dp))
      Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun CollapsibleSectionHeader(
  text: String,
  expanded: Boolean,
  onToggle: () -> Unit,
  trailing: @Composable (() -> Unit)? = null,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = Dimens.XS),
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.weight(1f),
    )
    trailing?.invoke()
    Icon(
      imageVector = if (expanded) MeshIcons.ExpandLess else MeshIcons.ExpandMore,
      contentDescription = if (expanded) "Collapse" else "Expand",
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(20.dp),
    )
  }
}

@Composable
private fun LastMessageBanner(info: LastMessageInfo, onClick: () -> Unit) {
  val origin =
    when (info) {
      is LastMessageInfo.Dm -> info.contactName ?: info.contactKeyHex.take(12)
      is LastMessageInfo.Channel ->
        buildString {
          append("#")
          append(info.channelName ?: info.channelIndex.toString())
          info.sender?.let { append(" · $it") }
        }
    }
  OutlinedCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
      Icon(
        imageVector = MeshIcons.Message,
        contentDescription = "New message",
        tint = MaterialTheme.colorScheme.primary,
      )
      Spacer(Modifier.size(Dimens.S))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = origin,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
        )
        Text(
          text = info.text,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 2,
          overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
private fun WarningBanner(text: String, onDismiss: () -> Unit) {
  androidx.compose.material3.Surface(
    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    color = MaterialTheme.colorScheme.tertiaryContainer,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
      Icon(
        imageVector = MeshIcons.WarningAmber,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.size(18.dp),
      )
      Spacer(Modifier.size(Dimens.S))
      Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.weight(1f),
      )
      IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
        Icon(
          imageVector = MeshIcons.Close,
          contentDescription = "Dismiss",
          tint = MaterialTheme.colorScheme.onTertiaryContainer,
          modifier = Modifier.size(16.dp),
        )
      }
    }
  }
}
