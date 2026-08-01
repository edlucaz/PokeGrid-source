package online.idleworld.pokegrid.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import online.idleworld.pokegrid.config.GameConfig
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val versionCode: Int, val tagName: String, val downloadUrl: String, val fileName: String)

/**
 * Checks this repo's GitHub Releases for a newer build of this flavor and, if the user accepts,
 * downloads and launches the system installer. There's no Play Store distribution here, so this
 * is the whole update path: CI (.github/workflows/release.yml) publishes a release with both
 * flavors' signed APKs attached on every push to main; this is the client side of that. Both the
 * CI build and this app are signed with the same committed keystore (see app/build.gradle.kts),
 * which is what lets the install below land as an update instead of demanding an uninstall.
 */
class UpdateChecker(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** [force]=false respects the check-once-per-CHECK_INTERVAL_MS throttle; the menu action passes true. */
    fun checkInBackground(force: Boolean, onResult: (UpdateInfo?) -> Unit) {
        if (!force) {
            val last = prefs.getLong(KEY_LAST_CHECK, 0L)
            if (System.currentTimeMillis() - last < CHECK_INTERVAL_MS) return
        }
        prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
        Thread {
            val info = try {
                fetchLatestNewerRelease()
            } catch (_: Exception) {
                null
            }
            handler.post { onResult(info) }
        }.start()
    }

    fun downloadAndInstall(activity: Activity, info: UpdateInfo, onProgress: (Int) -> Unit, onBeforeInstall: () -> Unit, onError: (String) -> Unit) {
        Thread {
            try {
                val conn = (URL(info.downloadUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 15_000
                }
                if (conn.responseCode !in 200..299) throw java.io.IOException("HTTP ${conn.responseCode}")
                val total = conn.contentLength
                val outFile = File(activity.filesDir, "update.apk")
                conn.inputStream.use { input ->
                    FileOutputStream(outFile).use { output ->
                        val buf = ByteArray(8192)
                        var readTotal = 0
                        var n: Int
                        while (input.read(buf).also { n = it } >= 0) {
                            output.write(buf, 0, n)
                            readTotal += n
                            if (total > 0) {
                                val pct = readTotal * 100 / total
                                handler.post { onProgress(pct) }
                            }
                        }
                    }
                }
                handler.post {
                    onBeforeInstall()
                    installApk(activity, outFile)
                }
            } catch (e: Exception) {
                handler.post { onError(e.message ?: e.javaClass.simpleName) }
            }
        }.start()
    }

    private fun currentVersionCode(): Int {
        val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return PackageInfoCompat.getLongVersionCode(pkgInfo).toInt()
    }

    private fun fetchLatestNewerRelease(): UpdateInfo? {
        val conn = (URL("https://api.github.com/repos/$OWNER/$REPO/releases?per_page=10").openConnection() as HttpURLConnection).apply {
            setRequestProperty("Accept", "application/vnd.github+json")
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        if (conn.responseCode != 200) return null
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val releases = JSONArray(body)
        val currentCode = currentVersionCode()
        for (i in 0 until releases.length()) {
            val rel = releases.getJSONObject(i)
            if (rel.optBoolean("draft", false) || rel.optBoolean("prerelease", false)) continue
            // Tags look like "build-<versionCode>" (see the CI workflow); the trailing number is
            // the actual versionCode baked into that release's APKs.
            val tag = rel.optString("tag_name", "")
            val versionCode = tag.substringAfterLast("-").toIntOrNull() ?: continue
            if (versionCode <= currentCode) continue
            val assets = rel.optJSONArray("assets") ?: continue
            for (j in 0 until assets.length()) {
                val a = assets.getJSONObject(j)
                val name = a.optString("name", "")
                if (name.endsWith(".apk") && name.contains(GameConfig.FLAVOR_NAME, ignoreCase = true)) {
                    return UpdateInfo(versionCode, tag, a.optString("browser_download_url"), name)
                }
            }
        }
        return null
    }

    private fun installApk(activity: Activity, file: File) {
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        // If the app doesn't have the "install unknown apps" permission yet, the system's own
        // installer screen prompts for it and lets the user come back and retry — no extra
        // handling needed here.
        activity.startActivity(intent)
    }

    companion object {
        private const val OWNER = "edlucaz"
        private const val REPO = "PokeGrid-source"
        private const val PREFS_NAME = "pokegrid_prefs"
        private const val KEY_LAST_CHECK = "update_last_check"
        private const val CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L
    }
}
