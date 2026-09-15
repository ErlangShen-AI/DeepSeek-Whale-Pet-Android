package com.whalepet.core

import android.content.Context
import kotlin.math.abs

/** 气泡的三种显示内容。 */
enum class BubbleMode { BALANCE, COST, QUOTE }

/**
 * 一次显示所需的全部内容。
 *
 * mode 说明这一屏是谁，lines 是气泡的三行文字，gif 表示用动图代替文字。
 */
class BubblePlan(
    val mode: BubbleMode,
    val lines: List<QuoteLine?>,
    val gif: Boolean = false
)

/**
 * 余额与用量的显示文本。
 *
 * 气泡、台词、设置页预览与通知栏都从这里取值，因此同一时刻各处显示的金额必然一致。
 * usageHint 为空表示提示行不显示。
 */
class Display(
    val title: String,
    val balance: String,
    val usage: String?,
    val usageHint: String?,
    val isPeak: Boolean,
    val peakText: PeakText,
    val custom: Boolean
)

/**
 * 气泡内容的唯一取值入口。
 *
 * 隐藏余额、自定义余额、自定义今日已用、显示今日已用这几个开关只在这里参与一次判定，
 * 只改变取到的文本，不改变调用方的流程。所有消费方拿到的都是同一份 BubblePlan 或 Display。
 */
class BubbleContent(private val texts: BubbleTexts) {

    /** 直接以 Context 构造，文案从应用资源读取。 */
    constructor(context: Context) : this(AndroidBubbleTexts(context))

    /** 影响取值的开关快照，取值过程中不读取配置。 */
    data class Settings(
        val hideBalance: Boolean,
        val customEnabled: Boolean,
        val customBalance: String,
        val customUsage: String,
        val showUsage: Boolean,
        val peakText: PeakText
    )

    fun settingsOf(prefs: Prefs): Settings = Settings(
        hideBalance = prefs.hideBalance,
        customEnabled = prefs.customBalanceEnabled,
        customBalance = prefs.customBalanceText,
        customUsage = prefs.customUsageText,
        showUsage = prefs.showTodayUsage,
        peakText = prefs.peakText
    )

    /** 余额与用量的显示文本，balanceOverride 用于数字滚动时的逐帧取值。 */
    fun display(
        snapshot: PetState.Snapshot,
        settings: Settings,
        balanceOverride: Double? = null
    ): Display {
        val currency = snapshot.currency
        val custom = customText(settings.customBalance, currency)
        val shown = balanceOverride ?: snapshot.balance
        val balance = when {
            settings.customEnabled && custom.isNotEmpty() -> custom
            shown != null -> Money.format(shown, currency)
            snapshot.status == PetState.Status.ERROR -> Money.PLACEHOLDER
            else -> LOADING_PLACEHOLDER
        }
        val customUsage = if (settings.customEnabled) customText(settings.customUsage, currency) else ""
        val usageValue = when {
            customUsage.isNotEmpty() -> customUsage
            snapshot.status == PetState.Status.ERROR ->
                snapshot.message?.take(MESSAGE_LIMIT) ?: texts.failed
            snapshot.balance == null -> null
            snapshot.todayUsage != null -> Money.format(snapshot.todayUsage, currency)
            else -> Money.PLACEHOLDER
        }
        val visible = settings.showUsage
        val usageHint = when {
            !visible -> null
            snapshot.status == PetState.Status.ERROR && customUsage.isEmpty() -> usageValue
            usageValue == null -> texts.loading
            else -> texts.todayUsage(usageValue)
        }
        return Display(
            title = texts.balanceLabel,
            balance = balance,
            usage = if (visible) usageValue else null,
            usageHint = usageHint,
            isPeak = snapshot.isPeak,
            peakText = settings.peakText,
            custom = settings.customEnabled
        )
    }

    /** 气泡内容：消耗气泡优先，其次只显示台词，再其次自定义或真实余额。 */
    fun plan(
        snapshot: PetState.Snapshot,
        settings: Settings,
        cost: Double?,
        gifAvailable: Boolean,
        balanceOverride: Double? = null
    ): BubblePlan {
        if (cost != null) return costPlan(cost)
        val display = display(snapshot, settings, balanceOverride)
        if (settings.hideBalance) {
            return quotePlan(display, skipPeriodGroup = true, gifAvailable = gifAvailable)
        }
        return balancePlan(display)
    }

    /** 余额内容：标签、金额与提示行各一行。 */
    fun balancePlan(display: Display): BubblePlan {
        return BubblePlan(
            mode = BubbleMode.BALANCE,
            lines = listOf(
                QuoteLine(display.title, QuoteStyle.LABEL),
                QuoteLine(display.balance, QuoteStyle.AMOUNT),
                display.usageHint?.let { QuoteLine(it, QuoteStyle.HINT) }
            )
        )
    }

    /** 台词内容：隐藏余额时跳过含时段与用量的那一组。 */
    fun quotePlan(display: Display, skipPeriodGroup: Boolean, gifAvailable: Boolean): BubblePlan {
        val bundle = Quotes.pickRandom(
            QuoteContext(
                isPeak = display.isPeak,
                usageHint = display.usageHint,
                peakText = display.peakText
            ),
            skipPeriodGroup = skipPeriodGroup
        )
        if (bundle.gif && !gifAvailable) {
            return BubblePlan(BubbleMode.QUOTE, Quotes.gifFallback(), gif = false)
        }
        return BubblePlan(BubbleMode.QUOTE, bundle.lines, bundle.gif)
    }

    /** 消耗金额显示为红色正数，不显示提示行。 */
    private fun costPlan(delta: Double): BubblePlan {
        return BubblePlan(
            mode = BubbleMode.COST,
            lines = listOf(
                QuoteLine(texts.costLabel, QuoteStyle.LABEL),
                QuoteLine(Money.format(abs(delta), CNY), QuoteStyle.AMOUNT, PetGeometry.PEAK_COLOR),
                null
            )
        )
    }

    /**
     * 自定义文本的处理。
     *
     * 输入数字时按币种加上符号与两位小数，输入其他文字时原样显示，
     * 因此正负号、整数与任意文本都能正确呈现。
     */
    private fun customText(text: String, currency: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return trimmed
        val number = trimmed.replace(",", "").toDoubleOrNull() ?: return trimmed
        return Money.format(number, currency)
    }

    companion object {
        private const val MESSAGE_LIMIT = 14
        private const val LOADING_PLACEHOLDER = "…"
        private const val CNY = "CNY"
    }
}
