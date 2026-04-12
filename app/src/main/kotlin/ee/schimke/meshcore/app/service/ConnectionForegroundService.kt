package ee.schimke.meshcore.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import ee.schimke.meshcore.app.MainActivity
import ee.schimke.meshcore.app.MeshcoreApp
import ee.schimke.meshcore.app.R

/**
 * Foreground service that maintains a persistent notification while a
 * Bluetooth connection is active. The invariant is:
 *
 *   **BT connected ⟺ this service is running ⟺ notification is visible.**
 *
 * - [AppConnectionController] starts this service on connect and stops
 *   it on disconnect.
 * - If the service is destroyed for any other reason (task-swipe, system
 *   reclaim), [onDestroy] triggers a disconnect so no BT connection can
 *   exist without the notification.
 * - The notification includes a "Disconnect" action that stops the
 *   service (which in turn disconnects BT via [onDestroy]).
 * - On Android 14+ users can dismiss foreground-service notifications;
 *   [onTaskRemoved] and [deleteIntent] handle that edge-case by also
 *   stopping the service.
 */
class ConnectionForegroundService : Service() {

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            Log.d(TAG, "Disconnect action received")
            disconnect()
            return START_NOT_STICKY
        }

        val deviceLabel = intent?.getStringExtra(EXTRA_DEVICE_LABEL) ?: "MeshCore device"
        val notification = buildNotification(deviceLabel)
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // User swiped the app away — tear down BT to maintain the invariant.
        disconnect()
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ------------------------------------------------------------------

    private fun disconnect() {
        Log.d(TAG, "Service stopping — disconnecting BT")
        val app = applicationContext as? MeshcoreApp ?: return
        app.connectionController.cancel()
        stopSelf()
    }

    private fun buildNotification(deviceLabel: String): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE,
        )

        val disconnectIntent = PendingIntent.getService(
            this, 1,
            Intent(this, ConnectionForegroundService::class.java).apply {
                action = ACTION_DISCONNECT
            },
            PendingIntent.FLAG_IMMUTABLE,
        )

        // deleteIntent fires if the user dismisses the notification
        // (possible on Android 14+ for foreground services).
        val deleteIntent = PendingIntent.getService(
            this, 2,
            Intent(this, ConnectionForegroundService::class.java).apply {
                action = ACTION_DISCONNECT
            },
            PendingIntent.FLAG_IMMUTABLE,
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bluetooth_connected)
            .setContentTitle("Connected to $deviceLabel")
            .setContentText("MeshCore — tap to open")
            .setContentIntent(openAppIntent)
            .setDeleteIntent(deleteIntent)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    null, "Disconnect", disconnectIntent,
                ).build(),
            )
            .build()
    }

    companion object {
        private const val TAG = "ConnFgService"
        private const val CHANNEL_ID = "meshcore_connection"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_DISCONNECT = "ee.schimke.meshcore.DISCONNECT"
        private const val EXTRA_DEVICE_LABEL = "device_label"

        fun start(context: Context, deviceLabel: String) {
            val intent = Intent(context, ConnectionForegroundService::class.java).apply {
                putExtra(EXTRA_DEVICE_LABEL, deviceLabel)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ConnectionForegroundService::class.java))
        }

        private fun ensureNotificationChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Active Connection",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shown while connected to a MeshCore device over Bluetooth"
            }
            nm.createNotificationChannel(channel)
        }
    }
}
