package online.idleworld.pokegrid.notif

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import online.idleworld.pokegrid.R

/** Android counterpart of the Electron app's `new Notification(...)` down/low-resource alerts. */
class Notifier(context: Context) {

    private val appContext = context.applicationContext
    private var nextId = 1000

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            appContext.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = appContext.getString(R.string.notif_channel_desc)
        }
        val manager = appContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    fun notify(title: String, body: String) {
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        // POST_NOTIFICATIONS is requested at startup; if denied, this just no-ops silently
        // rather than crashing (mirrors the Electron app treating notifications as best-effort).
        try {
            NotificationManagerCompat.from(appContext).notify(nextId++, notification)
        } catch (_: SecurityException) {
        }
    }

    companion object {
        private const val CHANNEL_ID = "pokegrid_alerts"
    }
}
