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
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.webkit.WebViewFeature
import com.google.android.material.appbar.MaterialToolbar
import online.idleworld.pokegrid.data.CredentialStore
import online.idleworld.pokegrid.data.ErrorLog
import online.idleworld.pokegrid.model.Account
import online.idleworld.pokegrid.notif.Notifier
import online.idleworld.pokegrid.service.BackgroundModeController
import online.idleworld.pokegrid.web.AlertKind
import online.idleworld.pokegrid.web.GamePanel
import online.idleworld.pokegrid.web.InjectedScripts
import online.idleworld.pokegrid.web.PanelStatus

/**
 * PokeDream's UI: unlike PokeGrid's 2x2 grid (built for a bigger canvas / 4 accounts), this is
 * mobile-first for 2 accounts — one account fills the whole screen at a time, and a small
 * floating pill at the bottom (the "switcher card") lets you jump to the other one. Both
 * WebViews keep running in the background regardless of which is visible.
 */
class MainActivity : AppCompatActivity(), GamePanel.Listener {

    private data class ChipViewHolder(val root: View, val dot: View, val name: TextView)

    private lateinit var credentialStore: CredentialStore
    private lateinit var errorLog: ErrorLog
    private lateinit var notifier: Notifier
    private lateinit var scripts: InjectedScripts
    private lateinit var toolbar: MaterialToolbar
    private lateinit var bgController: BackgroundModeController

    private lateinit var stages: List<FrameLayout>
    private lateinit var chips: List<ChipViewHolder>
    private lateinit var panels: List<GamePanel>
    private var accounts: MutableList<Account> = mutableListOf()
    private var activeIndex = 0

    private var ecoOn = true
    private var chatHidden = true
    private var awakeOn = false

    private val notifPermLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        credentialStore = CredentialStore(this)
        errorLog = ErrorLog(this)
        notifier = Notifier(this)
        scripts = InjectedScripts(this)
        bgController = BackgroundModeController(this)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        applyEdgeToEdgeInsets()

        checkWebViewProfileSupport()
        requestNotificationPermissionIfNeeded()
        bgController.applyPersisted()

        accounts = credentialStore.load().toMutableList()

        stages = listOf(findViewById(R.id.stage0), findViewById(R.id.stage1))
        panels = stages.mapIndexed { i, stage -> GamePanel(i, stage, scripts, this).also { it.account = accounts[i] } }

        val chipRoots = listOf<View>(findViewById(R.id.chip0), findViewById(R.id.chip1))
        chips = chipRoots.map { root -> ChipViewHolder(root, root.findViewById(R.id.chipDot), root.findViewById(R.id.chipName)) }
        chips.forEachIndexed { i, c ->
            c.name.text = accounts[i].name.ifBlank { defaultName(i) }
            c.root.setOnClickListener { selectPanel(i) }
        }

        selectPanel(0)
        panels.forEach { it.start() }
    }

    override fun onDestroy() {
        panels.forEach { it.destroy() }
        super.onDestroy()
    }

    private fun selectPanel(i: Int) {
        activeIndex = i
        stages.forEachIndexed { idx, s -> s.visibility = if (idx == i) View.VISIBLE else View.GONE }
        chips.forEachIndexed { idx, c -> c.root.setBackgroundResource(if (idx == i) R.drawable.chip_bg_selected else R.drawable.chip_bg) }
        toolbar.title = accounts[i].name.ifBlank { defaultName(i) }
        invalidateOptionsMenu()
    }

    // ----- GamePanel.Listener -----

    override fun onStatus(index: Int, status: PanelStatus) {
        val chip = chips.getOrNull(index) ?: return
        val colorRes = when (status) {
            PanelStatus.OFF -> R.color.dot_off
            PanelStatus.LOADING -> R.color.dot_off
            PanelStatus.ONLINE -> R.color.dot_ok
            PanelStatus.ERROR -> R.color.dot_err
        }
        chip.dot.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, colorRes))
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
        menu.findItem(R.id.action_awake)?.setTitle(if (awakeOn) R.string.action_awake else R.string.action_sleep)
        val currentOff = panels.getOrNull(activeIndex)?.isOff ?: false
        menu.findItem(R.id.action_power)?.setTitle(if (currentOff) R.string.action_power_on else R.string.action_power_off)
        menu.findItem(R.id.action_bg)?.setTitle(if (bgController.enabled) R.string.action_bg_on else R.string.action_bg_off)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_login_all -> panels.forEach { it.loginNow() }
            R.id.action_accounts -> showAccountsDialog()
            R.id.action_power -> { panels[activeIndex].let { it.setOff(!it.isOff) }; invalidateOptionsMenu() }
            R.id.action_eco -> { ecoOn = !ecoOn; panels.forEach { it.setEco(ecoFps()) }; invalidateOptionsMenu() }
            R.id.action_chat -> { chatHidden = !chatHidden; panels.forEach { it.setChatHidden(chatHidden) }; invalidateOptionsMenu() }
            R.id.action_awake -> { awakeOn = !awakeOn; applyAwake(); invalidateOptionsMenu() }
            R.id.action_bg -> { bgController.toggle(); invalidateOptionsMenu() }
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
                chips.forEachIndexed { i, c -> c.name.text = accounts[i].name.ifBlank { defaultName(i) } }
                toolbar.title = accounts[activeIndex].name.ifBlank { defaultName(activeIndex) }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    /**
     * On edge-to-edge (Android 15+ targets draw behind system bars by default), the toolbar and
     * the floating switcher card need their own top/bottom padding — otherwise the status bar
     * covers the toolbar title and the nav bar covers the switcher card (the cut-off screen
     * reported after the first background-mode build).
     */
    private fun applyEdgeToEdgeInsets() {
        val root = findViewById<View>(R.id.root)
        val switcherCard = findViewById<View>(R.id.switcherCard)
        val baseBottomMargin = (20 * resources.displayMetrics.density).toInt()

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            toolbar.updatePadding(top = bars.top)
            switcherCard.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = baseBottomMargin + bars.bottom
            }
            insets
        }
        ViewCompat.requestApplyInsets(root)
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
