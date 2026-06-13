package ee.schimke.meshcore.mobile.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import ee.schimke.meshcore.components.ui.BleDeviceRow
import ee.schimke.meshcore.components.ui.BlePermissionPanel
import ee.schimke.meshcore.components.ui.BleScannerContent
import ee.schimke.meshcore.components.ui.isMeshCoreName
import ee.schimke.meshcore.transport.ble.BleAdvertisement
import ee.schimke.meshcore.transport.ble.BleScanner

/**
 * Stateful BLE connect panel: owns the runtime permission flow and the
 * Kable [BleScanner], and renders the live device list via the
 * transport-free [BleScannerContent] from `meshcore-components`.
 *
 * This lives in the Android integration layer (not in the reusable UI
 * module) so that the BLE transport dependency stays out of
 * `meshcore-components`. Callers receive a transport-agnostic
 * [BleDeviceRow] to connect to.
 */
@Composable
fun BleScannerPanel(
  busy: Boolean,
  onConnect: (BleDeviceRow) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  // BLE perms gate the scanner UI. POST_NOTIFICATIONS (Android 13+) is
  // requested alongside but not gating — a denied notification permission
  // shouldn't block connecting, it just means the foreground-service
  // notification won't appear.
  val blePerms = remember {
    arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
  }
  val requestedPerms = remember {
    buildList {
        addAll(blePerms)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          add(Manifest.permission.POST_NOTIFICATIONS)
        }
      }
      .toTypedArray()
  }
  fun checkGranted(): Boolean =
    blePerms.all {
      ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
  var granted by remember { mutableStateOf(checkGranted()) }
  var lastResult by remember { mutableStateOf<Map<String, Boolean>?>(null) }
  val launcher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
      result ->
      lastResult = result
      granted = blePerms.all { result[it] == true }
    }
  LaunchedEffect(Unit) {
    granted = checkGranted()
    // Existing users already granted BLE but never saw a POST_NOTIFICATIONS
    // prompt (that permission was added later). Ask for anything still
    // missing on first entry; system dialog is a no-op for granted perms
    // and won't re-show after the user has denied twice.
    if (
      granted &&
        requestedPerms.any {
          ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
    ) {
      launcher.launch(requestedPerms)
    }
  }

  if (!granted) {
    BlePermissionPanel(lastResult, onRequest = { launcher.launch(requestedPerms) }, modifier)
    return
  }

  val scanner = remember { BleScanner() }
  val devices = remember { mutableStateListOf<BleAdvertisement>() }
  var scanError by remember { mutableStateOf<String?>(null) }
  var meshOnly by remember { mutableStateOf(true) }

  LaunchedEffect(granted, meshOnly) {
    devices.clear()
    scanError = null
    if (!granted) return@LaunchedEffect
    try {
      scanner.advertisements.collect { adv ->
        if (meshOnly && !isMeshCoreName(adv.name)) return@collect
        if (devices.none { it.identifier == adv.identifier }) devices.add(adv)
      }
    } catch (c: kotlinx.coroutines.CancellationException) {
      // Tab switch, navigation, or user-initiated cancel — not a scan
      // failure. Suppress and re-throw for structured concurrency.
      throw c
    } catch (t: Throwable) {
      // Only show errors that aren't routine scan interruptions
      val msg = t.message?.lowercase() ?: ""
      val suppress = msg.contains("bluetooth") && (msg.contains("disabled") || msg.contains("off"))
      if (!suppress) {
        scanError = t.message
      }
      granted = checkGranted()
    }
  }

  BleScannerContent(
    rows = devices.map { BleDeviceRow(it.identifier, it.name, it.rssi) },
    busy = busy,
    meshOnly = meshOnly,
    onMeshOnlyChange = { meshOnly = it },
    onPick = onConnect,
    modifier = modifier,
    scanError = scanError,
  )
}
