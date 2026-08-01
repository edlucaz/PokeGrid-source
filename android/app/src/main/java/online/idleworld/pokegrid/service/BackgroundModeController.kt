package online.idleworld.pokegrid.service

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
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
    }
}
