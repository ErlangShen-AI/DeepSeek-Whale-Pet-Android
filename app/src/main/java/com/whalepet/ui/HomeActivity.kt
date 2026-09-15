package com.whalepet.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.materialswitch.MaterialSwitch
import com.whalepet.BuildConfig
import com.whalepet.R
import com.whalepet.core.BubbleContent
import com.whalepet.core.Diagnostics
import com.whalepet.core.Money
import com.whalepet.core.PetState
import com.whalepet.core.Prefs
import com.whalepet.core.SoundSet
import com.whalepet.core.UsageMode
import com.whalepet.pet.PetService
import com.whalepet.pet.PetSound
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * 设置界面。
 *
 * 顶部是桌宠实时预览与余额状态，下面按功能分组排列各项开关与输入。
 * 可点击的行都带按下高亮与箭头，输入框在写入完成后显示已保存，
 * 密钥与令牌的写入都带防抖，离开界面时立即落盘。
 */
class HomeActivity : AppCompatActivity() {

    private lateinit var metrics: Metrics
    private lateinit var prefs: Prefs
    private lateinit var state: PetState
    private lateinit var sound: PetSound
    private val content = BubbleContent(this)
    private val flows = PermissionFlows(this)

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var root: FrameLayout
    private lateinit var topBar: LinearLayout
    private lateinit var headerBlock: LinearLayout
    private lateinit var headerTitle: TextView
    private lateinit var barTitle: TextView
    private lateinit var preview: PetPreviewView
    private lateinit var balanceText: TextView
    private lateinit var usageText: TextView
    private lateinit var refreshRow: LinearLayout
    private lateinit var permissionCard: LinearLayout
    private lateinit var overlayRow: LinearLayout
    private lateinit var notificationRow: LinearLayout
    private lateinit var batteryRow: LinearLayout
    private lateinit var petSwitch: MaterialSwitch
    private lateinit var usageSegment: SegmentedControl
    private lateinit var peakSegment: SegmentedControl
    private lateinit var tokenBlock: LinearLayout

    private var apiKeyRow: Ui.InputRow? = null
    private var tokenRow: Ui.InputRow? = null
    private var customBalanceRow: Ui.InputRow? = null
    private var customUsageRow: Ui.InputRow? = null
    private var customBalanceSave: Runnable? = null
    private var customUsageSave: Runnable? = null
    private var apiKeySave: Runnable? = null
    private var tokenSave: Runnable? = null

    private val overlayPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            syncPermission()
            if (prefs.petEnabled && Settings.canDrawOverlays(this)) {
                PetService.start(this)
            }
        }

    private val batteryPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            syncBackgroundState()
        }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        metrics = Metrics(this)
        prefs = Prefs.get(this)
        state = PetState.get(this)
        sound = PetSound.get(this).also { it.apply(prefs) }
        setContentView(buildScreen())
        applyWindowInsets()
        requestNotificationPermission()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                state.snapshot.collect { snapshot -> render(snapshot) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        syncPermission()
        syncBackgroundState()
        petSwitch.isChecked = prefs.petEnabled
        usageSegment.setSelected(prefs.usageMode.ordinal, animated = false)
        peakSegment.setSelected(prefs.peakText.ordinal, animated = false)
        render(state.snapshot.value)
        if (prefs.petEnabled && Settings.canDrawOverlays(this)) {
            PetService.start(this)
        }
        state.refresh(manual = false)
    }

    override fun onPause() {
        super.onPause()
        flushPendingSaves()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    private fun buildScreen(): View {
        root = FrameLayout(this)
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.background))

        val scroll = ScrollView(this)
        scroll.isFillViewport = true
        scroll.clipToPadding = false
        scroll.overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS

        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(0, 0, 0, metrics.u(BOTTOM_PADDING))

        headerBlock = buildHeader()
        content.addView(headerBlock)
        val statusCard = SettingsCards.status(
            context = this,
            metrics = metrics,
            emphasis = HERO_CARD_EMPHASIS,
            onRefresh = { state.refresh(manual = true) }
        )
        preview = statusCard.preview
        balanceText = statusCard.balanceText
        usageText = statusCard.usageText
        refreshRow = statusCard.refreshRow
        content.addView(statusCard.card)
        content.addView(sectionTitle(getString(R.string.section_pet)))
        val petCard = SettingsCards.pet(
            context = this,
            metrics = metrics,
            petEnabled = prefs.petEnabled,
            scale = prefs.scale,
            scaleTicks = SCALE_TICKS,
            bubblesEnabled = prefs.bubbleEnabled,
            hideBalance = prefs.hideBalance,
            bubbleSeconds = prefs.bubbleSeconds,
            maxBubbleSeconds = Prefs.MAX_BUBBLE_SECONDS,
            onPetEnabledChange = { value -> setPetEnabled(value) },
            onScaleChange = { value -> prefs.scale = value },
            onBubblesChange = { value -> prefs.bubbleEnabled = value },
            onHideBalanceChange = { value -> prefs.hideBalance = value },
            onBubbleSecondsChange = { value -> prefs.bubbleSeconds = value }
        )
        content.addView(petCard.card)
        petSwitch = petCard.switch
        content.addView(sectionTitle(getString(R.string.section_permission)))
        val permissionView = SettingsCards.permission(
            context = this,
            metrics = metrics,
            onRequestOverlay = { requestOverlayPermission() },
            onOpenNotificationSettings = { openNotificationSettings() },
            onRequestBatteryExemption = { requestBatteryExemption() }
        )
        permissionCard = permissionView.card
        overlayRow = permissionView.overlayRow
        notificationRow = permissionView.notificationRow
        batteryRow = permissionView.batteryRow
        content.addView(permissionCard)
        content.addView(sectionTitle(getString(R.string.section_balance)))
        val balanceCard = SettingsCards.balance(
            context = this,
            metrics = metrics,
            apiKey = prefs.apiKey,
            refreshSeconds = prefs.refreshSeconds,
            refreshStepSeconds = REFRESH_STEP_SECONDS,
            minRefreshSeconds = Prefs.MIN_REFRESH_SECONDS,
            maxRefreshSeconds = Prefs.MAX_REFRESH_SECONDS,
            customEnabled = prefs.customBalanceEnabled,
            customBalance = prefs.customBalanceText,
            customUsage = prefs.customUsageText,
            onApiKeyChange = { value -> scheduleApiKeySave(value) },
            onRefreshSecondsChange = { value -> prefs.refreshSeconds = value },
            onCustomEnabledChange = { value -> prefs.customBalanceEnabled = value },
            onCustomBalanceChange = { value -> scheduleCustomSave(value, balance = true) },
            onCustomUsageChange = { value -> scheduleCustomSave(value, balance = false) }
        )
        apiKeyRow = balanceCard.apiKeyRow
        customBalanceRow = balanceCard.customBalanceRow
        customUsageRow = balanceCard.customUsageRow
        content.addView(balanceCard.card)

        content.addView(sectionTitle(getString(R.string.section_usage)))
        val usageCard = SettingsCards.usage(
            context = this,
            metrics = metrics,
            usageMode = prefs.usageMode,
            platformToken = prefs.platformToken,
            showTodayUsage = prefs.showTodayUsage,
            peakText = prefs.peakText,
            onUsageModeChange = { value ->
                prefs.usageMode = value
                state.invalidate()
                state.refresh(manual = true)
                syncTokenVisibility()
            },
            onTokenChange = { value -> scheduleTokenSave(value) },
            onShowUsageChange = { value ->
                prefs.showTodayUsage = value
                state.refresh(manual = true)
            },
            onPeakTextChange = { value -> prefs.peakText = value }
        )
        usageSegment = usageCard.segment
        tokenBlock = usageCard.tokenBlock
        tokenRow = usageCard.tokenRow
        peakSegment = usageCard.peakSegment
        content.addView(usageCard.card)
        syncTokenVisibility()
        content.addView(sectionTitle(getString(R.string.section_sound)))
        content.addView(
            SettingsCards.sound(
                context = this,
                metrics = metrics,
                soundSet = prefs.soundSet,
                volume = prefs.volume,
                onSoundSetChange = { value ->
                    prefs.soundSet = value
                    sound.apply(prefs)
                },
                onVolumeChange = { value ->
                    prefs.volume = value
                    sound.apply(prefs)
                },
                onPreview = {
                    sound.apply(prefs)
                    sound.preview()
                }
            )
        )
        content.addView(sectionTitle(getString(R.string.section_background)))
        content.addView(
            SettingsCards.background(
                context = this,
                metrics = metrics,
                autoStart = prefs.autoStart,
                onOpenSystemSettings = { openAppDetails() },
                onAutoStartChange = { value -> prefs.autoStart = value }
            )
        )
        content.addView(sectionTitle(getString(R.string.section_about)))
        content.addView(
            SettingsCards.about(
                context = this,
                metrics = metrics,
                version = BuildConfig.VERSION_NAME,
                onOpenProject = {
                    runCatching {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PROJECT_URL)))
                    }.onFailure { error ->
                        Diagnostics.warn("打开项目主页失败", error)
                    }
                }
            )
        )
        content.addView(sectionTitle(getString(R.string.section_guide)))
        content.addView(SettingsCards.guide(this, metrics))

        scroll.addView(
            content,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(
            scroll,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        topBar = buildTopBar()
        root.addView(
            topBar,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        scroll.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            val distance = headerBlock.height.toFloat().coerceAtLeast(1f)
            val collapse = (scrollY / distance).coerceIn(0f, 1f)
            topBar.background?.alpha = (collapse * 255f).toInt()
            barTitle.alpha = collapse
            headerTitle.alpha = 1f - collapse
            headerTitle.translationY = -scrollY * HEADER_PARALLAX
        }
        return root
    }

    private fun buildTopBar(): LinearLayout {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.background = Ui.scrollEdge(this, R.color.bar_background)
        bar.background?.alpha = 0
        bar.setPadding(
            metrics.u(Ui.PAGE_MARGIN),
            metrics.u(BAR_PADDING_V),
            metrics.u(Ui.PAGE_MARGIN),
            metrics.u(BAR_PADDING_V)
        )
        barTitle = Ui.text(this, metrics, TextRole.HEADLINE, getString(R.string.app_name))
        barTitle.alpha = 0f
        bar.addView(barTitle)
        return bar
    }

    private fun buildHeader(): LinearLayout {
        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        column.setPadding(
            metrics.u(Ui.PAGE_MARGIN),
            metrics.u(HEADER_TOP),
            metrics.u(Ui.PAGE_MARGIN),
            metrics.u(HEADER_BOTTOM)
        )
        headerTitle = Ui.text(this, metrics, TextRole.LARGE_TITLE, getString(R.string.home_title))
        column.addView(headerTitle)
        column.addView(
            Ui.text(
                this,
                metrics,
                TextRole.CALLOUT,
                getString(R.string.home_subtitle),
                R.color.text_secondary
            )
        )
        return column
    }

    private fun sectionTitle(content: String): TextView = Ui.sectionTitle(this, metrics, content)

    /** 逐字输入时先标记未保存，停顿后再写入，写入完成后标记已保存。 */
    private fun scheduleApiKeySave(value: String) {
        apiKeyRow?.status?.text = getString(R.string.save_state_unsaved)
        apiKeySave?.let { handler.removeCallbacks(it) }
        val runnable = Runnable { commitApiKey(value) }
        apiKeySave = runnable
        handler.postDelayed(runnable, SAVE_DELAY_MS)
    }

    private fun commitApiKey(value: String) {
        apiKeySave = null
        val trimmed = value.trim()
        if (trimmed != prefs.apiKey) {
            prefs.apiKey = trimmed
            state.invalidate()
            state.refresh(manual = true)
        }
        apiKeyRow?.status?.text = getString(
            if (trimmed.isNotEmpty()) R.string.save_state_saved else R.string.save_state_unsaved
        )
    }

    private fun scheduleTokenSave(value: String) {
        tokenRow?.status?.text = getString(R.string.save_state_unsaved)
        tokenSave?.let { handler.removeCallbacks(it) }
        val runnable = Runnable { commitToken(value) }
        tokenSave = runnable
        handler.postDelayed(runnable, SAVE_DELAY_MS)
    }

    private fun commitToken(value: String) {
        tokenSave = null
        val trimmed = value.trim()
        if (trimmed != prefs.platformToken) {
            prefs.platformToken = trimmed
            state.invalidate()
            state.refresh(manual = true)
        }
        tokenRow?.status?.text = contextString(trimmed.isNotEmpty())
    }

    private fun contextString(present: Boolean): String {
        return getString(
            if (present) R.string.save_state_saved else R.string.row_token_sub
        )
    }

    /** 离开界面时把尚未落盘的输入立即写入，避免正在输入的密钥丢失。 */
    private fun flushPendingSaves() {
        apiKeySave?.let {
            handler.removeCallbacks(it)
            it.run()
        }
        customBalanceSave?.let { handler.removeCallbacks(it); it.run() }
        customUsageSave?.let { handler.removeCallbacks(it); it.run() }
        tokenSave?.let {
            handler.removeCallbacks(it)
            it.run()
        }
    }

    private fun syncTokenVisibility() {
        val visible = prefs.usageMode == UsageMode.TOKEN
        tokenBlock.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun setPetEnabled(enabled: Boolean) {
        if (enabled && !Settings.canDrawOverlays(this)) {
            prefs.petEnabled = false
            petSwitch.isChecked = false
            syncPermission()
            requestOverlayPermission()
            return
        }
        prefs.petEnabled = enabled
        if (enabled) {
            PetService.start(this)
        } else {
            PetService.stop(this)
        }
    }

    private fun syncPermission() {
        val overlay = flows.overlayGranted()
        Ui.updateSubtitle(
            overlayRow,
            getString(if (overlay) R.string.row_overlay_on else R.string.row_overlay_off)
        )
        Ui.updateSubtitle(
            notificationRow,
            getString(
                if (flows.notificationsGranted()) R.string.row_notification_on
                else R.string.row_notification_off
            )
        )
    }

    private fun syncBackgroundState() {
        val exempt = flows.ignoringBatteryOptimizations()
        Ui.updateSubtitle(
            batteryRow,
            getString(if (exempt) R.string.row_battery_on else R.string.row_battery_off)
        )
    }

    private fun requestOverlayPermission() {
        overlayPermission.launch(flows.overlayIntent())
    }

    private fun requestBatteryExemption() {
        runCatching { batteryPermission.launch(flows.batteryIntent()) }
            .onFailure { error -> Diagnostics.warn("系统页面跳转失败", error); openAppDetails() }
    }

    private fun openNotificationSettings() {
        runCatching { startActivity(flows.notificationSettingsIntent()) }
            .onFailure { error -> Diagnostics.warn("系统页面跳转失败", error); openAppDetails() }
    }

    private fun openAppDetails() {
        runCatching { startActivity(flows.appDetailsIntent()) }
            .onFailure { error -> Diagnostics.warn("打开应用详情页失败", error) }
    }

    private fun requestNotificationPermission() {
        flows.requestNotificationPermission { permission ->
            notificationPermission.launch(permission)
        }
    }

    private fun render(snapshot: PetState.Snapshot) {
        val display = content.display(snapshot, content.settingsOf(prefs))
        balanceText.text = display.balance
        usageText.text = display.usageHint ?: getString(R.string.status_hidden)
        val statusLabel = when {
            display.custom -> getString(R.string.status_custom)
            snapshot.status == PetState.Status.LOADING -> getString(R.string.bubble_loading)
            else -> snapshot.message ?: formatUpdated(snapshot.updatedAt)
        }
        Ui.updateSubtitle(refreshRow, statusLabel)
        preview.lines = content.balancePlan(display).lines
    }

    private fun formatUpdated(updatedAt: Long): String {
        if (updatedAt <= 0L) return getString(R.string.status_idle)
        val time = android.text.format.DateFormat.getTimeFormat(this)
        return getString(R.string.status_updated, time.format(java.util.Date(updatedAt)))
    }

    /** 自定义文本逐字写入，停顿后落盘并标记已保存。 */
    private fun scheduleCustomSave(value: String, balance: Boolean) {
        val row = if (balance) customBalanceRow else customUsageRow
        row?.status?.text = getString(R.string.save_state_unsaved)
        val pending = if (balance) customBalanceSave else customUsageSave
        pending?.let { handler.removeCallbacks(it) }
        val runnable = Runnable {
            val trimmed = value.trim()
            if (balance) {
                customBalanceSave = null
                prefs.customBalanceText = trimmed
            } else {
                customUsageSave = null
                prefs.customUsageText = trimmed
            }
            row?.status?.text = getString(R.string.save_state_saved)
        }
        if (balance) customBalanceSave = runnable else customUsageSave = runnable
        handler.postDelayed(runnable, SAVE_DELAY_MS)
    }

    companion object {
        private const val PROJECT_URL = "https://github.com/ErlangShen-AI/DeepSeek-Whale-Pet-Android"
        private const val BAR_PADDING_V = 1.6f
        private const val HERO_CARD_EMPHASIS = 1.6f
        private const val HEADER_TOP = 1.6f
        private const val HEADER_BOTTOM = 2.4f
        private const val HEADER_PARALLAX = 0.35f
        private const val BOTTOM_PADDING = 6f
        private const val REFRESH_STEP_SECONDS = 10f
        private const val SCALE_TICKS = 10f
        private const val SAVE_DELAY_MS = 400L
    }
}
