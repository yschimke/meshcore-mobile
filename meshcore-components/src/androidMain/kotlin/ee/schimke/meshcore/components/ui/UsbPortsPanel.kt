package ee.schimke.meshcore.components.ui

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cable
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.mcarr.usb.interfaces.ISerialPortWrapper

private const val ACTION_USB_PERMISSION = "ee.schimke.meshcore.USB_PERMISSION"

/** Display-friendly wrapper pairing a port with its USB device metadata. */
data class UsbPortInfo(
    val port: ISerialPortWrapper,
    val deviceName: String?,
    val manufacturerName: String?,
    val productName: String?,
    val deviceAddress: String,
    val vendorId: Int,
    val productId: Int,
) {
    val displayLabel: String
        get() = productName
            ?: deviceName?.takeIf { !it.startsWith("/dev/") }
            ?: "USB ${vendorId.vid()}:${productId.pid()}"

    val subtitle: String
        get() = buildString {
            manufacturerName?.let { append(it); append(" · ") }
            append(deviceAddress)
            append(" · ${vendorId.vid()}:${productId.pid()}")
        }
}

private fun Int.vid() = "%04X".format(this)
private fun Int.pid() = "%04X".format(this)

/**
 * Lists USB serial ports and connects to the one picked by the user.
 * Automatically requests Android USB permission when needed.
 * The enumeration function is injected so the caller can route it
 * through Metro DI ([ee.schimke.meshcore.mobile.AndroidUsbPortLister]).
 */
@Composable
fun UsbPortsPanel(
    busy: Boolean,
    listPorts: () -> List<ISerialPortWrapper>,
    onConnect: (ISerialPortWrapper) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val usbManager = remember { context.getSystemService(Context.USB_SERVICE) as UsbManager }
    val portInfos = remember { mutableStateListOf<UsbPortInfo>() }
    // Port waiting for permission grant
    var pendingPort by remember { mutableStateOf<ISerialPortWrapper?>(null) }

    fun refreshPorts() {
        val rawPorts = listPorts()
        val sysDevices = usbManager.deviceList.values
        portInfos.clear()
        portInfos.addAll(rawPorts.map { port ->
            val dev = sysDevices.firstOrNull { d ->
                d.vendorId == port.vendorId && d.productId == port.productId
            }
            UsbPortInfo(
                port = port,
                deviceName = dev?.deviceName,
                manufacturerName = dev?.manufacturerName,
                productName = dev?.productName,
                deviceAddress = dev?.deviceName ?: "unknown",
                vendorId = port.vendorId,
                productId = port.productId,
            )
        })
    }

    LaunchedEffect(Unit) { refreshPorts() }

    // Listen for USB permission results AND device attach/detach
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    ACTION_USB_PERMISSION -> {
                        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        if (granted) {
                            pendingPort?.let(onConnect)
                        }
                        pendingPort = null
                    }
                    UsbManager.ACTION_USB_DEVICE_ATTACHED,
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        refreshPorts()
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }

    fun connectWithPermission(info: UsbPortInfo) {
        val device: UsbDevice? = usbManager.deviceList.values.firstOrNull { d ->
            d.vendorId == info.vendorId && d.productId == info.productId
        }
        android.util.Log.d("UsbConnect", "connectWithPermission: device=$device hasPermission=${device?.let { usbManager.hasPermission(it) }}")
        if (device != null && !usbManager.hasPermission(device)) {
            pendingPort = info.port
            // FLAG_MUTABLE is required so the system can attach EXTRA_PERMISSION_GRANTED.
            // FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT is needed on API 34+ because the
            // broadcast goes to a dynamically registered receiver (no component).
            val intent = Intent(ACTION_USB_PERMISSION).apply {
                setPackage(context.packageName)
            }
            @SuppressLint("UnspecifiedImmutableFlag")
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_MUTABLE or
                PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT
            val pi = PendingIntent.getBroadcast(context, 0, intent, flags)
            usbManager.requestPermission(device, pi)
        } else if (device != null) {
            android.util.Log.d("UsbConnect", "Permission granted, calling onConnect")
            onConnect(info.port)
        } else {
            android.util.Log.w("UsbConnect", "Device not found in system list for vid=${info.vendorId} pid=${info.productId}")
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Usb,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = "USB serial",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                enabled = !busy,
                onClick = { refreshPorts() },
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text("Refresh")
            }
        }
        if (portInfos.isEmpty()) {
            UsbEmptyState()
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(portInfos, key = { "${it.vendorId}:${it.productId}" }) { info ->
                    UsbPortCard(
                        label = info.displayLabel,
                        subtitle = info.subtitle,
                        busy = busy || pendingPort == info.port,
                        onConnect = { connectWithPermission(info) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
fun UsbPortCard(
    label: String,
    busy: Boolean,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    onConnect: () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Cable,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FilledTonalButton(enabled = !busy, onClick = onConnect) { Text("Connect") }
        }
    }
}

@Composable
private fun UsbEmptyState() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.Usb,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "No USB serial ports detected",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Plug in a MeshCore device to get started.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
