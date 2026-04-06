package ee.schimke.meshcore.app.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ee.schimke.meshcore.app.ui.theme.Dimens

// --- Connecting / Failed status view ----------------------------------------

sealed class DeviceConnectStatus {
    /**
     * [startedAtMs] is a wall-clock timestamp (System.currentTimeMillis).
     * The connecting card animates its own progress against the current
     * time — it does NOT need periodic elapsed updates from the caller.
     */
    data class Connecting(val startedAtMs: Long, val timeoutMs: Long) : DeviceConnectStatus()
    data class Failed(val cause: Throwable) : DeviceConnectStatus()
}

/**
 * Full-screen status view shown when the device screen is visible but
 * we don't yet have a live client — either because the transport is
 * still connecting or because it failed. The failure variant surfaces
 * the exception message *and* a scrollable stack trace so the user can
 * diagnose why their radio didn't respond.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceStatusView(
    title: String,
    status: DeviceConnectStatus,
    onCancel: () -> Unit,
    onOpenThemePicker: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = onOpenThemePicker) {
                        Icon(Icons.Rounded.Contrast, contentDescription = "Theme")
                    }
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.CardGap),
        ) {
            when (status) {
                is DeviceConnectStatus.Connecting -> ConnectingCard(
                    startedAtMs = status.startedAtMs,
                    timeoutMs = status.timeoutMs,
                    onCancel = onCancel,
                )
                is DeviceConnectStatus.Failed -> FailureCard(cause = status.cause, onRetry = onCancel)
            }
        }
    }
}

@Composable
private fun ConnectingCard(
    startedAtMs: Long,
    timeoutMs: Long,
    onCancel: () -> Unit,
) {
    // Drive the progress bar from inside the composable itself using
    // withFrameMillis — the caller only has to provide the wall-clock
    // start time. In preview mode the produceState coroutine returns
    // immediately so the bar freezes at its initial value and the
    // renderer can settle.
    val inPreview = androidx.compose.ui.platform.LocalInspectionMode.current
    val elapsedMs by androidx.compose.runtime.produceState(
        initialValue = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(0L),
        startedAtMs,
    ) {
        if (inPreview) return@produceState
        while (true) {
            androidx.compose.runtime.withFrameMillis {
                value = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(0L)
            }
        }
    }
    val progress = (elapsedMs.toFloat() / timeoutMs.toFloat()).coerceIn(0f, 1f)
    val remainingS = ((timeoutMs - elapsedMs).coerceAtLeast(0) / 1000).toInt()
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Contrast,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.size(Dimens.M))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Connecting…",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = "Waiting for the radio to respond — ${remainingS}s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            androidx.compose.material3.LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
            )
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Cancel") }
        }
    }
}

@Composable
private fun FailureCard(cause: Throwable, onRetry: () -> Unit) {
    val scroll = rememberScrollState()
    val stack = remember(cause) {
        buildString {
            val messages = generateSequence<Throwable>(cause) { it.cause }.toList()
            messages.forEachIndexed { i, t ->
                if (i > 0) append("Caused by: ")
                append(t::class.simpleName ?: "Throwable")
                append(": ")
                append(t.message ?: "(no message)")
                append('\n')
            }
            append('\n')
            append(cause.stackTraceToString())
        }
    }
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(Modifier.size(Dimens.S))
                Text(
                    text = "Connection failed",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Text(
                text = cause.message ?: "Unknown error",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            OutlinedButton(onClick = onRetry) { Text("Back to scanner") }
        }
    }
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Details",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(Dimens.XS))
            Text(
                text = stack,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .verticalScroll(scroll),
            )
        }
    }
}
