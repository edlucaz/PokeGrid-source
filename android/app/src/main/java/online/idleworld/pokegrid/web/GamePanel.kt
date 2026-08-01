package online.idleworld.pokegrid.web

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.GeolocationPermissions
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import online.idleworld.pokegrid.config.GameConfig
import online.idleworld.pokegrid.model.Account
import org.json.JSONObject

enum class PanelStatus { OFF, LOADING, ONLINE, ERROR }
enum class AlertKind { DOWN, LOW_BALLS, LOW_POTIONS, LOW_REVIVES, SHINY }

/**
 * Owns one game panel end to end: an isolated WebView (its own login session via the
 * WebView Multi-Profile API), the domain lock, the auto-login watcher, the injected
 * eco/chat/dock/sellguard scripts, a crash/hang watchdog, and the low-resource alert poll.
 * This is the Android counterpart of one `<webview partition="persist:contaN">` panel in the
 * Electron app's index.html/main.js.
 */
class GamePanel(
    val index: Int,
    private val container: ViewGroup,
    private val scripts: InjectedScripts,
    private val listener: Listener
) {
    interface Listener {
        fun onStatus(index: Int, status: PanelStatus)
        fun onError(origem: String, detalhe: String)
        fun onAlert(index: Int, kind: AlertKind)
    }

    companion object {
        private val LOGIN_URL = GameConfig.LOGIN_URL
        private val START_URL = GameConfig.START_URL
        private val GAME_HOST = GameConfig.GAME_HOST
        private const val LOGIN_COOLDOWN_MS = 15_000L
        private const val LOGIN_WATCHDOG_MS = 20_000L
        private const val RETRY_DELAY_MS = 6_000L
        private const val CRASH_RECREATE_DELAY_MS = 1_500L
        private const val ALERT_POLL_MS = 15_000L
        private const val MAX_RETRIES = 5
        private const val BALL_MIN = 100
        private const val POTION_MIN = 15
        private const val REVIVE_MIN = 5
    }

    lateinit var webView: WebView
        private set

    var isOff = false
        private set
    var account: Account = Account()

    private val handler = Handler(Looper.getMainLooper())
    private var fails = 0
    private var alertedDown = false
    private var lastLoginAttempt = 0L
    private var currentEcoFps = 15
    private var chatHidden = true
    private var dockHidden = false
    private var sellGuardOn = true
    private var zoomFactor = 1f
    private var lowBallsOn = false
    private var lowPotionsOn = false
    private var lowRevivesOn = false
    private var lastShinyN = 0
    private var alertPollRunnable: Runnable? = null
    private var loginWatchdogRunnable: Runnable? = null
    private var retryRunnable: Runnable? = null

    init {
        webView = createWebView()
        container.addView(webView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    fun start() {
        webView.loadUrl(START_URL)
    }

    fun setOff(off: Boolean) {
        isOff = off
        if (off) {
            stopAlertPolling()
            stopLoginWatchdog()
            cancelRetry()
            webView.loadUrl("about:blank")
            setStatus(PanelStatus.OFF)
        } else {
            webView.loadUrl(START_URL)
        }
    }

    /** Forces an immediate login attempt, bypassing the cooldown (mirrors "Entrar em todas"). */
    fun loginNow() {
        if (!account.hasCredentials) return
        if (isOff) { setOff(false); return }
        lastLoginAttempt = 0L
        webView.loadUrl(LOGIN_URL)
    }

    fun setEco(fps: Int) {
        currentEcoFps = fps
        if (!isOff) webView.evaluateJavascript(scripts.eco(fps), null)
    }

    fun setChatHidden(hide: Boolean) {
        chatHidden = hide
        if (!isOff) webView.evaluateJavascript(scripts.chat(hide), null)
    }

    fun setDockHidden(hide: Boolean) {
        if (!GameConfig.ENABLE_DOCK_TOGGLE) return
        dockHidden = hide
        if (!isOff) webView.evaluateJavascript(scripts.dock(hide), null)
    }

    fun setSellGuard(on: Boolean) {
        if (!GameConfig.ENABLE_SELLGUARD) return
        sellGuardOn = on
        if (!isOff) webView.evaluateJavascript(scripts.sellGuardToggle(on), null)
    }

    fun setZoom(factor: Float) {
        zoomFactor = factor.coerceIn(0.5f, 2f)
        applyZoom()
    }

    fun runScript(js: String) {
        if (!isOff) webView.evaluateJavascript(js, null)
    }

    fun destroy() {
        stopAlertPolling()
        stopLoginWatchdog()
        cancelRetry()
        try {
            container.removeView(webView)
            webView.destroy()
        } catch (_: Exception) {
        }
    }

    private fun createWebView(): WebView {
        val context = container.context
        val wv = WebView(context)

        // Must run before any other WebView method: the profile can't change once the WebView
        // has settings applied, evaluateJavascript() called, or a page loaded.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            WebViewCompat.setProfile(wv, "conta${index + 1}")
        }

        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            // Strips the WebView-only "; wv" / "Version/X.0 " tokens so the UA looks like plain
            // mobile Chrome — same idea as the Electron app stripping "Electron/..." from its UA
            // to avoid tripping Cloudflare's in-app-browser heuristics.
            userAgentString = userAgentString
                .replace("; wv", "")
                .replace(Regex("Version/[0-9.]+ "), "")
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
        }
        wv.setBackgroundColor(0xFF0D1117.toInt())

        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (isGameUri(uri)) return false
                openExternally(uri)
                return true
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                if (!isOff) setStatus(PanelStatus.LOADING)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                if (isOff) return
                fails = 0
                alertedDown = false
                cancelRetry()
                setStatus(PanelStatus.ONLINE)
                applyAllScripts()
                if (url != null && url.startsWith(LOGIN_URL)) {
                    view.evaluateJavascript(scripts.scrollLogin(), null)
                    maybeAutoLogin()
                }
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (!request.isForMainFrame || isOff) return
                handleLoadFailure()
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                listener.onError("painel${index + 1}", "processo do painel caiu (crashed=${detail.didCrash()})")
                recreateAfterCrash()
                return true // we're handling it: don't let the OS kill the whole app
            }
        }

        wv.webChromeClient = object : WebChromeClient() {
            override fun onJsAlert(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
                showNativeAlert(message) { result.confirm() }
                return true
            }

            override fun onJsConfirm(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
                showNativeConfirm(message, onOk = { result.confirm() }, onCancel = { result.cancel() })
                return true
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                request.deny() // mic/camera/etc: same blanket denial as the Electron app
            }

            override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback) {
                callback.invoke(origin, false, false)
            }
        }

        return wv
    }

    private fun isGameUri(uri: Uri): Boolean {
        val host = uri.host ?: return false
        return uri.scheme == "https" && (host == GAME_HOST || host.endsWith(".$GAME_HOST"))
    }

    private fun openExternally(uri: Uri) {
        try {
            container.context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: Exception) {
            listener.onError("painel${index + 1}", "falha ao abrir link externo: ${e.message}")
        }
    }

    private fun applyAllScripts() {
        val wv = webView
        if (GameConfig.ENABLE_RESOURCE_ALERTS) wv.evaluateJavascript(scripts.stateCollector(), null)
        wv.evaluateJavascript(scripts.eco(currentEcoFps), null)
        wv.evaluateJavascript(scripts.webglWatchdog(), null)
        wv.evaluateJavascript(scripts.popupKiller(), null)
        if (GameConfig.ENABLE_SELLGUARD) {
            wv.evaluateJavascript(scripts.sellGuard(), null)
            wv.evaluateJavascript(scripts.sellGuardToggle(sellGuardOn), null)
        }
        wv.evaluateJavascript(scripts.chat(chatHidden), null)
        if (GameConfig.ENABLE_DOCK_TOGGLE) wv.evaluateJavascript(scripts.dock(dockHidden), null)
        applyZoom()
        if (GameConfig.ENABLE_RESOURCE_ALERTS) startAlertPolling()
        startLoginWatchdog()
    }

    private fun applyZoom() {
        if (isOff) return
        webView.evaluateJavascript("document.body.style.zoom='${zoomFactor}';", null)
    }

    private fun maybeAutoLogin() {
        if (!account.hasCredentials) return
        val now = System.currentTimeMillis()
        if (now - lastLoginAttempt < LOGIN_COOLDOWN_MS) return
        lastLoginAttempt = now
        webView.evaluateJavascript(scripts.login(account.email, account.senha), null)
    }

    private fun handleLoadFailure() {
        setStatus(PanelStatus.ERROR)
        fails++
        if (fails == 3 && !alertedDown) {
            alertedDown = true
            listener.onAlert(index, AlertKind.DOWN)
        }
        if (fails <= MAX_RETRIES) {
            cancelRetry()
            val r = Runnable { if (!isOff) webView.reload() }
            retryRunnable = r
            handler.postDelayed(r, RETRY_DELAY_MS)
        }
    }

    private fun recreateAfterCrash() {
        handler.postDelayed({
            try {
                container.removeView(webView)
                webView.destroy()
            } catch (_: Exception) {
            }
            webView = createWebView()
            container.addView(webView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            webView.loadUrl(if (isOff) "about:blank" else START_URL)
        }, CRASH_RECREATE_DELAY_MS)
    }

    private fun cancelRetry() {
        retryRunnable?.let { handler.removeCallbacks(it) }
        retryRunnable = null
    }

    private fun setStatus(status: PanelStatus) {
        listener.onStatus(index, status)
    }

    private fun startAlertPolling() {
        stopAlertPolling()
        val r = object : Runnable {
            override fun run() {
                if (isOff) return
                webView.evaluateJavascript(scripts.readAlerts()) { resultJson -> handleAlertResult(resultJson) }
                handler.postDelayed(this, ALERT_POLL_MS)
            }
        }
        alertPollRunnable = r
        handler.postDelayed(r, ALERT_POLL_MS)
    }

    private fun stopAlertPolling() {
        alertPollRunnable?.let { handler.removeCallbacks(it) }
        alertPollRunnable = null
    }

    /**
     * Backup path for auto-login: onPageFinished only fires on full top-level navigations, but a
     * client-routed SPA can drop back to its login screen purely via client-side state (no new
     * page load, so no onPageFinished). This periodically retries maybeAutoLogin(), which is a
     * cheap no-op when the login form isn't present or the cooldown hasn't elapsed.
     */
    private fun startLoginWatchdog() {
        stopLoginWatchdog()
        val r = object : Runnable {
            override fun run() {
                if (isOff) return
                maybeAutoLogin()
                handler.postDelayed(this, LOGIN_WATCHDOG_MS)
            }
        }
        loginWatchdogRunnable = r
        handler.postDelayed(r, LOGIN_WATCHDOG_MS)
    }

    private fun stopLoginWatchdog() {
        loginWatchdogRunnable?.let { handler.removeCallbacks(it) }
        loginWatchdogRunnable = null
    }

    private fun handleAlertResult(resultJson: String?) {
        if (resultJson.isNullOrBlank() || resultJson == "null") return
        try {
            val o = JSONObject(resultJson)
            if (!o.optBoolean("live", false)) return
            checkLow(o.optInt("balls"), BALL_MIN, lowBallsOn, { lowBallsOn = it }) { listener.onAlert(index, AlertKind.LOW_BALLS) }
            checkLow(o.optInt("potions"), POTION_MIN, lowPotionsOn, { lowPotionsOn = it }) { listener.onAlert(index, AlertKind.LOW_POTIONS) }
            checkLow(o.optInt("revives"), REVIVE_MIN, lowRevivesOn, { lowRevivesOn = it }) { listener.onAlert(index, AlertKind.LOW_REVIVES) }
            val shinyN = o.optInt("shinyN")
            if (shinyN > lastShinyN) listener.onAlert(index, AlertKind.SHINY)
            lastShinyN = shinyN
        } catch (_: Exception) {
        }
    }

    private inline fun checkLow(current: Int, min: Int, wasOn: Boolean, setOn: (Boolean) -> Unit, fire: () -> Unit) {
        if (current < min) {
            if (!wasOn) { setOn(true); fire() }
        } else if (wasOn) setOn(false)
    }

    private fun showNativeAlert(message: String?, onOk: () -> Unit) {
        AlertDialog.Builder(container.context)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { _, _ -> onOk() }
            .setCancelable(false)
            .show()
    }

    private fun showNativeConfirm(message: String?, onOk: () -> Unit, onCancel: () -> Unit) {
        AlertDialog.Builder(container.context)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { _, _ -> onOk() }
            .setNegativeButton(android.R.string.cancel) { _, _ -> onCancel() }
            .setCancelable(false)
            .show()
    }
}
