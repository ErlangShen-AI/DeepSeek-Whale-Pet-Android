package com.whalepet.ui

import android.content.Context
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.materialswitch.MaterialSwitch
import com.whalepet.R
import com.whalepet.core.Money
import com.whalepet.core.PeakText
import com.whalepet.core.PetGeometry
import com.whalepet.core.SoundSet
import com.whalepet.core.UsageMode
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 设置界面里不依赖配置与状态的卡片。
 *
 * 使用说明按条目顺序排成一张卡，条目之间的分隔线与行的外观都取自 Ui 与 Metrics。
 */
object SettingsCards {

    fun guide(context: Context, metrics: Metrics): LinearLayout {
        val card = Ui.card(context, metrics)
        val items = listOf(
            context.getString(R.string.guide_tap_whale),
            context.getString(R.string.guide_tap_bubble),
            context.getString(R.string.guide_drag),
            context.getString(R.string.guide_no_multi_tap),
            context.getString(R.string.guide_settings_entry),
            context.getString(R.string.guide_content_modes)
        )
        for (index in items.indices) {
            if (index > 0) Ui.addDivider(card, context, metrics)
            card.addView(Ui.settingRow(context, metrics, items[index], null))
        }
        return card
    }

    /** 后台运行卡片：系统设置入口与开机自启开关。 */
    fun background(
        context: Context,
        metrics: Metrics,
        autoStart: Boolean,
        onOpenSystemSettings: () -> Unit,
        onAutoStartChange: (Boolean) -> Unit
    ): LinearLayout {
        val card = Ui.card(context, metrics)
        card.addView(
            Ui.actionRow(
                context,
                metrics,
                context.getString(R.string.row_autostart),
                context.getString(R.string.row_autostart_sub),
                context.getString(R.string.action_allow)
            ) { onOpenSystemSettings() }
        )
        Ui.addDivider(card, context, metrics)
        card.addView(
            Ui.switchRow(
                context,
                metrics,
                context.getString(R.string.row_auto_start),
                context.getString(R.string.row_auto_start_sub),
                autoStart
            ) { value -> onAutoStartChange(value) }
        )
        return card
    }

    /** 关于卡片：版本号、项目主页入口与许可说明。 */
    fun about(
        context: Context,
        metrics: Metrics,
        version: String,
        onOpenProject: () -> Unit
    ): LinearLayout {
        val card = Ui.card(context, metrics)
        card.addView(Ui.valueRow(context, metrics, context.getString(R.string.row_version), version))
        Ui.addDivider(card, context, metrics)
        card.addView(
            Ui.actionRow(
                context,
                metrics,
                context.getString(R.string.row_project),
                context.getString(R.string.row_project_sub),
                context.getString(R.string.action_open)
            ) { onOpenProject() }
        )
        Ui.addDivider(card, context, metrics)
        card.addView(
            Ui.valueRow(
                context,
                metrics,
                context.getString(R.string.row_license),
                context.getString(R.string.row_license_sub)
            )
        )
        return card
    }

    /** 声音卡片：音效集合、音量与试听。 */
    fun sound(
        context: Context,
        metrics: Metrics,
        soundSet: SoundSet,
        volume: Float,
        onSoundSetChange: (SoundSet) -> Unit,
        onVolumeChange: (Float) -> Unit,
        onPreview: () -> Unit
    ): LinearLayout {
        val card = Ui.card(context, metrics)
        val sets = listOf(
            context.getString(R.string.segment_sound_duck),
            context.getString(R.string.segment_sound_fx),
            context.getString(R.string.segment_sound_off)
        )
        val segment = SegmentedControl(context, metrics, sets, soundSet.ordinal) { index ->
            onSoundSetChange(SoundSet.entries[index])
        }
        card.addView(
            Ui.segmentRow(
                context,
                metrics,
                context.getString(R.string.row_sound_set),
                segment
            )
        )
        Ui.addDivider(card, context, metrics)
        card.addView(
            Ui.sliderRow(
                context,
                metrics,
                context.getString(R.string.row_volume),
                context.getString(R.string.row_percent_value, (volume * 100f).roundToInt()),
                0f,
                VOLUME_TICKS,
                1f,
                (volume * VOLUME_TICKS).roundToInt().toFloat(),
                format = { value ->
                    context.getString(
                        R.string.row_percent_value,
                        (value / VOLUME_TICKS * 100f).roundToInt()
                    )
                },
                onValue = { value -> onVolumeChange(value / VOLUME_TICKS) }
            )
        )
        Ui.addDivider(card, context, metrics)
        card.addView(
            Ui.actionRow(
                context,
                metrics,
                context.getString(R.string.row_sound_preview),
                context.getString(R.string.row_sound_preview_sub),
                context.getString(R.string.action_preview)
            ) { onPreview() }
        )
        return card
    }

    /** 桌宠卡片的控件引用，供调用方同步开关状态。 */
    class PetCard(val card: LinearLayout, val switch: MaterialSwitch)

    /** 桌宠卡片：显示开关、大小、气泡开关、只显示台词与自动关闭时间。 */
    fun pet(
        context: Context,
        metrics: Metrics,
        petEnabled: Boolean,
        scale: Float,
        scaleTicks: Float,
        bubblesEnabled: Boolean,
        hideBalance: Boolean,
        bubbleSeconds: Int,
        maxBubbleSeconds: Int,
        onPetEnabledChange: (Boolean) -> Unit,
        onScaleChange: (Float) -> Unit,
        onBubblesChange: (Boolean) -> Unit,
        onHideBalanceChange: (Boolean) -> Unit,
        onBubbleSecondsChange: (Int) -> Unit
    ): PetCard {
        val card = Ui.card(context, metrics)
        val toggle = MaterialSwitch(context)
        toggle.isChecked = petEnabled
        toggle.setOnCheckedChangeListener { _, checked -> onPetEnabledChange(checked) }
        card.addView(
            Ui.settingRow(
                context,
                metrics,
                context.getString(R.string.row_pet),
                context.getString(R.string.row_pet_sub),
                toggle
            )
        )
        Ui.addDivider(card, context, metrics)

        card.addView(
            Ui.sliderRow(
                context,
                metrics,
                context.getString(R.string.row_scale),
                scaleLabel(scale),
                PetGeometry.MIN_SCALE * scaleTicks,
                PetGeometry.MAX_SCALE * scaleTicks,
                1f,
                (scale * scaleTicks).roundToInt().toFloat(),
                format = { value -> scaleLabel(value / scaleTicks) },
                onValue = { value -> onScaleChange(value / scaleTicks) }
            )
        )
        Ui.addDivider(card, context, metrics)

        card.addView(
            Ui.switchRow(
                context,
                metrics,
                context.getString(R.string.row_bubble),
                context.getString(R.string.row_bubble_sub),
                bubblesEnabled
            ) { value -> onBubblesChange(value) }
        )
        Ui.addDivider(card, context, metrics)
        card.addView(
            Ui.switchRow(
                context,
                metrics,
                context.getString(R.string.row_hide_balance),
                context.getString(R.string.row_hide_balance_sub),
                hideBalance
            ) { value -> onHideBalanceChange(value) }
        )
        Ui.addDivider(card, context, metrics)

        card.addView(
            Ui.sliderRow(
                context,
                metrics,
                context.getString(R.string.row_bubble_seconds),
                context.getString(R.string.row_seconds_value, bubbleSeconds),
                0f,
                maxBubbleSeconds.toFloat(),
                1f,
                bubbleSeconds.toFloat(),
                format = { value -> context.getString(R.string.row_seconds_value, value.toInt()) },
                onValue = { value -> onBubbleSecondsChange(value.toInt()) }
            )
        )
        return PetCard(card, toggle)
    }

    /** 用量卡片的控件引用，供调用方同步令牌可见性与选中状态。 */
    class UsageCard(
        val card: LinearLayout,
        val segment: SegmentedControl,
        val tokenBlock: LinearLayout,
        val tokenRow: Ui.InputRow,
        val peakSegment: SegmentedControl
    )

    /** 用量卡片：用量模式、平台令牌、是否显示今日已用与峰谷文案。 */
    fun usage(
        context: Context,
        metrics: Metrics,
        usageMode: UsageMode,
        platformToken: String,
        showTodayUsage: Boolean,
        peakText: PeakText,
        onUsageModeChange: (UsageMode) -> Unit,
        onTokenChange: (String) -> Unit,
        onShowUsageChange: (Boolean) -> Unit,
        onPeakTextChange: (PeakText) -> Unit
    ): UsageCard {
        val card = Ui.card(context, metrics)
        val modes = listOf(
            context.getString(R.string.segment_usage_ledger),
            context.getString(R.string.segment_usage_token)
        )
        val segment = SegmentedControl(context, metrics, modes, usageMode.ordinal) { index ->
            onUsageModeChange(if (index == 0) UsageMode.LEDGER else UsageMode.TOKEN)
        }
        card.addView(
            Ui.segmentRow(context, metrics, context.getString(R.string.row_usage_mode), segment)
        )
        Ui.addDivider(card, context, metrics)

        val block = LinearLayout(context)
        block.orientation = LinearLayout.VERTICAL
        val token = Ui.inputRow(
            context,
            metrics,
            context.getString(R.string.row_platform_token),
            context.getString(R.string.row_platform_token_hint),
            platformToken,
            secret = true,
            status = context.getString(R.string.row_token_sub)
        ) { value -> onTokenChange(value) }
        block.addView(token.view)
        card.addView(block)
        Ui.addDivider(card, context, metrics)

        card.addView(
            Ui.switchRow(
                context,
                metrics,
                context.getString(R.string.row_show_usage),
                context.getString(R.string.row_show_usage_sub),
                showTodayUsage
            ) { value -> onShowUsageChange(value) }
        )
        Ui.addDivider(card, context, metrics)

        val peaks = listOf(
            context.getString(R.string.segment_peak_default),
            context.getString(R.string.segment_peak_liangwen),
            context.getString(R.string.segment_peak_qiangqiang)
        )
        val peakSegment = SegmentedControl(context, metrics, peaks, peakText.ordinal) { index ->
            onPeakTextChange(PeakText.entries[index])
        }
        card.addView(
            Ui.segmentRow(context, metrics, context.getString(R.string.row_peak_text), peakSegment)
        )
        return UsageCard(card, segment, block, token, peakSegment)
    }

    /** 余额卡片的控件引用，供调用方标注保存状态。 */
    class BalanceCard(
        val card: LinearLayout,
        val apiKeyRow: Ui.InputRow,
        val customBalanceRow: Ui.InputRow,
        val customUsageRow: Ui.InputRow
    )

    /** 余额卡片：密钥、刷新间隔、自定义余额与自定义今日已用。 */
    fun balance(
        context: Context,
        metrics: Metrics,
        apiKey: String,
        refreshSeconds: Int,
        refreshStepSeconds: Float,
        minRefreshSeconds: Int,
        maxRefreshSeconds: Int,
        customEnabled: Boolean,
        customBalance: String,
        customUsage: String,
        onApiKeyChange: (String) -> Unit,
        onRefreshSecondsChange: (Int) -> Unit,
        onCustomEnabledChange: (Boolean) -> Unit,
        onCustomBalanceChange: (String) -> Unit,
        onCustomUsageChange: (String) -> Unit
    ): BalanceCard {
        val card = Ui.card(context, metrics)
        val keyRow = Ui.inputRow(
            context,
            metrics,
            context.getString(R.string.row_api_key),
            context.getString(R.string.row_api_key_hint),
            apiKey,
            secret = true,
            status = if (apiKey.isEmpty()) {
                context.getString(R.string.save_state_unsaved)
            } else {
                context.getString(R.string.save_state_saved)
            }
        ) { value -> onApiKeyChange(value) }
        card.addView(keyRow.view)
        Ui.addDivider(card, context, metrics)

        card.addView(
            Ui.sliderRow(
                context,
                metrics,
                context.getString(R.string.row_refresh),
                context.getString(R.string.row_seconds_value, refreshSeconds),
                minRefreshSeconds.toFloat(),
                maxRefreshSeconds.toFloat(),
                refreshStepSeconds,
                refreshSeconds.toFloat(),
                format = { value ->
                    context.getString(R.string.row_seconds_value, value.toInt())
                },
                onValue = { value -> onRefreshSecondsChange(value.toInt()) }
            )
        )
        Ui.addDivider(card, context, metrics)

        card.addView(
            Ui.settingRow(context, metrics, context.getString(R.string.card_custom_title), null)
        )
        Ui.addDivider(card, context, metrics)
        card.addView(
            Ui.switchRow(
                context,
                metrics,
                context.getString(R.string.row_custom_enable),
                context.getString(R.string.row_custom_enable_sub),
                customEnabled
            ) { value -> onCustomEnabledChange(value) }
        )
        Ui.addDivider(card, context, metrics)

        val balanceRow = Ui.inputRow(
            context,
            metrics,
            context.getString(R.string.row_custom_balance),
            context.getString(R.string.row_custom_balance_hint),
            customBalance,
            secret = false,
            status = context.getString(R.string.save_state_saved)
        ) { value -> onCustomBalanceChange(value) }
        card.addView(balanceRow.view)
        Ui.addDivider(card, context, metrics)

        val usageRow = Ui.inputRow(
            context,
            metrics,
            context.getString(R.string.row_custom_usage),
            context.getString(R.string.row_custom_usage_hint),
            customUsage,
            secret = false,
            status = context.getString(R.string.save_state_saved)
        ) { value -> onCustomUsageChange(value) }
        card.addView(usageRow.view)
        return BalanceCard(card, keyRow, balanceRow, usageRow)
    }

    /** 状态卡片的控件引用，供调用方写入余额、用量与刷新状态。 */
    class StatusCard(
        val card: LinearLayout,
        val preview: PetPreviewView,
        val balanceText: TextView,
        val usageText: TextView,
        val refreshRow: LinearLayout
    )

    /** 状态卡片：桌宠预览、当前余额、今日已用与立即刷新，用更深的阴影作为首屏主卡。 */
    fun status(
        context: Context,
        metrics: Metrics,
        emphasis: Float,
        onRefresh: () -> Unit
    ): StatusCard {
        val card = Ui.card(context, metrics, emphasis)
        val preview = PetPreviewView(context)
        preview.contentDescription = context.getString(R.string.preview_pet)
        val previewParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        previewParams.topMargin = metrics.u(PREVIEW_GAP)
        card.addView(preview, previewParams)
        Ui.addDivider(card, context, metrics)

        val balanceText = Ui.text(context, metrics, TextRole.VALUE, Money.PLACEHOLDER)
        card.addView(
            Ui.settingRow(
                context,
                metrics,
                context.getString(R.string.status_balance_label),
                null,
                balanceText
            )
        )
        Ui.addDivider(card, context, metrics)

        val usageText = Ui.text(
            context,
            metrics,
            TextRole.BODY,
            Money.PLACEHOLDER,
            R.color.text_secondary
        )
        card.addView(
            Ui.settingRow(
                context,
                metrics,
                context.getString(R.string.status_usage_label),
                null,
                usageText
            )
        )
        Ui.addDivider(card, context, metrics)

        val refreshRow = Ui.actionRow(
            context,
            metrics,
            context.getString(R.string.action_refresh_now),
            context.getString(R.string.status_idle)
        ) { onRefresh() }
        card.addView(refreshRow)
        return StatusCard(card, preview, balanceText, usageText, refreshRow)
    }

    /** 权限卡片的控件引用，供调用方刷新每行的状态文案。 */
    class PermissionCard(
        val card: LinearLayout,
        val overlayRow: LinearLayout,
        val notificationRow: LinearLayout,
        val batteryRow: LinearLayout
    )

    /** 权限卡片：悬浮窗权限、通知权限与电池优化白名单三个入口。 */
    fun permission(
        context: Context,
        metrics: Metrics,
        onRequestOverlay: () -> Unit,
        onOpenNotificationSettings: () -> Unit,
        onRequestBatteryExemption: () -> Unit
    ): PermissionCard {
        val card = Ui.card(context, metrics)
        val overlayRow = Ui.actionRow(
            context,
            metrics,
            context.getString(R.string.row_permission),
            context.getString(R.string.row_overlay_off),
            context.getString(R.string.action_grant)
        ) { onRequestOverlay() }
        card.addView(overlayRow)
        Ui.addDivider(card, context, metrics)

        val notificationRow = Ui.actionRow(
            context,
            metrics,
            context.getString(R.string.row_notification),
            context.getString(R.string.row_notification_off),
            context.getString(R.string.action_allow)
        ) { onOpenNotificationSettings() }
        card.addView(notificationRow)
        Ui.addDivider(card, context, metrics)

        val batteryRow = Ui.actionRow(
            context,
            metrics,
            context.getString(R.string.row_battery),
            context.getString(R.string.row_battery_off),
            context.getString(R.string.action_allow)
        ) { onRequestBatteryExemption() }
        card.addView(batteryRow)
        return PermissionCard(card, overlayRow, notificationRow, batteryRow)
    }

    /** 状态卡片里预览与上方内容之间留出的间距。 */
    private const val PREVIEW_GAP = 1.2f

    /** 缩放倍数按一位小数显示。 */
    private fun scaleLabel(value: Float): String {
        return String.format(Locale.US, "%.1f", value)
    }

    /** 音量滑块的刻度数，百分比按它换算。 */
    private const val VOLUME_TICKS = 20f
}
