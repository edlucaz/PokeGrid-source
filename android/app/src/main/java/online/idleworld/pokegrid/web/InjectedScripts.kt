package online.idleworld.pokegrid.web

import android.content.Context
import org.json.JSONObject

/**
 * Loads the JS files under assets/scripts (ported near-verbatim from the Electron app's
 * index.html) and does simple token substitution. Values are embedded via
 * [JSONObject.quote], which produces a properly escaped JS/JSON string literal, so a password
 * containing quotes or backslashes can't break out of the script.
 */
class InjectedScripts(context: Context) {

    private val assets = context.applicationContext.assets
    private val cache = mutableMapOf<String, String>()

    private fun raw(name: String): String =
        cache.getOrPut(name) { assets.open("scripts/$name").bufferedReader().use { it.readText() } }

    fun login(email: String, senha: String): String =
        raw("login.js")
            .replace("__EMAIL_JSON__", JSONObject.quote(email))
            .replace("__SENHA_JSON__", JSONObject.quote(senha))

    fun scrollLogin(): String = raw("scroll_login.js")

    fun eco(fps: Int): String = raw("eco.js").replace("__FPS__", fps.toString())

    fun chat(hide: Boolean): String = raw("chat.js").replace("__HIDE__", hide.toString())

    fun dock(hide: Boolean): String = raw("dock.js").replace("__HIDE__", hide.toString())

    fun popupKiller(): String = raw("popup_killer.js")

    fun webglWatchdog(): String = raw("webgl_watchdog.js")

    fun sellGuard(): String = raw("sellguard.js")

    fun sellGuardToggle(on: Boolean): String = raw("sellguard_toggle.js").replace("__ON__", on.toString())

    fun stateCollector(): String = raw("state_collector.js")

    fun readAlerts(): String = raw("read_alerts.js")

    fun preset(fileName: String): String =
        assets.open("presets/$fileName").bufferedReader().use { it.readText() }
}
