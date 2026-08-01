package online.idleworld.pokegrid.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import online.idleworld.pokegrid.R

/**
 * Keeps the app's process alive and the CPU awake (partial wake lock) while the user wants
 * farming to continue with the screen off or another app in the foreground. Holds no WebViews
 * itself — MainActivity's panels already survive backgrounding on their own as long as the
 * process isn't killed and the Activity isn't destroyed; this service is what prevents both.
 *
 * Deliberately does NOT try to survive the user swiping the app away from Recents: that destroys
 * the Activity (and its WebViews) regardless, so onTaskRemoved() stops the service too rather
 * than leaving a "running in background" notification up with nothing actually running.
 */
class FarmService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIF_ID, notification)
            }
            acquireWakeLock()
            isRunning = true
            lastError = null
        } catch (e: Exception) {
            // Never let a notification/foreground-type quirk on some OEM skin crash the whole
            // app — record it so the in-app diagnostics screen can surface it instead.
            lastError = "${e.javaClass.simpleName}: ${e.message}"
            isRunning = false
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        isRunning = false
        releaseWakeLock()
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PokeGrid:farm").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun buildNotification(): Notification {
        val openIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentPending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = Intent(this, FarmService::class.java).setAction(ACTION_STOP)
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.bg_notif_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(contentPending)
            .addAction(0, getString(R.string.bg_notif_stop), stopPending)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.bg_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
            description = getString(R.string.bg_channel_desc)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "pokegrid_farm_service"
        private const val NOTIF_ID = 42
        const val ACTION_STOP = "online.idleworld.pokegrid.action.STOP_FARM"

        /** Set by onStartCommand/onDestroy so MainActivity can show real diagnostics instead of guessing. */
        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        var lastError: String? = null
            private set
    }
}
