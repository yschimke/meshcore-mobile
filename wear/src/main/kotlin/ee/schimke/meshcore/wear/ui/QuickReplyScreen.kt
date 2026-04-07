package ee.schimke.meshcore.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListHeaderDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.google.protobuf.ByteString
import ee.schimke.meshcore.wear.ui.theme.WearDimens
import kotlinx.coroutines.launch

private val QUICK_REPLIES = listOf(
    "OK",
    "On my way",
    "Copy",
    "Help needed",
    "Check in",
    "Standing by",
)

// --- Stateful entry point ---------------------------------------------------

@Composable
fun QuickReplyScreen(
    pubkeyHex: String,
    viewModel: WearViewModel = viewModel(),
    onSent: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val recipientKey = ByteString.copyFrom(pubkeyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray())

    QuickReplyBody(
        onReply = { text ->
            scope.launch {
                viewModel.sendDirectMessage(recipientKey, text)
                onSent()
            }
        },
    )
}

// --- Stateless body (previewable) -------------------------------------------

@Composable
fun QuickReplyBody(
    replies: List<String> = QUICK_REPLIES,
    onReply: (String) -> Unit = {},
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
            verticalArrangement = Arrangement.spacedBy(WearDimens.CardGap),
        ) {
            item {
                ListHeader(
                    modifier = Modifier.minimumVerticalContentPadding(
                        top = ListHeaderDefaults.minimumTopListContentPadding,
                        bottom = ListHeaderDefaults.minimumBottomListContentPadding,
                    ),
                ) {
                    Text("Quick Reply")
                }
            }

            items(replies) { reply ->
                Button(
                    onClick = { onReply(reply) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumVerticalContentPadding(
                            ButtonDefaults.minimumVerticalListContentPadding
                        )
                        .transformedHeight(this, transformationSpec),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    transformation = SurfaceTransformation(transformationSpec),
                ) {
                    Text(
                        text = reply,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}
