package ee.schimke.meshcore.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GroupOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListHeaderDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextDefaults
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import ee.schimke.meshcore.grpc.ContactMsg
import ee.schimke.meshcore.grpc.ContactType
import ee.schimke.meshcore.wear.ui.theme.WearDimens

// --- Stateful entry point ---------------------------------------------------

@Composable
fun ContactsScreen(
    viewModel: WearViewModel = viewModel(),
    onContactSelected: (ContactMsg) -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val contacts = (state as? WearUiState.Connected)?.contacts
        ?.filter { it.type == ContactType.CHAT }
        ?: emptyList()

    ContactsBody(contacts = contacts, onContactSelected = onContactSelected)
}

// --- Stateless body (previewable) -------------------------------------------

@Composable
fun ContactsBody(
    contacts: List<ContactMsg>,
    onContactSelected: (ContactMsg) -> Unit = {},
) {
    val columnState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    ScreenScaffold(
        scrollState = columnState,
    ) { contentPadding ->
        TransformingLazyColumn(
            state = columnState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            if (contacts.isEmpty()) {
                item {
                    Icon(
                        imageVector = Icons.Rounded.GroupOff,
                        contentDescription = "No contacts",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(36.dp)
                            .minimumVerticalContentPadding(
                                TextDefaults.minimumTopListContentPadding
                            )
                            .transformedHeight(this, transformationSpec),
                    )
                }
                item {
                    Text(
                        text = "No chat contacts",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumVerticalContentPadding(
                                TextDefaults.minimumBottomListContentPadding
                            )
                            .transformedHeight(this, transformationSpec),
                    )
                }
            } else {
                item {
                    ListHeader(
                        modifier = Modifier.minimumVerticalContentPadding(
                            top = ListHeaderDefaults.minimumTopListContentPadding,
                            bottom = ListHeaderDefaults.minimumBottomListContentPadding,
                        ),
                    ) {
                        Text("Contacts")
                    }
                }
                items(contacts, key = { it.publicKey.toByteArray().contentHashCode() }) { contact ->
                    Button(
                        onClick = { onContactSelected(contact) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumVerticalContentPadding(
                                ButtonDefaults.minimumVerticalListContentPadding
                            )
                            .transformedHeight(this, transformationSpec),
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        transformation = SurfaceTransformation(transformationSpec),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(WearDimens.S),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                modifier = Modifier.size(WearDimens.IconSize),
                            )
                            Text(
                                text = contact.name.ifEmpty { "Unknown" },
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }
        }
    }
}
