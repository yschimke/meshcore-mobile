@file:SuppressLint("RestrictedApi")

package ee.schimke.meshcore.wear.widget

import android.annotation.SuppressLint
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.remote.creation.compose.action.Action
import androidx.compose.remote.creation.compose.layout.RemoteAlignment
import androidx.compose.remote.creation.compose.layout.RemoteArrangement
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.layout.RemoteColumn
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.layout.RemoteRow
import androidx.compose.remote.creation.compose.layout.RemoteText
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.background
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.modifier.fillMaxWidth
import androidx.compose.remote.creation.compose.modifier.height
import androidx.compose.remote.creation.compose.modifier.padding
import androidx.compose.remote.creation.compose.modifier.size
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.remote.creation.compose.state.rsp
import androidx.compose.remote.tooling.preview.RemotePreview
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.remote.material3.RemoteAppCard
import androidx.wear.compose.remote.material3.RemoteButton
import androidx.wear.compose.remote.material3.RemoteButtonGroup
import androidx.wear.compose.remote.material3.RemoteCard
import androidx.wear.compose.remote.material3.RemoteCardDefaults
import androidx.wear.compose.remote.material3.RemoteCircularProgressIndicator
import androidx.wear.compose.remote.material3.RemoteIcon
import androidx.wear.compose.remote.material3.RemoteMaterialTheme
import androidx.wear.compose.remote.material3.RemoteTextButton
import androidx.wear.compose.remote.material3.RemoteTextButtonDefaults
import androidx.wear.compose.remote.material3.RemoteTitleCard

@Preview(name = "Status — connected", widthDp = 192, heightDp = 192)
@Composable
fun StatusWidgetConnectedPreview() = RemotePreview {
    StatusWidgetContent(
        deviceName = "node-peak",
        connected = true,
        batteryPercent = 81,
        contactCount = 3,
    )
}

@Preview(name = "Status — disconnected", widthDp = 192, heightDp = 192)
@Composable
fun StatusWidgetDisconnectedPreview() = RemotePreview {
    StatusWidgetContent(
        deviceName = "node-peak",
        connected = false,
        batteryPercent = null,
        contactCount = 0,
    )
}

@Preview(name = "Status — low battery", widthDp = 192, heightDp = 192)
@Composable
fun StatusWidgetLowBatteryPreview() = RemotePreview {
    StatusWidgetContent(
        deviceName = "node-peak",
        connected = true,
        batteryPercent = 15,
        contactCount = 5,
    )
}

@Preview(name = "Component Catalog", widthDp = 192, heightDp = 192)
@Composable
fun WearComponentCatalogPreview() = RemotePreview {
    val surfaceContainer = Color(0xFF1A211F).rc
    val onSurface = Color(0xFFDDE4E1).rc
    val onSurfaceVariant = Color(0xFFBEC9C5).rc
    val primary = Color(0xFF53DBC9).rc
    val tertiary = Color(0xFFE0C38C).rc
    val errorRed = Color(0xFFFFB4AB).rc

    RemoteColumn(
        modifier = RemoteModifier
            .fillMaxSize()
            .background(surfaceContainer)
            .padding(12.rdp),
        verticalArrangement = RemoteArrangement.spacedBy(4.rdp)
    ) {
        RemoteText(
            text = "Wear Catalog".rs,
            color = primary,
            fontSize = 14.rsp
        )

        RemoteText(
            text = "Typography & Colors".rs,
            color = onSurface,
            fontSize = 12.rsp
        )

        RemoteRow(
            horizontalArrangement = RemoteArrangement.spacedBy(4.rdp)
        ) {
            RemoteBox(
                modifier = RemoteModifier
                    .fillMaxWidth(0.2f)
                    .height(10.rdp)
                    .background(primary)
            ) {}
            RemoteBox(
                modifier = RemoteModifier
                    .fillMaxWidth(0.25f)
                    .height(10.rdp)
                    .background(tertiary)
            ) {}
            RemoteBox(
                modifier = RemoteModifier
                    .fillMaxWidth(0.33f)
                    .height(10.rdp)
                    .background(errorRed)
            ) {}
            RemoteBox(
                modifier = RemoteModifier
                    .fillMaxWidth(0.5f)
                    .height(10.rdp)
                    .background(onSurfaceVariant)
            ) {}
        }

        RemoteText(
            text = "Connected \u2B24".rs,
            color = primary,
            fontSize = 10.rsp
        )

        RemoteText(
            text = "Disconnected \u2B24".rs,
            color = errorRed,
            fontSize = 10.rsp
        )
    }
}

@Composable
@RemoteComposable
private fun CatalogComponentPreview(
    label: String,
    content: @Composable @RemoteComposable () -> Unit
) {
    RemoteColumn(
        horizontalAlignment = RemoteAlignment.CenterHorizontally,
        modifier = RemoteModifier.size(150.rdp)
    ) {
        RemoteBox(
            modifier = RemoteModifier.size(135.rdp),
            contentAlignment = RemoteAlignment.Center
        ) {
            content()
        }
        RemoteText(label.rs, fontSize = 10.rsp, color = Color.White.rc)
    }
}

@Preview(name = "Component Grid", widthDp = 500, heightDp = 500)
@Composable
fun WearComponentGridPreview() = RemotePreview {
    val action = Action.Empty

    RemoteColumn(
        modifier = RemoteModifier
            .fillMaxSize()
            .background(Color.Black.rc)
            .padding(8.rdp),
        verticalArrangement = RemoteArrangement.spacedBy(16.rdp)
    ) {
        // Row 1
        RemoteRow(
            modifier = RemoteModifier.fillMaxWidth(),
            horizontalArrangement = RemoteArrangement.SpaceEvenly
        ) {
            CatalogComponentPreview("RemoteAppCard") {
                RemoteAppCard(
                    onClick = action,
                    appName = { RemoteText("App".rs) },
                    time = { RemoteText("Now".rs) },
                    title = { RemoteText("Title".rs) }
                ) {
                    RemoteText("Content".rs)
                }
            }
            CatalogComponentPreview("RemoteButton") {
                RemoteButton(onClick = action) {
                    RemoteText("B".rs)
                }
            }
            CatalogComponentPreview("RemoteButtonGroup") {
                RemoteButtonGroup {
                    RemoteButton(onClick = action) {
                        RemoteText(
                            "1".rs
                        )
                    }
                    RemoteButton(onClick = action) {
                        RemoteText(
                            "2".rs
                        )
                    }
                    RemoteButton(onClick = action) {
                        RemoteText(
                            "3".rs
                        )
                    }
                }
            }
        }

        // Row 2
        RemoteRow(
            modifier = RemoteModifier.fillMaxWidth(),
            horizontalArrangement = RemoteArrangement.SpaceEvenly
        ) {
            CatalogComponentPreview("RemoteCard") {
                RemoteCard(onClick = action) {
                    RemoteText("Card Content", color = RemoteCardDefaults.cardColors().contentColor)
                }
            }
            CatalogComponentPreview("RemoteCircularProgressIndicator") {
                RemoteCircularProgressIndicator(progress = 0.75f.rf)
            }
            CatalogComponentPreview("RemoteIcon") {
                RemoteIcon(imageVector = Icons.Rounded.Person, contentDescription = null)
            }
        }

        // Row 3
        RemoteRow(
            modifier = RemoteModifier.fillMaxWidth(),
            horizontalArrangement = RemoteArrangement.SpaceEvenly
        ) {
            CatalogComponentPreview("RemoteText") {
                RemoteText("Text".rs)
            }
            CatalogComponentPreview("RemoteTextButton") {
                RemoteTextButton(onClick = action,
                        colors = filledTonalColor()) {
                    RemoteText(
                        "Button"
                    )
                }
            }
            CatalogComponentPreview("RemoteTitleCard") {
                RemoteTitleCard(onClick = action, title = {
                    RemoteText("Title", color = RemoteCardDefaults.cardColors().titleColor)
                }) {
                    RemoteText("Card Content", color = RemoteCardDefaults.cardColors().contentColor)
                }
            }
        }
    }
}

@Composable
private fun filledTonalColor() =
    RemoteTextButtonDefaults.textButtonColors()
        .copy(
            containerColor = RemoteMaterialTheme.colorScheme.primary,
            contentColor = RemoteMaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = RemoteMaterialTheme.colorScheme.primary.copy(alpha = 0.12f.rf),
            disabledContentColor = RemoteMaterialTheme.colorScheme.primary.copy(0.38f.rf),
        )
