package online.idleworld.pokegrid.service

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import online.idleworld.pokegrid.R

/**
 * Persists the "keep farming in the background" preference and starts/stops [FarmService]
 * accordingly. Shared by both flavors' MainActivity so the on/off + battery-exemption-prompt
 * logic isn't duplicated.
 */
class BackgroundModeController(private val activity: Activity) {

    private val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var enabled: Boolean = prefs.getBoolean(KEY_ENABLED, true)
        private set

    /** Call once from onCreate to (re)start the service if the user left it on last time. */
    fun applyPersisted() {
        setEnabled(enabled, promptBattery = false)
    }

    fun toggle() {
        setEnabled(!enabled, promptBattery = true)
    }

    private fun setEnabled(on: Boolean, promptBattery: Boolean) {
        enabled = on
        prefs.edit().putBoolean(KEY_ENABLED, on).apply()
        val intent = Intent(activity, FarmService::class.java)
        if (on) {
            ContextCompat.startForegroundService(activity, intent)
            if (promptBattery) maybeAskBatteryExemption()
        } else {
            activity.stopService(intent)
        }
    }

    /**
     * Call from the POST_NOTIFICATIONS ActivityResultLauncher callback. The service can call
     * startForeground() before the user answers that permission dialog — when that happens the
     * notification is silently suppressed and never reappears on its own, even after the user
     * grants the permission. Re-triggering the service here is what makes it actually show up.
     */
    fun onNotificationPermissionResult(granted: Boolean) {
        prefs.edit().putBoolean(KEY_ASKED_NOTIF, true).apply()
        if (granted && enabled) {
            ContextCompat.startForegroundService(activity, Intent(activity, FarmService::class.java))
        } else {
            maybeExplainBlockedNotifications()
        }
    }

    /**
     * Covers the case where notifications were already denied in an earlier session (before
     * this flow existed): Android won't show its own permission dialog again, so the only way
     * left to fix it is the app's notification settings screen — this points the user there,
     * once.
     */
    private fun maybeExplainBlockedNotifications() {
        if (!enabled) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (granted) return
        val canStillAsk = ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
        val askedBefore = prefs.getBoolean(KEY_ASKED_NOTIF, false)
        if (canStillAsk || !askedBefore) return // system will still prompt on its own, or we haven't even tried yet
        if (prefs.getBoolean(KEY_EXPLAINED_NOTIF_BLOCKED, false)) return
        prefs.edit().putBoolean(KEY_EXPLAINED_NOTIF_BLOCKED, true).apply()

        AlertDialog.Builder(activity)
            .setTitle(R.string.notif_blocked_title)
            .setMessage(R.string.notif_blocked_msg)
            .setPositiveButton(R.string.battery_opt_open) { _, _ ->
                try {
                    activity.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                    )
                } catch (_: Exception) {
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    /**
     * Reports the actual live state instead of guessing: whether the toggle is on, whether the
     * service really started (FarmService.isRunning, set from onStartCommand — not just "we
     * asked Android to start it"), and every permission/setting that can silently swallow the
     * notification without the service itself failing.
     */
    fun showDiagnostics() {
        if (enabled) {
            try {
                ContextCompat.startForegroundService(activity, Intent(activity, FarmService::class.java))
            } catch (_: Exception) {
            }
        }

        val notifPermGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val notifGloballyEnabled = NotificationManagerCompat.from(activity).areNotificationsEnabled()
        val channel = activity.getSystemService(NotificationManager::class.java)?.getNotificationChannel(FarmService.CHANNEL_ID)
        val channelOk = channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
        val batteryIgnored = activity.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(activity.packageName) ?: false

        fun yn(b: Boolean) = if (b) "Sim" else "Não"
        val msg = buildString {
            appendLine("Segundo plano ligado no menu: ${yn(enabled)}")
            appendLine("Serviço realmente iniciou: ${yn(FarmService.isRunning)}")
            appendLine("Notificações do app permitidas (geral): ${yn(notifGloballyEnabled)}")
            appendLine("Permissão de notificação concedida: ${yn(notifPermGranted)}")
            appendLine("Canal \"Segundo plano\" ativo: ${yn(channelOk)}")
            appendLine("Ignorando otimização de bateria: ${yn(batteryIgnored)}")
            FarmService.lastError?.let { appendLine("\nErro ao iniciar o serviço: $it") }
            if (FarmService.isRunning && (!notifGloballyEnabled || !notifPermGranted || !channelOk)) {
                appendLine("\nO farm está rodando, mas a notificação está bloqueada por uma das linhas marcadas \"Não\" acima — ajuste isso nas configurações do app.")
            }
        }.trim()

        AlertDialog.Builder(activity)
            .setTitle(R.string.diag_title)
            .setMessage(msg)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun maybeAskBatteryExemption() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val pm = activity.getSystemService(PowerManager::class.java) ?: return
        if (pm.isIgnoringBatteryOptimizations(activity.packageName)) return
        if (prefs.getBoolean(KEY_ASKED_BATTERY, false)) return
        prefs.edit().putBoolean(KEY_ASKED_BATTERY, true).apply()

        AlertDialog.Builder(activity)
            .setTitle(R.string.battery_opt_title)
            .setMessage(R.string.battery_opt_msg)
            .setPositiveButton(R.string.battery_opt_open) { _, _ ->
                try {
                    activity.startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${activity.packageName}"))
                    )
                } catch (_: Exception) {
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    companion object {
        private const val PREFS_NAME = "pokegrid_prefs"
        private const val KEY_ENABLED = "bg_mode_enabled"
        private const val KEY_ASKED_BATTERY = "bg_mode_asked_battery"
        private const val KEY_ASKED_NOTIF = "bg_mode_asked_notif"
        private const val KEY_EXPLAINED_NOTIF_BLOCKED = "bg_mode_explained_notif_blocked"
    }
}
