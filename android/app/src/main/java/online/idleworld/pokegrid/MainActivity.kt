package online.idleworld.pokegrid

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewFeature
import com.google.android.material.appbar.MaterialToolbar
import online.idleworld.pokegrid.data.CredentialStore
import online.idleworld.pokegrid.data.ErrorLog
import online.idleworld.pokegrid.model.Account
import online.idleworld.pokegrid.notif.Notifier
import online.idleworld.pokegrid.web.AlertKind
import online.idleworld.pokegrid.web.GamePanel
import online.idleworld.pokegrid.web.InjectedScripts
import online.idleworld.pokegrid.web.PanelStatus

class MainActivity : AppCompatActivity(), GamePanel.Listener {

    private data class PanelViewHolder(
        val root: View,
        val dot: View,
        val name: TextView,
        val status: TextView,
        val power: ImageButton,
        val expand: ImageButton,
        val container: FrameLayout,
        val homeCell: FrameLayout
    )

    private lateinit var credentialStore: CredentialStore
    private lateinit var errorLog: ErrorLog
    private lateinit var notifier: Notifier
    private lateinit var scripts: InjectedScripts

    private lateinit var expandOverlay: FrameLayout
    private lateinit var panels: List<GamePanel>
    private lateinit var panelViews: List<PanelViewHolder>
    private var accounts: MutableList<Account> = mutableListOf()

    private var ecoOn = true
    private var chatHidden = true
    private var dockHidden = false
    private var sellGuardOn = true
    private var awakeOn = false
    private var expandedIndex = -1

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (expandedIndex != -1) toggleExpand(expandedIndex)
        }
    }

    private val notifPermLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        onBackPressedDispatcher.addCallback(this, backCallback)

        credentialStore = CredentialStore(this)
        errorLog = ErrorLog(this)
        notifier = Notifier(this)
        scripts = InjectedScripts(this)

        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))
        expandOverlay = findViewById(R.id.expandOverlay)

        checkWebViewProfileSupport()
        requestNotificationPermissionIfNeeded()

        accounts = credentialStore.load().toMutableList()

        val cells = listOf<FrameLayout>(
            findViewById(R.id.cell0), findViewById(R.id.cell1),
            findViewById(R.id.cell2), findViewById(R.id.cell3)
        )
        val inflater = LayoutInflater.from(this)
        panelViews = cells.map { cell ->
            val root = inflater.inflate(R.layout.view_panel, cell, false)
            cell.addView(root)
            PanelViewHolder(
                root = root,
                dot = root.findViewById(R.id.panelDot),
                name = root.findViewById(R.id.panelName),
                status = root.findViewById(R.id.panelStatus),
                power = root.findViewById(R.id.panelPower),
                expand = root.findViewById(R.id.panelExpand),
                container = root.findViewById(R.id.webviewContainer),
                homeCell = cell
            )
        }

        panels = panelViews.mapIndexed { i, pv ->
            GamePanel(i, pv.container, scripts, this).also { it.account = accounts[i] }
        }

        panelViews.forEachIndexed { i, pv ->
            pv.name.text = accounts[i].name.ifBlank { defaultName(i) }
            pv.status.setText(R.string.panel_status_loading)
            pv.power.setOnClickListener { togglePower(i) }
            pv.expand.setOnClickListener { toggleExpand(i) }
        }

        panels.forEach { it.start() }
    }

    override fun onDestroy() {
        panels.forEach { it.destroy() }
        super.onDestroy()
    }

    // ----- GamePanel.Listener -----

    override fun onStatus(index: Int, status: PanelStatus) {
        val pv = panelViews.getOrNull(index) ?: return
        val (colorRes, textRes) = when (status) {
            PanelStatus.OFF -> R.color.dot_off to R.string.panel_status_off
            PanelStatus.LOADING -> R.color.dot_off to R.string.panel_status_loading
            PanelStatus.ONLINE -> R.color.dot_ok to R.string.panel_status_online
            PanelStatus.ERROR -> R.color.dot_err to R.string.panel_status_error
        }
        pv.dot.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, colorRes))
        pv.status.setText(textRes)
    }

    override fun onError(origem: String, detalhe: String) {
        errorLog.write(origem, detalhe)
    }

    override fun onAlert(index: Int, kind: AlertKind) {
        val nome = accounts.getOrNull(index)?.name?.ifBlank { defaultName(index) } ?: defaultName(index)
        val msg = when (kind) {
            AlertKind.DOWN -> getString(R.string.notif_msg_down, nome)
            AlertKind.LOW_BALLS -> getString(R.string.notif_msg_balls, nome)
            AlertKind.LOW_POTIONS -> getString(R.string.notif_msg_potions, nome)
            AlertKind.LOW_REVIVES -> getString(R.string.notif_msg_revives, nome)
            AlertKind.SHINY -> getString(R.string.notif_msg_shiny, nome)
        }
        notifier.notify(getString(R.string.app_name), msg)
    }

    // ----- Menu -----

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        refreshMenuTitles(menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        refreshMenuTitles(menu)
        return super.onPrepareOptionsMenu(menu)
    }

    private fun refreshMenuTitles(menu: Menu) {
        menu.findItem(R.id.action_eco)?.setTitle(if (ecoOn) R.string.action_eco else R.string.action_eco_off)
        menu.findItem(R.id.action_chat)?.setTitle(if (chatHidden) R.string.action_chat_hidden else R.string.action_chat_shown)
        menu.findItem(R.id.action_dock)?.setTitle(if (dockHidden) R.string.action_dock_hidden else R.string.action_dock_shown)
        menu.findItem(R.id.action_sellguard)?.let {
            it.title = getString(R.string.action_sellguard) + if (sellGuardOn) " ✓" else ""
        }
        menu.findItem(R.id.action_awake)?.setTitle(if (awakeOn) R.string.action_awake else R.string.action_sleep)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_login_all -> panels.forEach { it.loginNow() }
            R.id.action_accounts -> showAccountsDialog()
            R.id.action_eco -> { ecoOn = !ecoOn; panels.forEach { it.setEco(ecoFps()) }; invalidateOptionsMenu() }
            R.id.action_chat -> { chatHidden = !chatHidden; panels.forEach { it.setChatHidden(chatHidden) }; invalidateOptionsMenu() }
            R.id.action_dock -> { dockHidden = !dockHidden; panels.forEach { it.setDockHidden(dockHidden) }; invalidateOptionsMenu() }
            R.id.action_sellguard -> { sellGuardOn = !sellGuardOn; panels.forEach { it.setSellGuard(sellGuardOn) }; invalidateOptionsMenu() }
            R.id.action_awake -> { awakeOn = !awakeOn; applyAwake(); invalidateOptionsMenu() }
            R.id.action_error_log -> startActivity(Intent.createChooser(errorLog.shareIntent(), null))
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun ecoFps(): Int = if (ecoOn) 15 else 0

    private fun applyAwake() {
        if (awakeOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    // ----- Panel power / expand -----

    private fun togglePower(i: Int) {
        val panel = panels[i]
        val turningOff = !panel.isOff
        panel.setOff(turningOff)
        panelViews[i].power.alpha = if (turningOff) 0.5f else 1f
    }

    private fun toggleExpand(i: Int) {
        val pv = panelViews[i]
        if (expandedIndex == i) {
            expandOverlay.removeView(pv.root)
            pv.homeCell.addView(pv.root)
            expandOverlay.visibility = View.GONE
            expandedIndex = -1
        } else {
            if (expandedIndex != -1) toggleExpand(expandedIndex)
            pv.homeCell.removeView(pv.root)
            expandOverlay.addView(pv.root, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            expandOverlay.visibility = View.VISIBLE
            expandedIndex = i
        }
        backCallback.isEnabled = expandedIndex != -1
    }

    // ----- Accounts dialog -----

    private fun showAccountsDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_accounts, null)
        val container = view.findViewById<LinearLayout>(R.id.accountsContainer)
        val rows = accounts.mapIndexed { i, acc ->
            val row = LayoutInflater.from(this).inflate(R.layout.view_account_row, container, false)
            row.findViewById<TextView>(R.id.rowTitle).text = getString(R.string.dialog_row_title) + " " + (i + 1)
            val nameEt = row.findViewById<EditText>(R.id.rowName).apply { setText(acc.name) }
            val emailEt = row.findViewById<EditText>(R.id.rowEmail).apply { setText(acc.email) }
            val senhaEt = row.findViewById<EditText>(R.id.rowSenha).apply { setText(acc.senha) }
            container.addView(row)
            Triple(nameEt, emailEt, senhaEt)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_accounts_title)
            .setView(view)
            .setPositiveButton(R.string.dialog_save) { _, _ ->
                accounts = rows.mapIndexed { i, (n, e, s) -> Account(n.text.toString(), e.text.toString(), s.text.toString()) }.toMutableList()
                credentialStore.save(accounts)
                panels.forEachIndexed { i, p -> p.account = accounts[i] }
                panelViews.forEachIndexed { i, pv -> pv.name.text = accounts[i].name.ifBlank { defaultName(i) } }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun defaultName(i: Int) = "Conta ${i + 1}"

    private fun checkWebViewProfileSupport() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            AlertDialog.Builder(this)
                .setTitle(R.string.webview_unsupported_title)
                .setMessage(R.string.webview_unsupported_msg)
                .setPositiveButton(R.string.ok, null)
                .show()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
